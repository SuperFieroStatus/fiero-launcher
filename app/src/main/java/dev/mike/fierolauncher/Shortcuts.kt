package dev.mike.fierolauncher

import android.content.ComponentName
import android.content.Context

/**
 * D-pad shortcuts on the home screen, kept in Fiero's own prefs (never Settings.Global, which
 * Launcher4 owns and rewrites). Stored as "label|package/class".
 */
object Shortcuts {
    enum class Dir(val key: String, val arrow: String, val title: String) {
        UP("up", "▲", "Up"),
        DOWN("down", "▼", "Down"),
        LEFT("left", "◀", "Left"),
        RIGHT("right", "▶", "Right"),
    }

    data class Shortcut(val label: String, val component: ComponentName)

    /** Launcher4's shortcuts at the time Fiero was written. */
    private val defaults = mapOf(
        Dir.UP to Shortcut("Spotify", ComponentName("com.spotify.music", "com.spotify.music.MainActivity")),
        Dir.DOWN to Shortcut(
            "Maps",
            ComponentName("com.google.android.apps.mapslite", "com.google.maps.lite.twa.MapsLiteTwaLauncherActivity"),
        ),
        Dir.LEFT to Shortcut("F-Droid", ComponentName("org.fdroid.fdroid", "org.fdroid.IconActivity")),
        Dir.RIGHT to Shortcut("FlipSpot", ComponentName("dev.mike.flipspot", "dev.mike.flipspot.HomeActivity")),
    )

    private fun prefs(context: Context) = context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)

    fun get(context: Context, dir: Dir): Shortcut? {
        val raw = prefs(context).getString(dir.key, null) ?: return defaults[dir]
        if (raw.isEmpty()) return null // explicitly cleared
        val label = raw.substringBefore('|')
        val component = ComponentName.unflattenFromString(raw.substringAfter('|')) ?: return defaults[dir]
        return Shortcut(label, component)
    }

    fun set(context: Context, dir: Dir, shortcut: Shortcut?) {
        val value = shortcut?.let { "${it.label}|${it.component.flattenToString()}" } ?: ""
        prefs(context).edit().putString(dir.key, value).apply()
    }
}
