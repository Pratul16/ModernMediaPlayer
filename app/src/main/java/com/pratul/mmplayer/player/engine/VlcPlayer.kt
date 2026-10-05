@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package com.pratul.mmplayer.player.engine

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.pratul.mmplayer.data.settings.AppSettings
import com.pratul.mmplayer.data.settings.DecoderMode
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import kotlin.math.abs
import java.io.File
import java.util.concurrent.Executors

/**
 * libVLC exposed as a Media3 [Player], so PlayerView's controls, track menus and (later) the
 * MediaSession work unchanged. Used as a fallback for containers and codecs Media3 cannot handle:
 * WMV/WMA (ASF), RealMedia, DivX/Xvid and MPEG-2 on devices without those decoders, and others.
 *
 * All methods run on the main thread; libVLC delivers its events there too.
 */
class VlcPlayer(context: Context, settings: AppSettings) : SimpleBasePlayer(Looper.getMainLooper()) {

    private val appContext = context.applicationContext
    private val libVlc = LibVLC(appContext, vlcOptions(settings))
    private val mediaPlayer = MediaPlayer(libVlc)
    private val hardwareDecoding = settings.decoderMode != DecoderMode.SOFTWARE
    private val seekIncrementMs = settings.doubleTapSeekSeconds * 1000L

    private var mediaItems: List<MediaItem> = emptyList()
    private var currentIndex = 0
    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE
    private var durationMs = C.TIME_UNSET
    private var seekable = true
    private var videoSize = VideoSize.UNKNOWN
    private var playerError: PlaybackException? = null
    private var speed = settings.defaultPlaybackSpeed
    private var volume = 1f
    private var boost = 1f
    private var tracks = Tracks.EMPTY
    private var trackParameters = TrackSelectionParameters.DEFAULT
    private var pendingStartMs = C.TIME_UNSET
    private var pendingSeekMs = C.TIME_UNSET
    private var pendingSeekAt = 0L
    private var startedOnce = false
    /** Touched only on the worker thread. */
    private val openDescriptors = ArrayList<ParcelFileDescriptor>()
    private var videoView: View? = null
    @Volatile private var released = false
    @Volatile private var loadGeneration = 0
    private var pendingLoadUri: Uri? = null

