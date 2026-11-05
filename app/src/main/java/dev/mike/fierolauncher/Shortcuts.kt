package dev.mike.fierolauncher

import android.content.ComponentName
import android.content.Context

/**
 * Home-screen shortcuts (four D-pad directions + the right soft key), kept in Fiero's own prefs (never
 * Settings.Global, which Launcher4 owns and rewrites). Stored as "label|package/class".
 */
object Shortcuts {
    enum class Dir(val key: String, val arrow: String, val title: String) {
        UP("up", "▲", "Up"),
        DOWN("down", "▼", "Down"),
        LEFT("left", "◀", "Left"),
        RIGHT("right", "▶", "Right"),
        SOFT_RIGHT("softright", "⏵", "Right soft key"),
        ;

        /** How the shortcut menu names this slot. */
        val menuName: String get() = if (this == SOFT_RIGHT) title else "$arrow key"
    }

    data class Shortcut(val label: String, val component: ComponentName)

    /**
     * Starting shortcuts (the author's TCL Flip 2 setup). A default whose app isn't installed shows as
     * empty, so on other phones the slots start blank until you assign them.
     */
    private val defaults = mapOf(
        Dir.UP to Shortcut("Spotify", ComponentName("com.spotify.music", "com.spotify.music.MainActivity")),
        Dir.DOWN to Shortcut(
            "Maps",
            ComponentName("com.google.android.apps.mapslite", "com.google.maps.lite.twa.MapsLiteTwaLauncherActivity"),
        ),
        Dir.LEFT to Shortcut("F-Droid", ComponentName("org.fdroid.fdroid", "org.fdroid.IconActivity")),
        Dir.RIGHT to Shortcut("FlipSpot", ComponentName("dev.mike.flipspot", "dev.mike.flipspot.HomeActivity")),
        Dir.SOFT_RIGHT to Shortcut("FlipSpot", ComponentName("dev.mike.flipspot", "dev.mike.flipspot.HomeActivity")),
    )

    private fun installedDefault(context: Context, dir: Dir): Shortcut? =
        defaults[dir]?.takeIf { context.packageManager.getLaunchIntentForPackage(it.component.packageName) != null }

    private fun prefs(context: Context) = context.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)

    fun get(context: Context, dir: Dir): Shortcut? {
        val raw = prefs(context).getString(dir.key, null) ?: return installedDefault(context, dir)
        if (raw.isEmpty()) return null // explicitly cleared
        val label = raw.substringBefore('|')
        val component = ComponentName.unflattenFromString(raw.substringAfter('|')) ?: return installedDefault(context, dir)
        return Shortcut(label, component)
    }

    fun set(context: Context, dir: Dir, shortcut: Shortcut?) {
        val value = shortcut?.let { "${it.label}|${it.component.flattenToString()}" } ?: ""
        prefs(context).edit().putString(dir.key, value).apply()
    }
}
