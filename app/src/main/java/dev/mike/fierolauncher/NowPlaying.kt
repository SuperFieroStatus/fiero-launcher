package dev.mike.fierolauncher

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.mike.fierolauncher.ui.KeyRouter

/**
 * Notification-listener access: lets Fiero read other apps' MediaSessions (Now Playing) and list
 * notifications on its own Notices screen. Grant once: `adb shell cmd notification allow_listener <this component>`.
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
        try {
            activeNotifications.orEmpty().forEach(::quietIfUnwanted)
        } catch (e: Exception) {
            Log.w(KeyRouter.TAG, "listener: can't read notifications: $e")
        }
        Notices.changed()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        Notices.changed()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let(::quietIfUnwanted)
        Notices.changed()
    }

    /**
     * Keeps the status-bar count to texts, missed calls and voicemail: anything else (Wi-Fi offers, USB,
     * app notices, Android's "Fiero is displaying over other apps") is snoozed, which takes it out of the
     * status bar and the shade. Fiero's Notices screen still lists it. Re-posted notices are quieted again.
     */
    private fun quietIfUnwanted(sbn: StatusBarNotification) {
        val n = Notices.from(sbn)
        if (!Notices.shouldQuiet(n.pkg, n.transport, n.clearable)) return
        try {
            snoozeNotification(sbn.key, Notices.SNOOZE_MS)
            Log.i(KeyRouter.TAG, "listener: quieted ${sbn.packageName} ${sbn.notification.channelId}")
        } catch (e: Exception) {
            Log.w(KeyRouter.TAG, "listener: can't quiet ${sbn.key}: $e")
        }
    }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Notices.changed()

    companion object {
        /** The bound listener (same process), or null before the system connects it. */
        @Volatile
        var instance: MediaListener? = null
            private set
    }
}

/**
 * Follows the most relevant active MediaSession (playing first, Spotify preferred) and reports a
 * one-line summary, or null when there's nothing to show / no listener access.
 */
class NowPlaying(context: Context, private val onChange: (Line?) -> Unit) {

    data class Line(val playing: Boolean, val title: String, val subtitle: String, val pkg: String)

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val msm = app.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(app, MediaListener::class.java)
    private var controllers: List<MediaController> = emptyList()
    private var started = false

    /** Controller currently shown; transport keys go here. */
    var current: MediaController? = null
        private set

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        rebind(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = publish()
    }

    fun start() {
        if (started) return
        try {
            msm.addOnActiveSessionsChangedListener(sessionsChanged, listener, main)
            rebind(msm.getActiveSessions(listener))
            started = true
        } catch (e: SecurityException) {
            Log.w(KeyRouter.TAG, "NowPlaying: no notification-listener access ($e)")
            onChange(null)
        }
    }

    fun stop() {
        if (!started) return
        msm.removeOnActiveSessionsChangedListener(sessionsChanged)
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = emptyList()
        current = null
        started = false
    }

    private fun rebind(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers = list
        controllers.forEach { it.registerCallback(callback, main) }
        publish()
    }

    private fun publish() {
        val withMeta = controllers.filter { !it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() }
        val pick = withMeta.firstOrNull { it.isPlaying() }
            ?: withMeta.firstOrNull { it.packageName == SPOTIFY }
            ?: withMeta.firstOrNull()
        current = pick
        if (pick == null) return onChange(null)
        val md = pick.metadata!!
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val sub = md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            .ifBlank { md.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty() }
        onChange(Line(pick.isPlaying(), title, sub, pick.packageName))
    }

    fun togglePlayPause(): Boolean {
        val c = current ?: return false
        if (c.isPlaying()) c.transportControls.pause() else c.transportControls.play()
        return true
    }

    /** Pauses every session that's playing; never starts anything. Returns how many it paused. */
    fun pauseAll(): Int {
        val playing = controllers.filter { it.isPlaying() }
        playing.forEach { it.transportControls.pause() }
        return playing.size
    }

    private fun MediaController.isPlaying() = playbackState?.state == PlaybackState.STATE_PLAYING

    companion object {
        const val SPOTIFY = "com.spotify.music"
    }
}
