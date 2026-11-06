package dev.mike.fierolauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import dev.mike.fierolauncher.ui.KeyRouter

/**
 * Current weather from Fiero Weather (dev.mike.fieroweather), for the home screen and the outer screen.
 * Fiero itself never goes online: Fiero Weather refreshes every 30 minutes and sends ready-to-show text
 * here; Fiero only asks it for a refresh when what it has is getting old. Both directions need a
 * signature permission, so only apps signed with the same key can take part.
 */
object Weather {
    data class Info(
        val symbol: String,
        val temp: String,
        val text: String,
        val hilo: String,
        val place: String,
        val at: Long,
        /** % chance of rain in the next 12 hours, -1 if unknown. */
        val rain: Int,
    ) {
        val age: Long get() = System.currentTimeMillis() - at

        /** "☂️ 30%", or "" when unknown. */
        val umbrella: String get() = if (rain < 0) "" else "☂️ $rain%"
    }

    const val PERMISSION = "dev.mike.fiero.permission.WEATHER"
    private const val WEATHER_PACKAGE = "dev.mike.fieroweather"
    private const val ACTION_REFRESH = "dev.mike.fieroweather.action.REFRESH"

    /** Older than this: ask Fiero Weather for a refresh. */
    private const val STALE_MS = 40 * 60 * 1000L
    /** Older than this: don't show it at all (it would mislead). */
    const val EXPIRED_MS = 6 * 60 * 60 * 1000L

    private val main = Handler(Looper.getMainLooper())
    private val observers = LinkedHashSet<() -> Unit>()
    private var lastAsk = 0L

    fun observe(o: () -> Unit) { observers += o }
    fun unobserve(o: () -> Unit) { observers -= o }

    private fun prefs(c: Context) = c.getSharedPreferences("weather", Context.MODE_PRIVATE)

    /** Last weather received, or null if none or too old to show. */
    fun current(c: Context): Info? {
        val p = prefs(c)
        val at = p.getLong("at", 0L)
        if (at == 0L) return null
        val info = Info(
            p.getString("symbol", "").orEmpty(), p.getString("temp", "").orEmpty(), p.getString("text", "").orEmpty(),
            p.getString("hilo", "").orEmpty(), p.getString("place", "").orEmpty(), at, p.getInt("rain", -1),
        )
        return info.takeIf { it.age < EXPIRED_MS && it.temp.isNotEmpty() }
    }

    internal fun store(c: Context, i: Intent) {
        prefs(c).edit()
            .putString("symbol", i.getStringExtra("symbol"))
            .putString("temp", i.getStringExtra("temp"))
            .putString("text", i.getStringExtra("text"))
            .putString("hilo", i.getStringExtra("hilo"))
            .putString("place", i.getStringExtra("place"))
            .putLong("at", i.getLongExtra("at", System.currentTimeMillis()))
            .putInt("rain", i.getIntExtra("rain", -1))
            .apply()
        main.post { observers.toList().forEach { it() } }
    }

    /** Asks Fiero Weather to refresh if what we have is old (at most every few minutes). No-op if it isn't installed. */
    fun refreshIfStale(c: Context) {
        val info = current(c)
        if (info != null && info.age < STALE_MS) return
        val now = System.currentTimeMillis()
        if (now - lastAsk < 5 * 60 * 1000L) return
        lastAsk = now
        c.sendBroadcast(Intent(ACTION_REFRESH).setPackage(WEATHER_PACKAGE), PERMISSION)
    }

    /** One line for the home screen: "☁️ 60° Cloudy · ☂️ 30% · H 72° L 56°" (the end is cut if it doesn't fit). */
    fun line(i: Info): String = listOf("${i.symbol} ${i.temp} ${i.text}", i.umbrella, i.hilo)
        .filter { it.isNotEmpty() }.joinToString(" · ")
}

class WeatherReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(KeyRouter.TAG, "weather: ${intent.getStringExtra("temp")} ${intent.getStringExtra("text")}")
        Weather.store(context, intent)
    }
}
