package dev.mike.fierolauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private lateinit var telephony: TelephonyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        signal = findViewById(R.id.signal)
        carrier = findViewById(R.id.carrier)
        battery = findViewById(R.id.battery)
        hints = findViewById(R.id.hints)
        softRight = findViewById(R.id.soft_right)
        telephony = getSystemService(TelephonyManager::class.java)

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
    }

    override fun onResume() {
        super.onResume()
        showShortcuts()
        // Sticky broadcast: registering returns the current state immediately.
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::showBattery)
        @Suppress("DEPRECATION")
        telephony.listen(signalListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        showSignal(telephony.signalStrength)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(batteryReceiver)
        @Suppress("DEPRECATION")
        telephony.listen(signalListener, PhoneStateListener.LISTEN_NONE)
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
        battery.text = "${if (plugged) "⚡" else ""}${level * 100 / scale.coerceAtLeast(1)}%"
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
    }

    // ---- keys ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        KeyRouter.dispatch(event, SCREEN, this) || super.dispatchKeyEvent(event)

    override fun onKeyAction(key: RoutedKey, event: KeyEvent): Boolean {
        if (event.repeatCount > 0) return true // nothing on Home auto-repeats
        when (key.action) {
            KeyAction.UP -> openShortcut(Shortcuts.Dir.UP)
            KeyAction.DOWN -> openShortcut(Shortcuts.Dir.DOWN)
            KeyAction.LEFT -> openShortcut(Shortcuts.Dir.LEFT)
            KeyAction.RIGHT -> openShortcut(Shortcuts.Dir.RIGHT)
            KeyAction.OK -> startActivity(Intent(this, AllAppsActivity::class.java))
            KeyAction.SOFT_LEFT -> expandNotifications()
            KeyAction.SOFT_RIGHT -> Apps.launch(this, FLIPSPOT, "FlipSpot")
            KeyAction.CALL -> dial("")
            KeyAction.DIGIT -> dial(key.digit.toString())
            KeyAction.STAR -> dial("*")
            KeyAction.POUND -> dial("#")
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

    /** StatusBarManager.expandNotificationsPanel is hidden API; needs EXPAND_STATUS_BAR (normal). */
    @SuppressLint("WrongConstant")
    private fun expandNotifications() {
        try {
            val sbm = getSystemService("statusbar")
            sbm.javaClass.getMethod("expandNotificationsPanel").invoke(sbm)
        } catch (e: Exception) {
            Log.w(KeyRouter.TAG, "expandNotificationsPanel failed: $e")
            say("Can't open notifications")
        }
    }

    companion object {
        private const val SCREEN = "Home"
        val FLIPSPOT = android.content.ComponentName("dev.mike.flipspot", "dev.mike.flipspot.HomeActivity")
    }
}
