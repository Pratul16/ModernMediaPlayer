@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package com.pratul.mmplayer.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.pratul.mmplayer.ModernMediaApp
import com.pratul.mmplayer.player.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground media service. Publishes the current player through a MediaSession, which gives the
 * media notification (title, previous / play-pause / next), lock-screen controls, Bluetooth and
 * headset buttons, Android Auto and "Now playing" in quick settings — and keeps audio playing
 * after the player screen is closed or the phone is locked.
 */
class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private val holder get() = (application as ModernMediaApp).container.playbackHolder

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            holder.nowPlaying.collect { nowPlaying ->
                if (nowPlaying == null) {
                    shutDown()
                    return@collect
                }
                val current = session
                if (current == null) {
                    session = MediaSession.Builder(this@PlaybackService, nowPlaying.player)
                        .setSessionActivity(openPlayer(nowPlaying))
                        .build()
                        .also { addSession(it) }
                } else {
                    if (current.player !== nowPlaying.player) current.player = nowPlaying.player
                    current.setSessionActivity(openPlayer(nowPlaying))
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiped away from recents: keep going only if something is actually playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            holder.stop()
            shutDown()
        }
    }

    private fun shutDown() {
        session?.let { removeSession(it); it.release() }
        session = null
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }

    /** Tapping the notification reopens the player on the current file. */
    private fun openPlayer(nowPlaying: NowPlaying): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            PlayerActivity.intent(this, nowPlaying.uri, nowPlaying.mimeType),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
