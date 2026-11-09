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
    /** Snoozed by Fiero (see [Notices.shouldQuiet]): listed on the Notices screen but not in the status bar. */
    val quiet: Boolean = false,
) {
    /** A text, missed call or voicemail: what the home-screen count is about. */
    val important: Boolean get() = pkg in Notices.IMPORTANT
}

object Notices {

    /** Texts, missed calls and voicemail. Counted on the home screen and never quieted. */
    val IMPORTANT = setOf(
        "com.android.mms",               // Messages
        "com.android.dialer",            // missed calls, visual voicemail
        "com.android.phone",             // voicemail waiting (carrier)
        "com.android.server.telecom",    // missed calls on some builds
    )

    /** Also left alone (they're time-critical), but not counted. */
    private val ALSO_KEEP = setOf("com.android.deskclock", "com.android.calendar")

    /** Our own apps' ongoing notices (Fiero Nav guidance etc.) are left alone too. */
    private const val FIERO_PREFIX = "dev.mike."

    /** How long a quieted notice stays snoozed; it's re-quieted if re-posted. */
    const val SNOOZE_MS = 365L * 24 * 60 * 60 * 1000

    /** Android's own "<app> is displaying over other apps" notice, posted because of the outer-screen strip. */
    private const val ALERT_WINDOW_TAG = "com.android.server.wm.AlertWindowNotification"

    /**
     * Whether Fiero should snooze this notice so it leaves the status bar and the shade: everything except
     * texts, calls and voicemail, alarms and calendar reminders, and Fiero's own apps. Media players are
     * quieted too (the home screen's Now Playing line, `*` and the front button use the media session, not
     * the notice). Other apps' ongoing notices (a foreground service) are left alone; Android's own (USB,
     * Wi-Fi networks, "displaying over other apps") are quieted.
     */
    fun shouldQuiet(pkg: String, transport: Boolean, clearable: Boolean): Boolean = when {
        pkg in IMPORTANT || pkg in ALSO_KEEP || pkg.startsWith(FIERO_PREFIX) -> false
        transport || pkg == "android" || pkg == "com.android.systemui" -> true
        else -> clearable
    }

    /**
     * What's worth listing: important ones (texts, calls, voicemail) first, then the rest newest first,
     * with notices you can clear before ongoing ones (USB, charging):
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
            .sortedWith(
                compareByDescending<Notice> { it.important }
                    .thenByDescending { it.clearable }
                    .thenByDescending { it.time },
            )
    }

    fun from(sbn: StatusBarNotification, quiet: Boolean = false): Notice {
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
            quiet = quiet,
        )
    }

    /** Current notices, or null when Fiero has no notification access (or the listener isn't bound yet). */
    fun current(): List<Notice>? {
        val l = MediaListener.instance ?: return null
        return try {
            val active = l.activeNotifications.orEmpty().map { from(it) }
            val hidden = hidden()
            val snoozed = l.snoozedNotifications.orEmpty().map { from(it, quiet = true) }
                .filterNot { hiddenId(it.key, it.time) in hidden }
            visible(active + snoozed)
        } catch (e: Exception) {
            Log.w(KeyRouter.TAG, "notices: $e")
            null
        }
    }

    fun sbn(key: String): StatusBarNotification? = MediaListener.instance?.let { l ->
        runCatching {
            l.activeNotifications.orEmpty().firstOrNull { it.key == key }
                ?: l.snoozedNotifications.orEmpty().firstOrNull { it.key == key }
        }.getOrNull()
    }

    /**
     * Clears a notice. Android won't cancel a snoozed (quieted) one, so those are just hidden from the
     * list instead; they're already out of the status bar and the shade.
     */
    fun dismiss(n: Notice) {
        if (!n.quiet) {
            runCatching { MediaListener.instance?.cancelNotification(n.key) }
                .onFailure { Log.w(KeyRouter.TAG, "dismiss ${n.key}: $it") }
            return
        }
        val l = MediaListener.instance ?: return
        val prefs = l.getSharedPreferences("notices", Context.MODE_PRIVATE)
        // Keep only entries for notices that are still snoozed, so the set can't grow forever.
        val stillSnoozed = runCatching { l.snoozedNotifications.orEmpty().map { hiddenId(it.key, it.notification.`when`.takeIf { w -> w > 0 } ?: it.postTime) } }
            .getOrDefault(emptyList()).toSet()
        val hidden = prefs.getStringSet(HIDDEN, emptySet()).orEmpty().filter { it in stillSnoozed }.toMutableSet()
        hidden += hiddenId(n.key, n.time)
        prefs.edit().putStringSet(HIDDEN, hidden).apply()
        changed()
    }

    private const val HIDDEN = "hidden"

    /** Key plus time: if the app posts the notice again (new time), it shows again. */
    private fun hiddenId(key: String, time: Long) = "$key@$time"

    private fun hidden(): Set<String> =
        MediaListener.instance?.getSharedPreferences("notices", Context.MODE_PRIVATE)
            ?.getStringSet(HIDDEN, emptySet()).orEmpty()

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
