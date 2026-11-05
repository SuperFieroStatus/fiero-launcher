package dev.mike.fierolauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils
import android.util.Log
import android.view.KeyEvent
import android.widget.TextView
import dev.mike.fierolauncher.ui.KeyAction
import dev.mike.fierolauncher.ui.KeyActionHandler
import dev.mike.fierolauncher.ui.KeyRouter
import dev.mike.fierolauncher.ui.ListItem
import dev.mike.fierolauncher.ui.ListScreen
import dev.mike.fierolauncher.ui.RoutedKey
import dev.mike.fierolauncher.ui.say
import java.text.DateFormat

/**
 * Keypad replacement for the stock notification shade, which on a 240x320 screen is mostly Spotify's
 * player. One row per notice ("title · text"); the footer shows the app and time.
 * OK opens it, left soft clears it, right soft clears all (press twice), Back goes home.
 */
class NoticesActivity : Activity(), KeyActionHandler {

    private lateinit var list: ListScreen
    private lateinit var title: TextView
    private lateinit var softLeft: TextView
    private lateinit var softCenter: TextView
    private lateinit var softRight: TextView

    private var notices: List<Notice> = emptyList()
    private var noAccess = false
    private var clearAllArmedAt = 0L

    private val refresh: () -> Unit = { load() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notices)
        list = findViewById(R.id.list)
        title = findViewById(R.id.title)
        softLeft = findViewById(R.id.soft_left)
        softCenter = findViewById(R.id.soft_center)
        softRight = findViewById(R.id.soft_right)
        list.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        Notices.observe(refresh)
        load()
    }

    override fun onPause() {
        Notices.unobserve(refresh)
        super.onPause()
    }

    private fun load() {
        val now = Notices.current()
        noAccess = now == null
        val keep = notices.getOrNull(list.selectedIndex)?.key
        notices = now.orEmpty()
        val timeFmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        val dateFmt = DateFormat.getDateInstance(DateFormat.SHORT)
        list.setEmptyText(if (noAccess) "No notification access" else "No notices")
        list.setListItems(
            notices.map { n ->
                val label = listOf(n.title, n.text).filter { it.isNotBlank() }.joinToString(" · ")
                val time = if (DateUtils.isToday(n.time)) timeFmt.format(n.time) else dateFmt.format(n.time)
                ListItem(label, detail = "${Notices.appLabel(this, n.pkg)} · $time", dim = !n.clearable)
            },
            notices.indexOfFirst { it.key == keep }.coerceAtLeast(0),
        )
        title.text = if (notices.isEmpty()) "Notices" else "Notices (${notices.size})"
        softLeft.text = if (notices.getOrNull(list.selectedIndex)?.clearable == true) "Clear" else ""
        softCenter.text = if (notices.isEmpty()) "" else "Open"
        softRight.text = when {
            noAccess -> "Shade"
            notices.any { it.clearable } -> "Clear all"
            else -> ""
        }
        list.onSelectionChanged = { i -> softLeft.text = if (notices.getOrNull(i)?.clearable == true) "Clear" else "" }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        KeyRouter.dispatch(event, SCREEN, this) || super.dispatchKeyEvent(event)

    override fun onKeyAction(key: RoutedKey, event: KeyEvent): Boolean {
        val repeat = event.repeatCount > 0
        when (key.action) {
            KeyAction.UP -> list.move(-1)
            KeyAction.DOWN -> list.move(+1)
            KeyAction.LEFT -> list.page(-1)
            KeyAction.RIGHT -> list.page(+1)
            KeyAction.OK -> if (!repeat) notices.getOrNull(list.selectedIndex)?.let(::open)
            KeyAction.SOFT_LEFT -> if (!repeat) notices.getOrNull(list.selectedIndex)?.let(::clear)
            KeyAction.SOFT_RIGHT -> if (!repeat) {
                if (noAccess) openShade() else clearAll()
            }
            KeyAction.BACK -> if (!repeat) finish()
            // Digits, * and # would otherwise fall through to the dialer on this phone.
            KeyAction.DIGIT, KeyAction.STAR, KeyAction.POUND -> Unit
            KeyAction.CALL -> return false
        }
        return true
    }

    private fun open(n: Notice) {
        val sbn = Notices.sbn(n.key) ?: return load()
        val notification = sbn.notification
        val intent = notification.contentIntent
        try {
            if (intent != null) {
                intent.send()
            } else {
                val launch = packageManager.getLaunchIntentForPackage(n.pkg) ?: return say("Nothing to open")
                startActivity(launch)
            }
            // The stock shade drops auto-cancel notices when they're opened; do the same.
            if (notification.flags and Notification.FLAG_AUTO_CANCEL != 0 && n.clearable) Notices.dismiss(n.key)
        } catch (e: PendingIntent.CanceledException) {
            Log.w(KeyRouter.TAG, "notice open: $e")
            say("Can't open that")
        }
    }

    private fun clear(n: Notice) {
        if (!n.clearable) return say("Can't clear that one")
        Notices.dismiss(n.key)
    }

    private fun clearAll() {
        val clearable = notices.filter { it.clearable }
        if (clearable.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        if (now - clearAllArmedAt > CONFIRM_MS) {
            clearAllArmedAt = now
            say("Press again to clear ${clearable.size}")
            return
        }
        clearAllArmedAt = 0
        clearable.forEach { Notices.dismiss(it.key) }
    }

    /** Fallback when Fiero has no notification access. */
    @SuppressLint("WrongConstant")
    private fun openShade() {
        try {
            val sbm = getSystemService("statusbar")
            sbm.javaClass.getMethod("expandNotificationsPanel").invoke(sbm)
        } catch (e: Exception) {
            Log.w(KeyRouter.TAG, "expandNotificationsPanel failed: $e")
        }
    }

    companion object {
        private const val SCREEN = "Notices"
        private const val CONFIRM_MS = 3000L
    }
}
