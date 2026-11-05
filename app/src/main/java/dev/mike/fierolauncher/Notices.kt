package dev.mike.fierolauncher

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.mike.fierolauncher.ui.KeyRouter

/** One row on the Notices screen: what the stock shade would show, flattened for a keypad list. */
data class Notice(
    val key: String,
    val pkg: String,
    val tag: String?,
    val title: String,
    val text: String,
    val time: Long,
    val clearable: Boolean,
    /** Media player controls (Spotify etc.): already on the home screen's Now Playing line. */
    val transport: Boolean,
    val groupSummary: Boolean,
    val group: String?,
)

object Notices {

    /** Android's own "<app> is displaying over other apps" notice, posted because of the outer-screen strip. */
    private const val ALERT_WINDOW_TAG = "com.android.server.wm.AlertWindowNotification"

    /**
     * What's worth listing, newest first, with notices you can clear before ongoing ones (USB, charging):
     * no media controls, no empty notices, and a group's summary only when none of its members is shown.
     */
    fun visible(all: List<Notice>): List<Notice> {
        val base = all.filter { n ->
            !n.transport &&
                !(n.tag?.startsWith(ALERT_WINDOW_TAG) ?: false) &&
                (n.title.isNotBlank() || n.text.isNotBlank())
        }
        val groupsWithMembers = base.filter { !it.groupSummary && it.group != null }.map { it.group }.toSet()
        return base
            .filter { !(it.groupSummary && it.group in groupsWithMembers) }
            .sortedWith(compareByDescending<Notice> { it.clearable }.thenByDescending { it.time })
    }

    fun from(sbn: StatusBarNotification): Notice {
        val n = sbn.notification
        val x = n.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (x.getCharSequence(Notification.EXTRA_TEXT) ?: x.getCharSequence(Notification.EXTRA_BIG_TEXT))
            ?.toString().orEmpty()
        return Notice(
            key = sbn.key,
            pkg = sbn.packageName,
            tag = sbn.tag,
            title = title.trim(),
            text = text.replace('\n', ' ').trim(),
            time = n.`when`.takeIf { it > 0 } ?: sbn.postTime,
            clearable = sbn.isClearable,
            transport = n.category == Notification.CATEGORY_TRANSPORT || x.containsKey(Notification.EXTRA_MEDIA_SESSION),
            groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            group = if (sbn.isGroup) sbn.groupKey else null,
        )
    }

    /** Current notices, or null when Fiero has no notification access (or the listener isn't bound yet). */
    fun current(): List<Notice>? {
        val l = MediaListener.instance ?: return null
        return try {
            visible(l.activeNotifications.orEmpty().map(::from))
        } catch (e: Exception) {
            Log.w(KeyRouter.TAG, "notices: $e")
            null
        }
    }

    fun sbn(key: String): StatusBarNotification? =
        MediaListener.instance?.let { l -> runCatching { l.activeNotifications.orEmpty().firstOrNull { it.key == key } }.getOrNull() }

    fun dismiss(key: String) {
        runCatching { MediaListener.instance?.cancelNotification(key) }
            .onFailure { Log.w(KeyRouter.TAG, "dismiss $key: $it") }
    }

    fun appLabel(context: Context, pkg: String): String = when (pkg) {
        "android", "com.android.systemui" -> "System"
        else -> try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    // ---- change notifications (posted / removed), on the main thread ----

    private val main by lazy { Handler(Looper.getMainLooper()) } // lazy: keeps [visible] testable on the JVM
    private val observers = LinkedHashSet<() -> Unit>()

    fun observe(o: () -> Unit) { observers += o }
    fun unobserve(o: () -> Unit) { observers -= o }
    internal fun changed() {
        main.post { observers.toList().forEach { it() } }
    }
}
