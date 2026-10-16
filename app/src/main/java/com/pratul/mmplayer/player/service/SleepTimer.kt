package com.pratul.mmplayer.player.service

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A running sleep timer: either a countdown, or "stop when the current video/track ends". */
data class SleepTimerState(
    /** Milliseconds left, or null for [endOfItem]. */
    val remainingMs: Long?,
    val endOfItem: Boolean = false,
)

/**
 * Pauses playback after a while or at the end of the current item, with a gentle fade-out.
 * It acts on whatever [PlaybackHolder] says is playing, so it keeps working after the player
 * screen is closed and music continues in the background.
 */
class SleepTimer(
    private val context: Context,
    private val holder: PlaybackHolder,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SleepTimerState?>(null)
    val state: StateFlow<SleepTimerState?> = _state.asStateFlow()

    private var job: Job? = null

    /** Pause after [minutes]. */
    fun start(minutes: Int) {
        cancel()
        val endAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        _state.value = SleepTimerState(remainingMs = minutes * 60_000L)
        job = scope.launch(Dispatchers.Main) {
            while (isActive) {
                val left = endAt - SystemClock.elapsedRealtime()
                if (left <= 0) break
                _state.value = SleepTimerState(remainingMs = left)
                // Start fading out over the last few seconds.
                if (left <= FADE_MS) currentPlayer()?.let { setVolume(it, left.toFloat() / FADE_MS) }
                delay(if (left <= FADE_MS) 200 else 1_000)
            }
            fallAsleep()
        }
    }

    /** Pause when the video or track that's playing now finishes (before the next one starts). */
    fun startEndOfItem() {
        cancel()
        _state.value = SleepTimerState(remainingMs = null, endOfItem = true)
        job = scope.launch(Dispatchers.Main) {
            val player = currentPlayer()
            val item = player?.currentMediaItemIndex
            while (isActive) {
                val p = currentPlayer() ?: break
                val duration = p.duration
                val position = p.currentPosition
                val nearEnd = duration != C.TIME_UNSET && duration > 0 && duration - position < END_MARGIN_MS
                val moved = p.currentMediaItemIndex != item || p.playbackState == Player.STATE_ENDED
                if (nearEnd || moved) break
                delay(250)
            }
            fallAsleep()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        if (_state.value != null) currentPlayer()?.let { setVolume(it, 1f) }
        _state.value = null
    }

    private fun fallAsleep() {
        val player = currentPlayer()
        if (player != null) {
            player.pause()
            setVolume(player, 1f)
        }
        _state.value = null
        job = null
        Toast.makeText(context, "Sleep timer: playback paused", Toast.LENGTH_SHORT).show()
    }

    private fun currentPlayer(): Player? = holder.nowPlaying.value?.player

    private fun setVolume(player: Player, volume: Float) {
        runCatching { player.volume = volume.coerceIn(0f, 1f) }
    }

    private companion object {
        const val FADE_MS = 10_000L
        const val END_MARGIN_MS = 400L
    }
}
