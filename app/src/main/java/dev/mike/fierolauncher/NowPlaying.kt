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
import android.util.Log
import dev.mike.fierolauncher.ui.KeyRouter

/**
 * Exists only so Fiero holds notification-listener access, which is what lets it read other apps'
 * MediaSessions. Grant once: `adb shell cmd notification allow_listener <this component>`.
 */
class MediaListener : NotificationListenerService()

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

    private fun MediaController.isPlaying() = playbackState?.state == PlaybackState.STATE_PLAYING

    companion object {
        const val SPOTIFY = "com.spotify.music"
    }
}
