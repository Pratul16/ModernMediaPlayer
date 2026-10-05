@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package com.pratul.mmplayer.player.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What is playing right now, app-wide. */
data class NowPlaying(
    val player: Player,
    val uri: Uri,
    val mimeType: String?,
    val title: String,
    val isAudio: Boolean,
    /** True once the player screen has closed and playback continues in the background. */
    val detached: Boolean = false,
)

/**
 * Process-wide owner of "the" player. While the player screen is open its ViewModel drives the
 * player and keeps this holder up to date; when the screen closes during audio (or video with
 * "Play audio in background"), the player is handed over here instead of being released, and
 * [PlaybackService] keeps it alive with a media notification, lock-screen and headset controls.
 */
class PlaybackHolder(private val context: Context) {

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** Set by the background listener: continue to the next item when one finishes. */
    var autoPlayNext: Boolean = true

    // Media3's engine holds its own wake lock; the VLC engine needs one while playing in the background.
    private val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ModernMediaPlayer:background")
        .apply { setReferenceCounted(false) }

    private fun updateWakeLock() {
        val current = _nowPlaying.value
        val needed = current != null && current.detached && current.player !is ExoPlayer && current.player.isPlaying
        if (needed && !wakeLock.isHeld) wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        if (!needed && wakeLock.isHeld) wakeLock.release()
    }

    private val backgroundListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateWakeLock()

        override fun onPlaybackStateChanged(playbackState: Int) {
            val current = _nowPlaying.value ?: return
            if (playbackState != Player.STATE_ENDED) return
            val player = current.player
            if (autoPlayNext && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.play()
            }
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            val current = _nowPlaying.value ?: return
            mediaItem ?: return
            _nowPlaying.value = current.copy(
                uri = mediaItem.localConfiguration?.uri ?: current.uri,
                mimeType = mediaItem.localConfiguration?.mimeType,
                title = mediaItem.mediaMetadata.title?.toString() ?: current.title,
            )
        }
    }

    /** The player screen started (or changed) a player. Starts the media service. */
    fun attach(nowPlaying: NowPlaying) {
        val previous = _nowPlaying.value
        if (previous != null && previous.player !== nowPlaying.player && previous.detached) {
            // A background player from earlier gives way to the new one.
            previous.player.removeListener(backgroundListener)
            previous.player.release()
        }
        _nowPlaying.value = nowPlaying.copy(detached = false)
        // Plain start (the app is in the foreground here); Media3 promotes the service to a foreground
        // service with its notification once playback actually runs.
        runCatching { context.startService(Intent(context, PlaybackService::class.java)) }
    }

    /** Keeps the item title/uri in sync while the screen is open (queue moves to the next file). */
    fun update(transform: (NowPlaying) -> NowPlaying) {
        _nowPlaying.value?.let { _nowPlaying.value = transform(it) }
    }

    /** The player screen closed but playback should continue. */
    fun detach(autoPlayNext: Boolean) {
        val current = _nowPlaying.value ?: return
        this.autoPlayNext = autoPlayNext
        // Without the screen there is no "Up next" card, so let the queue continue on its own.
        (current.player as? ExoPlayer)?.pauseAtEndOfMediaItems = !autoPlayNext
        current.player.addListener(backgroundListener)
        _nowPlaying.value = current.copy(detached = true)
        updateWakeLock()
    }

    /** The player screen reopened for the same file: give it the running player. */
    fun adopt(uri: Uri): NowPlaying? {
        val current = _nowPlaying.value ?: return null
        if (!current.detached || current.uri != uri) return null
        current.player.removeListener(backgroundListener)
        _nowPlaying.value = current.copy(detached = false)
        updateWakeLock()
        return current
    }

    /** Drops [player] from the holder (the screen released it, e.g. when switching engines). */
    fun forget(player: Player) {
        if (_nowPlaying.value?.player === player) _nowPlaying.value = null
    }

    /** Stop everything: from the notification, the mini player's close button, or task removal. */
    fun stop() {
        val current = _nowPlaying.value ?: return
        _nowPlaying.value = null
        updateWakeLock()
        current.player.removeListener(backgroundListener)
        if (current.detached) {
            current.player.stop()
            current.player.release()
        } else {
            current.player.pause()
        }
    }

    val isPlayingInBackground: Boolean
        get() = _nowPlaying.value?.let { it.detached && it.player.playWhenReady && it.player.playbackState != Player.STATE_IDLE } == true

    /** Position for "Continue" when the screen reopens a detached player. */
    fun positionOf(player: Player): Long = player.currentPosition.takeIf { it != C.TIME_UNSET } ?: 0L
}

/** Safety net so a forgotten lock can never drain the battery (renewed on each play). */
private const val WAKE_LOCK_TIMEOUT_MS = 4 * 60 * 60 * 1000L
