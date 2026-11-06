package dev.mike.fierolauncher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import android.util.Log
import android.view.KeyEvent
import android.widget.TextClock
import android.widget.TextView
import dev.mike.fierolauncher.ui.KeyAction
import dev.mike.fierolauncher.ui.KeyActionHandler
import dev.mike.fierolauncher.ui.KeyRouter
import dev.mike.fierolauncher.ui.RoutedKey
import dev.mike.fierolauncher.ui.SignalBarsView
import dev.mike.fierolauncher.ui.say
import java.util.Locale

class HomeActivity : Activity(), KeyActionHandler {

    private lateinit var signal: SignalBarsView
    private lateinit var carrier: TextView
    private lateinit var battery: TextView
    private lateinit var hints: TextView
    private lateinit var softRight: TextView
    private lateinit var softLeft: TextView
    private val noticesChanged: () -> Unit = { showNoticeCount() }
    private lateinit var weather: TextView
    private val weatherChanged: () -> Unit = { showWeather() }
    private lateinit var telephony: TelephonyManager
    private lateinit var audio: AudioManager
    private lateinit var nowPlayingView: TextView
    private lateinit var nowPlaying: NowPlaying
    private var batteryText = ""

    /** '*' or '#' pressed and not yet long-pressed: acted on at key-up (short press dials it). */
    private var pendingShort: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        signal = findViewById(R.id.signal)
        carrier = findViewById(R.id.carrier)
        battery = findViewById(R.id.battery)
        hints = findViewById(R.id.hints)
        softRight = findViewById(R.id.soft_right)
        softLeft = findViewById(R.id.soft_left)
        weather = findViewById(R.id.weather)
        telephony = getSystemService(TelephonyManager::class.java)
        audio = getSystemService(AudioManager::class.java)
        nowPlayingView = findViewById(R.id.now_playing)
        nowPlaying = NowPlaying(this, ::showNowPlaying)

