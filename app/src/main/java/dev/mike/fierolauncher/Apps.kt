package dev.mike.fierolauncher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.mike.fierolauncher.ui.KeyRouter
import dev.mike.fierolauncher.ui.say
import java.text.Collator

/** A launchable activity: what the All apps list shows and what a D-pad shortcut points at. */
data class AppEntry(val label: String, val component: ComponentName) {
    /** Label as T9 digits (letters -> 2-9, digits kept, anything else -> '0' word break). */
    val t9: String = toT9(label)
}

object Apps {
    @Volatile
    private var cache: List<AppEntry>? = null

    fun invalidate() {
        cache = null
    }

    /** Every launcher-visible app except Fiero itself, sorted by label. Call off the UI thread. */
    fun load(context: Context): List<AppEntry> {
        cache?.let { return it }
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        val list = pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    it.loadLabel(pm).toString().trim(),
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                )
            }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
        cache = list
        return list
    }

    /** Starts [component] the way a launcher does (resumes the app's existing task if it has one). */
    fun launch(context: Context, component: ComponentName, label: String = component.packageName): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            Log.w(KeyRouter.TAG, "launch $component failed: $e")
        } catch (e: SecurityException) {
            Log.w(KeyRouter.TAG, "launch $component failed: $e")
        }
        // The activity may have been renamed by an update; fall back to the package's own entry point.
        val fallback = context.packageManager.getLaunchIntentForPackage(component.packageName)
        if (fallback != null) {
            try {
                context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: RuntimeException) {
                Log.w(KeyRouter.TAG, "fallback launch ${component.packageName} failed: $e")
            }
        }
        context.say("$label isn't installed")
        return false
    }
}

private const val T9_KEYS = "22233344455566677778889999"

fun toT9(text: String): String {
    val sb = StringBuilder(text.length)
    for (ch in text.lowercase()) {
        sb.append(
            when (ch) {
                in 'a'..'z' -> T9_KEYS[ch - 'a']
                in '0'..'9' -> ch
                else -> '0'
            },
        )
    }
    return sb.toString()
}

/**
 * True if the typed digits match the start of any word in the label ("Maps Lite": "6277" and "5483"
 * both match). '0' in [typed] stands for a word break, so "6270548" matches "Maps Li…".
 */
fun AppEntry.matchesT9(typed: String): Boolean {
    if (typed.isEmpty()) return true
    for (i in t9.indices) {
        val wordStart = i == 0 || (t9[i - 1] == '0' && t9[i] != '0')
        if (wordStart && t9.startsWith(typed, i)) return true
    }
    return false
}
