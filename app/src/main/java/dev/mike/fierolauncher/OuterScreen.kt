package dev.mike.fierolauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import dev.mike.fierolauncher.ui.KeyRouter

/**
 * The TCL Flip 2's outside: the front button and the 128×128 outer screen.
 *
 * - **Front button:** the system turns each press into the (unprotected) broadcast
 *   `com.tct.intent.action.QUICK_BUTTON_TAP`; Fiero toggles play/pause on it.
 * - **Outer screen:** TCL's own clock lives there (a SystemUI presentation). Fiero adds a small
 *   Now Playing strip along its bottom with an overlay window (needs "display over other apps",
 *   granted over adb: `appops set dev.mike.fierolauncher SYSTEM_ALERT_WINDOW allow`).
 *
 * Lives for the whole launcher process (started from [FieroApp]); the home app is kept running.
 */
object OuterScreen {
    private const val ACTION_FRONT_BUTTON = "com.tct.intent.action.QUICK_BUTTON_TAP"

    private lateinit var app: Context
    private lateinit var nowPlaying: NowPlaying
    private var lastPress = 0L

    private var windowManager: WindowManager? = null
    private var strip: View? = null
    private var title: TextView? = null
    private var artist: TextView? = null
    private var line: NowPlaying.Line? = null

    fun start(context: Context) {
        app = context.applicationContext
        nowPlaying = NowPlaying(app) { l -> line = l; render() }
        nowPlaying.start()

        app.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = frontButton()
        }, IntentFilter(ACTION_FRONT_BUTTON))

        val dm = app.getSystemService(DisplayManager::class.java)
        dm.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = attach()
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) = Unit
        }, null)
        attach()
    }

    // ---- front button ----

    private fun frontButton() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPress < 400) return // one press can arrive twice; ignore the echo
        lastPress = now
        // Notification access may have been granted after start: retry the session watch.
        nowPlaying.start()
        if (nowPlaying.togglePlayPause()) return
        // Nothing loaded: a media key resumes the app that played last (e.g. Spotify).
        val audio = app.getSystemService(AudioManager::class.java)
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
        Log.i(KeyRouter.TAG, "front button: sent MEDIA_PLAY")
    }

    // ---- outer screen ----

    private fun outerDisplay(): Display? =
        app.getSystemService(DisplayManager::class.java)
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

    private fun attach() {
        if (strip != null) return
        val display = outerDisplay() ?: return
        if (!Settings.canDrawOverlays(app)) {
            Log.i(KeyRouter.TAG, "outer screen: no overlay permission")
            return
        }
        val ctx = app.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        val wm = ctx.getSystemService(WindowManager::class.java)
        val pad = (3 * ctx.resources.displayMetrics.density).toInt()
        val t = TextView(ctx).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            paint.isFakeBoldText = true
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        val a = TextView(ctx).apply {
            setTextColor(0xFFFFB37A.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE0000000.toInt())
            setPadding(pad, pad / 2, pad, pad / 2)
            addView(t)
            addView(a)
            visibility = View.GONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }
        try {
            wm.addView(box, lp)
        } catch (e: RuntimeException) {
            Log.w(KeyRouter.TAG, "outer screen: can't add overlay: $e")
            return
        }
        windowManager = wm
        strip = box
        title = t
        artist = a
        render()
        Log.i(KeyRouter.TAG, "outer screen: Now Playing strip attached to display ${display.displayId}")
    }

    private fun render() {
        val box = strip ?: return
        val l = line
        if (l == null) {
            box.visibility = View.GONE
            return
        }
        title?.text = "${if (l.playing) "▶" else "❚❚"} ${l.title}"
        artist?.text = l.subtitle
        artist?.visibility = if (l.subtitle.isEmpty()) View.GONE else View.VISIBLE
        box.visibility = View.VISIBLE
    }

    /** Called when the overlay permission may have just been granted (launcher resumed). */
    fun retry() {
        if (::app.isInitialized) attach()
    }
}