        findViewById<TextClock>(R.id.clock).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
        }
        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd")
        findViewById<TextClock>(R.id.date).apply {
            format12Hour = datePattern
            format24Hour = datePattern
        }
        Log.i(KeyRouter.TAG, "HomeActivity created")
        handleHomeAlias(intent)
        handleWallpaper(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleHomeAlias(intent)
        handleWallpaper(intent)
    }

    /**
     * `am start ... --ez home_alias true|false` (deploy.ps1 -SetHome / -RevertHome). adb can't toggle
     * components of a non-debuggable app, but an app may always toggle its own.
     */
    /** `--es wallpaper black|restore` (see [Wallpaper]): black wallpaper = black outer screen. */
    private fun handleWallpaper(intent: Intent?) {
        val what = intent?.getStringExtra(EXTRA_WALLPAPER) ?: return
        intent.removeExtra(EXTRA_WALLPAPER)
        val msg = try {
            Wallpaper.apply(this, what)
        } catch (e: Exception) {
            "Wallpaper change failed: ${e.message}"
        }
        Log.i(KeyRouter.TAG, msg)
        say(msg)
    }

    private fun handleHomeAlias(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_HOME_ALIAS) != true) return
        val on = intent.getBooleanExtra(EXTRA_HOME_ALIAS, false)
        packageManager.setComponentEnabledSetting(
            android.content.ComponentName(this, "$packageName.HomeAlias"),
            if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP,
        )
        Log.i(KeyRouter.TAG, "HomeAlias ${if (on) "enabled" else "disabled"}")
        intent.removeExtra(EXTRA_HOME_ALIAS)
    }

    override fun onResume() {
        super.onResume()
        OuterScreen.retry() // in case "display over other apps" was just granted
        showShortcuts()
        // Sticky broadcast: registering returns the current state immediately.
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::showBattery)
        @Suppress("DEPRECATION")
        telephony.listen(signalListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        showSignal(telephony.signalStrength)
        registerReceiver(ringerReceiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION))
        nowPlaying.start()
        Notices.observe(noticesChanged)
        showNoticeCount()
        Weather.observe(weatherChanged)
        showWeather()
        Weather.refreshIfStale(this)
    }

    private fun showWeather() {
        val w = Weather.current(this)
        weather.visibility = if (w == null) android.view.View.GONE else android.view.View.VISIBLE
        if (w != null) weather.text = Weather.line(w)
    }

    /** "Notices 3" on the left soft key; plain "Notices" when there's nothing (or no access). */
    private fun showNoticeCount() {
        val n = Notices.current()?.count { it.clearable } ?: 0
        softLeft.text = if (n > 0) "Notices $n" else "Notices"
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(batteryReceiver)
        @Suppress("DEPRECATION")
        telephony.listen(signalListener, PhoneStateListener.LISTEN_NONE)
        unregisterReceiver(ringerReceiver)
        Notices.unobserve(noticesChanged)
        Weather.unobserve(weatherChanged)
        nowPlaying.stop()
        pendingShort = 0
    }

    // ---- glance row ----

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = showBattery(intent)
    }

    private fun showBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (level < 0) return
        batteryText = "${if (plugged) "⚡" else ""}${level * 100 / scale.coerceAtLeast(1)}%"
        showStatus()
    }

    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = showStatus()
    }

    /** Battery, prefixed with the ringer mode when it isn't Normal. */
    private fun showStatus() {
        val ringer = when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "Silent  "
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate  "
            else -> ""
        }
        battery.text = ringer + batteryText
    }

    private fun showNowPlaying(line: NowPlaying.Line?) {
        if (line == null) {
            nowPlayingView.visibility = android.view.View.GONE
            return
        }
        val text = if (line.subtitle.isEmpty()) line.title else "${line.title} · ${line.subtitle}"
        nowPlayingView.text = "${if (line.playing) "▶" else "❚❚"} $text"
        nowPlayingView.visibility = android.view.View.VISIBLE
    }

    @Suppress("DEPRECATION")
    private val signalListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(strength: SignalStrength?) = showSignal(strength)
    }

    private fun showSignal(strength: SignalStrength?) {
        val name = telephony.networkOperatorName.orEmpty()
        signal.level = if (strength == null || name.isEmpty()) 0 else strength.level
        carrier.text = name.ifEmpty { "No service" }
    }

    private fun showShortcuts() {
        fun label(d: Shortcuts.Dir) = "${d.arrow} ${Shortcuts.get(this, d)?.label ?: "—"}"
        hints.text = "${label(Shortcuts.Dir.UP)}   ${label(Shortcuts.Dir.DOWN)}\n" +
            "${label(Shortcuts.Dir.LEFT)}   ${label(Shortcuts.Dir.RIGHT)}"
        softRight.text = Shortcuts.get(this, Shortcuts.Dir.SOFT_RIGHT)?.label.orEmpty()
    }

    // ---- keys ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // KeyRouter consumes the key-up of handled keys without telling us; short * / # act on it.
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == pendingShort) {
            pendingShort = 0
            dial(if (event.keyCode == KeyEvent.KEYCODE_STAR) "*" else "#")
        }
        return KeyRouter.dispatch(event, SCREEN, this) || super.dispatchKeyEvent(event)
    }

    override fun onKeyAction(key: RoutedKey, event: KeyEvent): Boolean {
        if (key.action == KeyAction.STAR || key.action == KeyAction.POUND) {
            // Short press dials it (on key-up); long press: * = play/pause, # = ringer.
            if (event.repeatCount == 0) {
                pendingShort = event.keyCode
            } else if (event.isLongPress && pendingShort == event.keyCode) {
                pendingShort = 0
                if (key.action == KeyAction.STAR) playPause() else toggleRinger()
            }
            return true
        }
        if (event.repeatCount > 0) return true // nothing else on Home auto-repeats
        when (key.action) {
            KeyAction.UP -> openShortcut(Shortcuts.Dir.UP)
            KeyAction.DOWN -> openShortcut(Shortcuts.Dir.DOWN)
            KeyAction.LEFT -> openShortcut(Shortcuts.Dir.LEFT)
            KeyAction.RIGHT -> openShortcut(Shortcuts.Dir.RIGHT)
            KeyAction.OK -> startActivity(Intent(this, AllAppsActivity::class.java))
            KeyAction.SOFT_LEFT -> startActivity(Intent(this, NoticesActivity::class.java))
            KeyAction.SOFT_RIGHT -> openShortcut(Shortcuts.Dir.SOFT_RIGHT)
            KeyAction.CALL -> dial("")
            KeyAction.DIGIT -> dial(key.digit.toString())
            KeyAction.STAR, KeyAction.POUND -> Unit // handled above
            KeyAction.BACK -> Unit // Home is the bottom of the stack: swallow Back
        }
        return true
    }

    private fun openShortcut(dir: Shortcuts.Dir) {
        val s = Shortcuts.get(this, dir)
        if (s == null) say("Nothing on ${dir.title}. Apps > Options to set") else Apps.launch(this, s.component, s.label)
    }

    /** Dialer, pre-filled with the key that was pressed (feature-phone convention). */
    private fun dial(number: String) {
        // Fresh task: the stock dialer otherwise appends to whatever was left typed in it ("5" -> "55").
        val intent = Intent(Intent.ACTION_DIAL, if (number.isEmpty()) null else Uri.fromParts("tel", number, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            say("No dialer")
        }
    }

    private fun playPause() {
        if (!nowPlaying.togglePlayPause()) say("Nothing playing")
    }

    /**
     * Normal -> Silent -> Normal. Silent needs Do Not Disturb access on Android 11; without it,
     * fall back to Vibrate (always allowed).
     */
    private fun toggleRinger() {
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
            say("Ringer on")
            return
        }
        try {
            audio.ringerMode = AudioManager.RINGER_MODE_SILENT
            say("Silent")
        } catch (e: SecurityException) {
            Log.w(KeyRouter.TAG, "silent mode refused: $e")
            audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            say("Vibrate")
        }
    }

    companion object {
        private const val SCREEN = "Home"
        private const val EXTRA_HOME_ALIAS = "home_alias"
        private const val EXTRA_WALLPAPER = "wallpaper"
    }
}