    /**
     * libVLC's stop/release (and opening a file) can block for a long time, e.g. while a decoder
     * shuts down. They run here, in order, so the UI thread never waits on them.
     */
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "vlc-control") }
    private val mainHandler = Handler(Looper.getMainLooper())

    /** VLC must have its video surface before playback starts, or it cannot create a video output. */
    private var loadWaitingForSurface = false
    private val startWithoutSurface = Runnable {
        // No surface arrived (audio-only use, or the view is not attached yet): start anyway.
        if (loadWaitingForSurface) {
            loadWaitingForSurface = false
            startLoad()
        }
    }

    private val layoutListener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
        if (v.width > 0 && v.height > 0) mediaPlayer.vlcVout.setWindowSize(v.width, v.height)
    }

    init {
        mediaPlayer.setEventListener(::onVlcEvent)
    }

    /** Adds an external subtitle file and shows it. */
    fun addSubtitle(uri: Uri) {
        worker.execute { if (!released) mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, uri, true) }
    }

    /** Positive = subtitles appear later. Applies to embedded and external subtitles. */
    fun setSubtitleDelay(delayMs: Long) {
        mediaPlayer.setSpuDelay(delayMs * 1000)
    }

    /** Extra gain above 100% (1f..2f) used by the volume-boost gesture. */
    fun setBoost(gain: Float) {
        boost = gain.coerceIn(1f, 2f)
        applyVolume()
    }

    override fun getState(): State {
        val items = mediaItems.mapIndexed { index, item ->
            val isCurrent = index == currentIndex
            MediaItemData.Builder(index)
                .setMediaItem(item)
                .setDurationUs(if (isCurrent && durationMs != C.TIME_UNSET) Util.msToUs(durationMs) else C.TIME_UNSET)
                .setIsSeekable(seekable)
                .setTracks(if (isCurrent) tracks else Tracks.EMPTY)
                .build()
        }
        val state = if (items.isEmpty() && playbackState != Player.STATE_IDLE) Player.STATE_ENDED else playbackState
        return State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlaylist(items)
            .setCurrentMediaItemIndex(currentIndex.coerceAtMost((items.size - 1).coerceAtLeast(0)))
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (playerError != null) Player.STATE_IDLE else state)
            .setPlayerError(playerError)
            .setContentPositionMs { currentPositionMs() }
            .setPlaybackParameters(PlaybackParameters(speed))
            .setVideoSize(videoSize)
            .setVolume(volume)
            .setTrackSelectionParameters(trackParameters)
            .setSeekBackIncrementMs(seekIncrementMs)
            .setSeekForwardIncrementMs(seekIncrementMs)
            .build()
    }

    private fun currentPositionMs(): Long = when {
        pendingSeekMs != C.TIME_UNSET -> pendingSeekMs
        pendingStartMs != C.TIME_UNSET -> pendingStartMs
        playbackState == Player.STATE_ENDED && durationMs != C.TIME_UNSET -> durationMs
        else -> mediaPlayer.time.coerceAtLeast(0)
    }

    override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        this.mediaItems = mediaItems
        currentIndex = if (startIndex == C.INDEX_UNSET) 0 else startIndex
        pendingStartMs = if (startPositionMs == C.TIME_UNSET) C.TIME_UNSET else startPositionMs
        if (playbackState != Player.STATE_IDLE) loadCurrent()
        return done()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        playerError = null
        loadCurrent()
        return done()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        when {
            !mediaPlayer.hasMedia() -> Unit
            playWhenReady && playbackState == Player.STATE_ENDED -> restartFrom(0)
            playWhenReady -> mediaPlayer.play()
            else -> mediaPlayer.pause()
        }
        return done()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val target = if (positionMs == C.TIME_UNSET) 0 else positionMs
        if (mediaItemIndex != currentIndex && mediaItemIndex in mediaItems.indices) {
            currentIndex = mediaItemIndex
            pendingStartMs = target
            loadCurrent()
            return done()
        }
        if (playbackState == Player.STATE_ENDED) {
            restartFrom(target)
        } else {
            pendingSeekMs = target
            pendingSeekAt = SystemClock.uptimeMillis()
            // Precise, not keyframe-snapped: a short jump must not land back where it started.
            mediaPlayer.setTime(target, false)
        }
        return done()
    }

    private fun restartFrom(positionMs: Long) {
        pendingStartMs = positionMs
        startedOnce = false
        playbackState = Player.STATE_BUFFERING
        worker.execute {
            mediaPlayer.stop()
            mediaPlayer.play()
        }
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        speed = playbackParameters.speed
        mediaPlayer.setRate(speed)
        return done()
    }

    override fun handleSetVolume(volume: Float, volumeOperationType: Int): ListenableFuture<*> {
        this.volume = volume
        applyVolume()
        return done()
    }

    override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> {
        val vout = mediaPlayer.vlcVout
        if (vout.areViewsAttached()) vout.detachViews()
        videoView?.removeOnLayoutChangeListener(layoutListener)
        when (videoOutput) {
            is SurfaceView -> vout.setVideoView(videoOutput)
            is TextureView -> vout.setVideoView(videoOutput)
            else -> return done()
        }
        val view = videoOutput as View
        if (view.width > 0 && view.height > 0) vout.setWindowSize(view.width, view.height)
        vout.attachViews()
        videoView = view
        view.addOnLayoutChangeListener(layoutListener)
        if (loadWaitingForSurface) {
            loadWaitingForSurface = false
            mainHandler.removeCallbacks(startWithoutSurface)
            startLoad()
        }
        return done()
    }

    override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> {
        videoView?.removeOnLayoutChangeListener(layoutListener)
        videoView = null
        if (mediaPlayer.vlcVout.areViewsAttached()) mediaPlayer.vlcVout.detachViews()
        return done()
    }

    override fun handleSetTrackSelectionParameters(parameters: TrackSelectionParameters): ListenableFuture<*> {
        trackParameters = parameters
        if (C.TRACK_TYPE_TEXT in parameters.disabledTrackTypes) {
            mediaPlayer.setSpuTrack(-1)
        }
        parameters.overrides.values.forEach { override ->
            val id = override.mediaTrackGroup.getFormat(0).id ?: return@forEach
            val vlcId = id.substringAfter(':').toIntOrNull() ?: return@forEach
            when {
                id.startsWith(AUDIO_PREFIX) -> mediaPlayer.setAudioTrack(vlcId)
                id.startsWith(TEXT_PREFIX) && C.TRACK_TYPE_TEXT !in parameters.disabledTrackTypes ->
                    mediaPlayer.setSpuTrack(vlcId)
            }
        }
        refreshTracks()
        return done()
    }

    override fun handleStop(): ListenableFuture<*> {
        playbackState = Player.STATE_IDLE
        worker.execute { mediaPlayer.stop() }
        return done()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        loadWaitingForSurface = false
        mainHandler.removeCallbacks(startWithoutSurface)
        mediaPlayer.setEventListener(null)
        videoView?.removeOnLayoutChangeListener(layoutListener)
        videoView = null
        if (mediaPlayer.vlcVout.areViewsAttached()) mediaPlayer.vlcVout.detachViews()
        // Stopping can block until VLC's decoder threads exit; never do it on the UI thread.
        worker.execute {
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.release() }
            runCatching { libVlc.release() }
            closeDescriptors()
        }
        worker.shutdown()
        return done()
    }

    private fun loadCurrent() {
        mediaItems.getOrNull(currentIndex)?.localConfiguration?.uri ?: return
        durationMs = C.TIME_UNSET
        tracks = Tracks.EMPTY
        videoSize = VideoSize.UNKNOWN
        startedOnce = false
        playbackState = Player.STATE_BUFFERING
        if (videoView == null) {
            loadWaitingForSurface = true
            mainHandler.removeCallbacks(startWithoutSurface)
            mainHandler.postDelayed(startWithoutSurface, SURFACE_WAIT_MS)
        } else {
            startLoad()
        }
    }

    /** Opens and starts the current item on the worker thread. */
    private fun startLoad() {
        val uri = mediaItems.getOrNull(currentIndex)?.localConfiguration?.uri ?: return
        // Ignore a repeated request for the item already loaded: a second load would stop the first
        // and close the file VLC is still opening ("Bad file descriptor").
        if (uri == pendingLoadUri) return
        pendingLoadUri = uri
        val generation = ++loadGeneration
        val rate = speed
        worker.execute {
            if (released || generation != loadGeneration) return@execute
            try {
                mediaPlayer.stop()
                val media = createMedia(uri)
                media.setHWDecoderEnabled(hardwareDecoding, false)
                mediaPlayer.media = media
                media.release()
                mediaPlayer.setRate(rate)
                mediaPlayer.play()
            } catch (e: Exception) {
                mainHandler.post { if (!released && generation == loadGeneration) fail(e) }
            }
        }
    }

    /**
     * Library files are opened by path, which VLC reads natively. Other content (files shared from
     * other apps) goes through a file descriptor that stays open until the player is released:
     * VLC finishes with a file asynchronously, and closing descriptors early let a new file reuse
     * the same number, which VLC then closed ("Bad file descriptor" when moving to the next item).
     */
    private fun createMedia(uri: Uri): IMedia {
        if (uri.scheme != "content") return Media(libVlc, uri)
        readablePath(uri)?.let { return Media(libVlc, it) }
        val pfd = appContext.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot open $uri")
        openDescriptors += pfd
        return Media(libVlc, pfd.fileDescriptor)
    }

    private fun readablePath(uri: Uri): String? {
        if (uri.authority != MediaStore.AUTHORITY) return null
        @Suppress("DEPRECATION")
        val path = runCatching {
            appContext.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        return path?.takeIf { File(it).canRead() }
    }

    private fun onVlcEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Opening -> playbackState = Player.STATE_BUFFERING
            MediaPlayer.Event.Playing -> {
                playbackState = Player.STATE_READY
                if (!startedOnce) {
                    startedOnce = true
                    if (pendingStartMs != C.TIME_UNSET && pendingStartMs > 0) {
                        mediaPlayer.setTime(pendingStartMs, false)
                    }
                    pendingStartMs = C.TIME_UNSET
                    applyVolume()
                    if (!playWhenReady) mediaPlayer.pause()
                }
                refreshTracks()
                refreshVideoSize()
            }
            MediaPlayer.Event.Paused -> playbackState = Player.STATE_READY
            MediaPlayer.Event.LengthChanged -> durationMs = event.lengthChanged.takeIf { it > 0 } ?: C.TIME_UNSET
            MediaPlayer.Event.SeekableChanged -> seekable = event.seekable
            MediaPlayer.Event.TimeChanged -> {
                if (pendingSeekMs == C.TIME_UNSET) return
                // Time updates from before the seek can still arrive; keep reporting the target
                // until VLC is actually there (or a moment has passed).
                val arrived = abs(event.timeChanged - pendingSeekMs) < SEEK_ARRIVED_MS
                if (arrived || SystemClock.uptimeMillis() - pendingSeekAt > SEEK_TIMEOUT_MS) pendingSeekMs = C.TIME_UNSET
            }
            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> refreshTracks()
            MediaPlayer.Event.Vout -> refreshVideoSize()
            MediaPlayer.Event.EndReached -> playbackState = Player.STATE_ENDED
            MediaPlayer.Event.EncounteredError -> {
                fail(null)
                return
            }
            else -> return
        }
        invalidateState()
    }

    private fun fail(cause: Throwable?) {
        pendingLoadUri = null // allow "Try again" to reload the same file
        playerError = PlaybackException(
            "VLC could not play this file",
            cause,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        )
        playbackState = Player.STATE_IDLE
        playWhenReady = false
        invalidateState()
    }

    private fun refreshVideoSize() {
        val track = mediaPlayer.currentVideoTrack ?: return
        if (track.width <= 0 || track.height <= 0) return
        val ratio = if (track.sarNum > 0 && track.sarDen > 0) track.sarNum.toFloat() / track.sarDen else 1f
        videoSize = VideoSize(track.width, track.height, ratio)
    }

    /** Mirrors VLC's ES list into Media3 [Tracks] so the standard track menus can show and switch them. */
    private fun refreshTracks() {
        val groups = buildList {
            mediaPlayer.videoTracks?.filter { it.id >= 0 }?.forEach { track ->
                add(group("$VIDEO_PREFIX${track.id}", track.name, MimeTypes.VIDEO_UNKNOWN, track.id == mediaPlayer.videoTrack))
            }
            mediaPlayer.audioTracks?.filter { it.id >= 0 }?.forEach { track ->
                add(group("$AUDIO_PREFIX${track.id}", track.name, MimeTypes.AUDIO_UNKNOWN, track.id == mediaPlayer.audioTrack))
            }
            mediaPlayer.spuTracks?.filter { it.id >= 0 }?.forEach { track ->
                add(group("$TEXT_PREFIX${track.id}", track.name, MimeTypes.TEXT_UNKNOWN, track.id == mediaPlayer.spuTrack))
            }
        }
        tracks = Tracks(groups)
    }

    private fun group(id: String, label: String?, mimeType: String, selected: Boolean): Tracks.Group {
        val format = Format.Builder()
            .setId(id)
            .setLabel(label)
            .setSampleMimeType(mimeType)
            .build()
        return Tracks.Group(TrackGroup(id, format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(selected))
    }

    private fun applyVolume() {
        mediaPlayer.setVolume((volume * boost * 100).toInt().coerceIn(0, 200))
    }

    private fun closeDescriptors() {
        openDescriptors.forEach { runCatching { it.close() } }
        openDescriptors.clear()
    }

    private fun done(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private companion object {
        const val SEEK_ARRIVED_MS = 1_500L
        const val SEEK_TIMEOUT_MS = 3_000L
        const val SURFACE_WAIT_MS = 1_500L
        const val VIDEO_PREFIX = "vlc-video:"
        const val AUDIO_PREFIX = "vlc-audio:"
        const val TEXT_PREFIX = "vlc-text:"

        val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_RELEASE,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SET_SPEED_AND_PITCH,
                Player.COMMAND_SET_VIDEO_SURFACE,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_GET_TRACKS,
                Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS,
                Player.COMMAND_GET_VOLUME,
                Player.COMMAND_SET_VOLUME,
            )
            .build()

        fun vlcOptions(settings: AppSettings): ArrayList<String> = arrayListOf(
            "--audio-time-stretch",
            "--avcodec-skiploopfilter=1",
            "--freetype-rel-fontsize=${vlcFontSize(settings.subtitleSizeSp)}",
            "--freetype-color=${settings.subtitleColor.argb and 0xFFFFFF}",
            "--freetype-bold".takeIf { settings.subtitleBold } ?: "--no-freetype-bold",
            "--freetype-background-opacity=${if (settings.subtitleBackground) 160 else 0}",
            "--freetype-outline-thickness=4",
        )

        /** libVLC sizes subtitles relative to the video height: larger number = smaller text. */
        private fun vlcFontSize(sizeSp: Int): Int = when {
            sizeSp <= 14 -> 20
            sizeSp <= 18 -> 18
            sizeSp <= 22 -> 16
            sizeSp <= 28 -> 13
            else -> 10
        }
    }
}
