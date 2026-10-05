@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package com.pratul.mmplayer.player

import android.app.Application
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.pratul.mmplayer.ModernMediaApp
import com.pratul.mmplayer.data.database.entity.PlaybackHistoryEntity
import com.pratul.mmplayer.data.settings.AppSettings
import com.pratul.mmplayer.data.settings.AspectRatioMode
import com.pratul.mmplayer.data.settings.DecoderMode
import com.pratul.mmplayer.data.settings.ResumeMode
import com.pratul.mmplayer.data.settings.SettingKeys
import com.pratul.mmplayer.player.engine.ExoPlayerFactory
import com.pratul.mmplayer.player.engine.VlcPlayer
import com.pratul.mmplayer.player.session.PlaybackQueue
import com.pratul.mmplayer.player.session.QueueBuilder
import com.pratul.mmplayer.player.session.QueueEntry
import com.pratul.mmplayer.player.service.NowPlaying
import com.pratul.mmplayer.player.subtitles.ExternalSubtitles
import com.pratul.mmplayer.player.subtitles.SubtitleSource
import com.pratul.mmplayer.player.subtitles.SubtitleTrack
import com.pratul.mmplayer.utils.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.Locale

enum class Engine(val badge: String) { EXO_HARDWARE("HW"), EXO_HARDWARE_PLUS("HW+"), EXO_SOFTWARE("SW"), VLC("VLC") }

data class FileInfo(val uri: Uri, val name: String, val sizeBytes: Long?, val mimeType: String?)

/** "Continue watching from 1:17:32?" — auto-continues when [secondsLeft] reaches 0. */
data class ResumePrompt(val positionMs: Long, val secondsLeft: Int)

/** Shown when an item finishes and another follows. [secondsLeft] null = auto-play is off. */
data class UpNext(val title: String, val secondsLeft: Int?)

data class SubtitleUiState(
    val files: List<SubtitleSource> = emptyList(),
    val active: Uri? = null,
    val delayMs: Long = 0,
    val loading: Boolean = false,
)

data class PlayerUiState(
    val player: Player? = null,
    val engine: Engine = Engine.EXO_HARDWARE_PLUS,
    val title: String = "",
    val file: FileInfo? = null,
    /** Set when every available engine failed; drives the "Unable to play" dialog. */
    val error: String? = null,
    val triedVlc: Boolean = false,
    val triedSoftware: Boolean = false,
    val aspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val zoom: Float = 1f,
    val locked: Boolean = false,
    val orientationLocked: Boolean = false,
    val queueSize: Int = 1,
    val queueIndex: Int = 0,
    val resumePrompt: ResumePrompt? = null,
    val upNext: UpNext? = null,
    val subtitles: SubtitleUiState = SubtitleUiState(),
    /** Short notice such as "Resumed from 12:30"; cleared automatically. */
    val message: String? = null,
)

/**
 * Owns playback for [PlayerActivity]:
 * - engine choice and automatic fallback (hardware → FFmpeg → VLC);
 * - the queue (the rest of the folder, in episode order) with "Up next" and auto-play;
 * - memory per video: resume position, audio and subtitle choice, speed, aspect ratio, zoom;
 * - external subtitle files: found automatically, rendered with an adjustable delay.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as ModernMediaApp).container
    private val settingsRepository = container.settingsRepository
    private val historyDao = container.database.playbackHistoryDao()
    private val queueBuilder = QueueBuilder(application, container.database.mediaDao())
    private val holder = container.playbackHolder

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** Cues of the active external subtitle file (rendered by the screen, not by the engine). */
    private val _externalCues = MutableStateFlow<List<Cue>>(emptyList())
    val externalCues: StateFlow<List<Cue>> = _externalCues.asStateFlow()

    private var queue = PlaybackQueue(emptyList(), 0)
    private var currentIndex = 0
    private var openedUri: Uri? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    // Per-item session state.
    private var history: PlaybackHistoryEntity? = null
    private var resumeDecided = true
    private var tracksRestored = false
    private var historyLoaded = false
    private var countedPlay = false
    private var lastKnownPositionMs = 0L
    /** Furthest point actually reached while playing this item (guards against bogus "ended" events). */
    private var playedMs = 0L
    private var userSpeed = 1f
    private var subtitleTrack: SubtitleTrack? = null
    private val parsedSubtitles = HashMap<Uri, SubtitleTrack>()

    private var resumeJob: Job? = null
    private var upNextJob: Job? = null
    private var messageJob: Job? = null
    private var subtitleJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (_state.value.engine == Engine.VLC) {
                _state.update { it.copy(error = describe(error)) }
            } else {
                handleExoError(error)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val player = _state.value.player ?: return
            if (player is ExoPlayer && (tracks.hasNoPlayable(C.TRACK_TYPE_VIDEO) || tracks.hasNoPlayable(C.TRACK_TYPE_AUDIO))) {
                // Media3 opened the file but cannot decode a track: hand over to VLC rather than
                // playing silently or showing a black screen.
                fallBackToVlc()
                return
            }
            if (!tracksRestored && historyLoaded && !tracks.isEmpty) {
                tracksRestored = true
                restoreTrackChoices(player, tracks)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = _state.value.player?.currentMediaItemIndex ?: return
            if (index != currentIndex && index in queue.entries.indices) {
                saveHistory()
                startItem(index, allowResume = true)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) itemFinished()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) itemFinished()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) saveHistory()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            loudnessEnhancer?.release()
            loudnessEnhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
        }
    }

    init {
        // One ticker for position tracking, periodic history saves and external subtitle cues.
        viewModelScope.launch {
            var ticks = 0
            while (isActive) {
                delay(TICK_MS)
                val player = _state.value.player ?: continue
                if (player.playbackState != Player.STATE_IDLE) lastKnownPositionMs = player.currentPosition
                if (player.isPlaying) playedMs = maxOf(playedMs, player.currentPosition)
                updateExternalCues(player)
                if (++ticks % (SAVE_INTERVAL_MS / TICK_MS).toInt() == 0 && player.isPlaying) saveHistory()
            }
        }
    }

    // --- Opening ---------------------------------------------------------------------------

    fun open(uri: Uri, mimeType: String?, queueIds: List<Long>? = null) {
        if (uri == openedUri && _state.value.player != null) return
        openedUri = uri
        viewModelScope.launch {
            saveHistory()
            val current = settingsRepository.current()
            queue = withContext(Dispatchers.IO) {
                queueIds?.let { ids -> queueBuilder.fromIds(ids, startIndex = 0) }
                    ?: queueBuilder.build(uri, mimeType)
            }
            currentIndex = queue.startIndex.takeIf { queueIds == null } ?: queue.entries.indexOfFirst { it.uri == uri }.coerceAtLeast(0)
            holder.adopt(uri)?.let { running ->
                adoptRunning(running.player)
                return@launch
            }
            _state.update {
                it.copy(
                    error = null,
                    triedVlc = false,
                    triedSoftware = false,
                    queueSize = queue.entries.size,
                    upNext = null,
                    resumePrompt = null,
                )
            }
            // Stay paused until we know whether to resume (decided in startItem).
            resumeDecided = false
            val first = queue.entries.getOrNull(currentIndex)
            val engine = if (current.alwaysUseVlc || first?.prefersVlc() == true) Engine.VLC else exoEngine(current.decoderMode)
            startEngine(engine, currentIndex, 0L, current)
            startItem(currentIndex, allowResume = true)
        }
    }

    /** Re-attaches to playback that continued in the background (notification / mini player tap). */
    private fun adoptRunning(player: Player) {
        currentIndex = player.currentMediaItemIndex.coerceIn(0, (queue.entries.size - 1).coerceAtLeast(0))
        player.addListener(listener)
        (player as? ExoPlayer)?.pauseAtEndOfMediaItems = queue.entries.size > 1
        val engine = if (player is VlcPlayer) Engine.VLC else exoEngine(settings.value.decoderMode)
        _state.update {
            it.copy(player = player, engine = engine, error = null, queueSize = queue.entries.size, upNext = null, resumePrompt = null)
        }
        startItem(currentIndex, allowResume = false)
        holder.attach(nowPlayingFor(player, currentIndex))
    }

    /** Loads everything remembered about the item at [index] and applies it. */
    private fun startItem(index: Int, allowResume: Boolean) {
        currentIndex = index
        val entry = queue.entries.getOrNull(index) ?: return
        val settings = settings.value
        holder.update { it.copy(uri = entry.uri, mimeType = entry.mimeType, title = entry.title, isAudio = entry.isAudio) }
        history = null
        tracksRestored = false
        historyLoaded = false
        countedPlay = false
        resumeDecided = !allowResume
        lastKnownPositionMs = 0L
        playedMs = 0L
        subtitleTrack = null
        _externalCues.value = emptyList()
        upNextJob?.cancel()
        resumeJob?.cancel()
        _state.update {
            it.copy(
                title = entry.title,
                file = FileInfo(entry.uri, entry.title, entry.sizeBytes, entry.mimeType),
                queueIndex = index,
                upNext = null,
                resumePrompt = null,
                aspectRatio = settings.defaultAspectRatio,
                zoom = 1f,
                subtitles = SubtitleUiState(loading = true),
            )
        }
        viewModelScope.launch {
            val saved = entry.mediaId?.let { id -> withContext(Dispatchers.IO) { historyDao.get(id) } }
            history = saved
            historyLoaded = true
            val player = _state.value.player ?: return@launch
            if (!tracksRestored && !player.currentTracks.isEmpty) {
                tracksRestored = true
                restoreTrackChoices(player, player.currentTracks)
            }

            // Per-video speed, aspect ratio and zoom.
            userSpeed = saved?.playbackSpeed ?: settings.defaultPlaybackSpeed
            player.playbackParameters = PlaybackParameters(userSpeed)
            val aspect = saved?.aspectRatio?.let { name -> AspectRatioMode.entries.firstOrNull { it.name == name } }
            _state.update { it.copy(aspectRatio = aspect ?: settings.defaultAspectRatio, zoom = saved?.zoom ?: 1f) }
            applySubtitleDelay(saved?.subtitleDelayMs ?: 0)

            if (allowResume) decideResume(player, saved, settings)
            loadSubtitles(entry, saved, settings)
        }
    }

    private fun decideResume(player: Player, saved: PlaybackHistoryEntity?, settings: AppSettings) {
        val position = saved?.positionMs ?: 0L
        val worthResuming = saved != null && !saved.completed && position > MIN_RESUME_MS &&
            (saved.durationMs <= 0 || position < saved.durationMs - END_MARGIN_MS)
        if (!worthResuming || settings.resumeMode == ResumeMode.NEVER) {
            resumeDecided = true
            player.playWhenReady = true
            return
        }
        if (settings.resumeMode == ResumeMode.ALWAYS) {
            player.seekTo(position)
            resumeDecided = true
            player.playWhenReady = true
            showMessage("Resumed from ${formatDuration(position)}")
            return
        }
        // ASK: pause and offer to continue; continue automatically after a few seconds.
        player.playWhenReady = false
        resumeJob = viewModelScope.launch {
            for (left in RESUME_COUNTDOWN downTo 1) {
                _state.update { it.copy(resumePrompt = ResumePrompt(position, left)) }
                delay(1000)
            }
            answerResume(continueWatching = true)
        }
    }

    fun answerResume(continueWatching: Boolean) {
        val prompt = _state.value.resumePrompt ?: return
        resumeJob?.cancel()
        val player = _state.value.player
        if (continueWatching) player?.seekTo(prompt.positionMs) else player?.seekTo(0)
        player?.playWhenReady = true
        resumeDecided = true
        _state.update { it.copy(resumePrompt = null) }
    }

    // --- Remembering -------------------------------------------------------------------------

    private fun saveHistory(completed: Boolean = false) {
        val entry = queue.entries.getOrNull(currentIndex) ?: return
        val mediaId = entry.mediaId ?: return
        // Never overwrite a saved position while the user is still deciding whether to resume.
        if (!settings.value.saveHistory || !resumeDecided) return
        val player = _state.value.player
        val position = player?.currentPosition?.takeIf { player.playbackState != Player.STATE_IDLE } ?: lastKnownPositionMs
        val duration = player?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: history?.durationMs ?: 0L
        if (position <= 0 && !completed && history == null) return
        val done = completed || (duration > 0 && position >= duration * COMPLETE_FRACTION)
        val previous = history
        val entity = PlaybackHistoryEntity(
            mediaId = mediaId,
            positionMs = if (done) 0 else position,
            durationMs = duration,
            lastPlayedAt = System.currentTimeMillis(),
            completed = done || (previous?.completed == true && position < MIN_RESUME_MS),
            playCount = (previous?.playCount ?: 0) + if (countedPlay) 0 else 1,
            audioTrack = player?.let { selectedKey(it, C.TRACK_TYPE_AUDIO) } ?: previous?.audioTrack,
            subtitleUri = _state.value.subtitles.active?.toString(),
            subtitleDelayMs = _state.value.subtitles.delayMs,
            playbackSpeed = userSpeed,
            subtitleTrack = player?.let { textChoice(it) } ?: previous?.subtitleTrack,
            aspectRatio = _state.value.aspectRatio.name,
            zoom = _state.value.zoom,
        )
        countedPlay = true
        history = entity
        container.appScope.launch { historyDao.upsert(entity) }
    }

    /** The choice to remember for a track type: language, else label, else nothing. */
    private fun selectedKey(player: Player, type: Int): String? =
        player.currentTracks.groups.firstOrNull { it.type == type && it.isSelected }
            ?.getTrackFormat(0)?.trackKey()

    private fun textChoice(player: Player): String? = when {
        _state.value.subtitles.active != null -> null
        C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes -> TEXT_OFF
        else -> selectedKey(player, C.TRACK_TYPE_TEXT)
    }

    private fun restoreTrackChoices(player: Player, tracks: Tracks) {
        val saved = history ?: return
        saved.audioTrack?.let { key ->
            tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.getTrackFormat(0).trackKey() == key }
                ?.let { player.select(it, C.TRACK_TYPE_AUDIO) }
        }
        when (val key = saved.subtitleTrack) {
            null -> Unit
            TEXT_OFF -> player.setTextEnabled(false)
            else -> tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.getTrackFormat(0).trackKey() == key }
                ?.let { player.select(it, C.TRACK_TYPE_TEXT) }
        }
    }

    // --- Queue and auto-play ------------------------------------------------------------------

    private fun itemFinished() {
        if (_state.value.upNext != null || _state.value.error != null) return
        val duration = _state.value.player?.duration ?: C.TIME_UNSET
        if (playedMs < EARLY_END_MS && (duration == C.TIME_UNSET || duration > MIN_REAL_LENGTH_MS) && resumeDecided) {
            // "Ended" before anything played: a decoder gave up, not the end of the video.
            _state.update { it.copy(error = "Unable to play this file on this device.") }
            return
        }
        saveHistory(completed = true)
        val next = queue.entries.getOrNull(currentIndex + 1) ?: return
        val autoPlay = settings.value.autoPlayNext
        upNextJob?.cancel()
        if (!autoPlay) {
            _state.update { it.copy(upNext = UpNext(next.title, null)) }
            return
        }
        upNextJob = viewModelScope.launch {
            for (left in UP_NEXT_COUNTDOWN downTo 1) {
                _state.update { it.copy(upNext = UpNext(next.title, left)) }
                delay(1000)
            }
            playNext()
        }
    }

    fun playNext() {
        upNextJob?.cancel()
        _state.update { it.copy(upNext = null) }
        val player = _state.value.player ?: return
        if (currentIndex + 1 >= queue.entries.size) return
        player.seekTo(currentIndex + 1, 0)
        player.playWhenReady = true
    }

    fun cancelUpNext() {
        upNextJob?.cancel()
        _state.update { it.copy(upNext = null) }
    }

    // --- Subtitles ----------------------------------------------------------------------------

    private fun loadSubtitles(entry: QueueEntry, saved: PlaybackHistoryEntity?, settings: AppSettings) {
        subtitleJob?.cancel()
        subtitleJob = viewModelScope.launch {
            val app = getApplication<Application>()
            val found = if (settings.autoLoadSubtitles) {
                withContext(Dispatchers.IO) { ExternalSubtitles.find(app, entry, queue.entries.size) }
            } else {
                emptyList()
            }
            // A file chosen manually last time (not next to the video) is offered again too.
            val remembered = saved?.subtitleUri?.let(Uri::parse)
            val files = buildList {
                addAll(found)
                if (remembered != null && found.none { it.uri == remembered }) add(ExternalSubtitles.describe(app, remembered))
            }
            _state.update { it.copy(subtitles = it.subtitles.copy(files = files, loading = false)) }

            val toActivate = when {
                remembered != null -> files.firstOrNull { it.uri == remembered }
                saved?.subtitleTrack == TEXT_OFF -> null
                saved?.subtitleTrack != null -> null // an embedded track was chosen last time
                settings.autoLoadSubtitles && settings.subtitlesEnabledByDefault -> pickBest(found, settings)
                else -> null
            }
            toActivate?.let { activateSubtitle(it, announce = remembered == null) }
        }
    }

    private fun pickBest(files: List<SubtitleSource>, settings: AppSettings): SubtitleSource? {
        if (files.isEmpty()) return null
        val preferred = settings.preferredSubtitleLanguage.ifBlank { Locale.getDefault().language }
        return files.firstOrNull { it.language == preferred } ?: files.firstOrNull { it.language == null } ?: files.first()
    }

    /** Subtitle file picked in the options menu (or with "Load subtitle file…"). */
    fun selectSubtitleFile(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val source = _state.value.subtitles.files.firstOrNull { it.uri == uri }
                ?: withContext(Dispatchers.IO) { ExternalSubtitles.describe(app, uri) }.also { added ->
                    _state.update { it.copy(subtitles = it.subtitles.copy(files = it.subtitles.files + added)) }
                }
            activateSubtitle(source, announce = true)
        }
    }

    private suspend fun activateSubtitle(source: SubtitleSource, announce: Boolean) {
        val player = _state.value.player ?: return
        if (player is VlcPlayer) {
            player.addSubtitle(source.uri)
        } else {
            val track = parsedSubtitles[source.uri] ?: withContext(Dispatchers.IO) {
                ExternalSubtitles.load(getApplication(), source, settings.value.subtitleEncoding)
            }?.also { parsedSubtitles[source.uri] = it }
            if (track == null) {
                showMessage("Couldn't read ${source.name}")
                return
            }
            subtitleTrack = track
            // Show only the file's subtitles, not an embedded track on top of them.
            player.setTextEnabled(false)
        }
        _state.update { it.copy(subtitles = it.subtitles.copy(active = source.uri)) }
        if (announce) showMessage("Subtitles: ${source.name}")
        saveHistory()
    }

    /** Embedded track chosen, or subtitles turned off: stop showing the external file. */
    fun clearExternalSubtitle() {
        subtitleTrack = null
        _externalCues.value = emptyList()
        _state.update { it.copy(subtitles = it.subtitles.copy(active = null)) }
    }

    fun setSubtitleDelay(delayMs: Long) {
        applySubtitleDelay(delayMs.coerceIn(-MAX_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS))
        saveHistory()
    }

    private fun applySubtitleDelay(delayMs: Long) {
        _state.update { it.copy(subtitles = it.subtitles.copy(delayMs = delayMs)) }
        (_state.value.player as? VlcPlayer)?.setSubtitleDelay(delayMs)
    }

    private fun updateExternalCues(player: Player) {
        val track = subtitleTrack ?: return
        if (player is VlcPlayer) return
        val timeUs = (player.currentPosition - _state.value.subtitles.delayMs) * 1000
        val cues = track.cuesAt(timeUs)
        if (cues != _externalCues.value) _externalCues.value = cues
    }

    // --- Engine ------------------------------------------------------------------------------

    /** Re-opens the current file with a specific engine (from the player's options menu). */
    fun switchEngine(engine: Engine) {
        val position = _state.value.player?.currentPosition ?: 0L
        viewModelScope.launch {
            val current = settingsRepository.current()
            val decoderMode = when (engine) {
                Engine.EXO_HARDWARE -> DecoderMode.HARDWARE
                Engine.EXO_SOFTWARE -> DecoderMode.SOFTWARE
                else -> DecoderMode.HARDWARE_PLUS
            }
            restartEngine(engine, position, current.copy(decoderMode = decoderMode))
        }
    }

    fun retry() {
        _state.update { it.copy(error = null, triedVlc = false, triedSoftware = false) }
        viewModelScope.launch {
            val current = settingsRepository.current()
            restartEngine(exoEngine(current.decoderMode), 0L, current)
        }
    }

    /** New engine for the same item: keeps position, speed, subtitles and track choices. */
    private fun restartEngine(engine: Engine, positionMs: Long, settings: AppSettings) {
        val player = _state.value.player
        val keepTracks = player?.let { selectedKey(it, C.TRACK_TYPE_AUDIO) }
        startEngine(engine, currentIndex, positionMs, settings)
        tracksRestored = false
        history = history?.copy(audioTrack = keepTracks ?: history?.audioTrack)
        _state.value.player?.playbackParameters = PlaybackParameters(userSpeed)
        applySubtitleDelay(_state.value.subtitles.delayMs)
        val active = _state.value.subtitles.files.firstOrNull { it.uri == _state.value.subtitles.active }
        if (active != null) viewModelScope.launch { activateSubtitle(active, announce = false) }
    }

    private fun startEngine(engine: Engine, index: Int, positionMs: Long, settings: AppSettings) {
        if (queue.entries.isEmpty()) return
        releasePlayer()
        val app = getApplication<Application>()
        val player: Player = if (engine == Engine.VLC) {
            VlcPlayer(app, settings)
        } else {
            ExoPlayerFactory.create(app, settings).also {
                // Stop at the end of each item so "Up next" can count down (or wait) first.
                it.pauseAtEndOfMediaItems = queue.entries.size > 1
            }
        }
        val items = queue.entries.map { entry ->
            MediaItem.Builder()
                .setUri(entry.uri)
                .setMediaId(entry.uri.toString())
                .setMimeType(entry.mimeType)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.title).build())
                .build()
        }
        player.addListener(listener)
        player.setMediaItems(items, index, positionMs)
        player.prepare()
        player.playWhenReady = resumeDecided
        _state.update { it.copy(player = player, engine = engine, triedVlc = it.triedVlc || engine == Engine.VLC) }
        holder.attach(nowPlayingFor(player, index))
    }

    /**
     * Automatic decoder fallback, like HW → SW in other players:
     * a decoder failure first retries with FFmpeg software decoding, then (or for container
     * problems) moves to VLC. File-not-found and permission errors go straight to the dialog.
     */
    private fun handleExoError(error: PlaybackException) {
        val current = _state.value
        val decoderProblem = error.errorCode in 4000..4999
        val formatProblem = error.errorCode in 3000..3999
        when {
            decoderProblem && current.engine != Engine.EXO_SOFTWARE && !current.triedSoftware -> {
                _state.update { it.copy(triedSoftware = true) }
                switchAfterError(Engine.EXO_SOFTWARE, settings.value.copy(decoderMode = DecoderMode.SOFTWARE))
            }
            (decoderProblem || formatProblem) && settings.value.vlcFallback && !current.triedVlc -> fallBackToVlc()
            else -> _state.update { it.copy(error = describe(error)) }
        }
    }

    private fun fallBackToVlc() {
        val current = _state.value
        if (current.engine == Engine.VLC || current.triedVlc || !settings.value.vlcFallback) return
        _state.update { it.copy(triedVlc = true) }
        switchAfterError(Engine.VLC, settings.value)
    }

    private fun switchAfterError(engine: Engine, settings: AppSettings) {
        val position = lastKnownPositionMs
        // Posted so the failing ExoPlayer is not released from inside its own listener callback.
        viewModelScope.launch {
            yield()
            restartEngine(engine, position, settings)
        }
    }

    // --- Simple controls ----------------------------------------------------------------------

    fun setSpeed(speed: Float) {
        userSpeed = speed
        _state.value.player?.playbackParameters = PlaybackParameters(speed)
        saveHistory()
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _state.update { it.copy(aspectRatio = mode, zoom = 1f) }
        saveHistory()
    }

    fun setZoom(zoom: Float) = _state.update { it.copy(zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)) }

    fun commitZoom() = saveHistory()

    fun setLocked(locked: Boolean) = _state.update { it.copy(locked = locked) }

    fun setOrientationLocked(locked: Boolean) = _state.update { it.copy(orientationLocked = locked) }

    /** Extra loudness above the device maximum, 0f (off) .. 1f (about +12 dB). */
    fun setVolumeBoost(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        when (val player = _state.value.player) {
            is VlcPlayer -> player.setBoost(1f + clamped)
            else -> loudnessEnhancer?.run {
                setTargetGain((clamped * MAX_BOOST_MB).toInt())
                enabled = clamped > 0f
            }
        }
    }

    fun setLongPressSpeed(speed: Float) {
        if (speed == settings.value.longPressSpeed) return
        viewModelScope.launch { settingsRepository.set(SettingKeys.LONG_PRESS_SPEED, speed) }
    }

    fun rememberBrightness(value: Float) {
        if (!settings.value.rememberBrightness) return
        viewModelScope.launch { settingsRepository.set(SettingKeys.LAST_BRIGHTNESS, value) }
    }

    fun setPlaying(playing: Boolean) {
        _state.value.player?.playWhenReady = playing
        if (!playing) saveHistory()
    }

    private fun showMessage(text: String) {
        messageJob?.cancel()
        _state.update { it.copy(message = text) }
        messageJob = viewModelScope.launch {
            delay(MESSAGE_MS)
            _state.update { it.copy(message = null) }
        }
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The file no longer exists. It may have been moved, deleted or its SD card removed."
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "Modern Media Player does not have permission to read this file."
        else -> "Unable to play this file on this device."
    }

    private fun exoEngine(mode: DecoderMode) = when (mode) {
        DecoderMode.HARDWARE -> Engine.EXO_HARDWARE
        DecoderMode.HARDWARE_PLUS -> Engine.EXO_HARDWARE_PLUS
        DecoderMode.SOFTWARE -> Engine.EXO_SOFTWARE
    }

    private fun releasePlayer() {
        val player = _state.value.player ?: return
        holder.forget(player)
        player.removeListener(listener)
        player.release()
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        _state.update { it.copy(player = null) }
    }

    /**
     * True when leaving the player should not stop playback: audio files always continue, videos
     * when "Play audio in background" is on. Only while something is actually playing.
     */
    fun shouldContinueInBackground(): Boolean {
        val player = _state.value.player ?: return false
        if (!player.playWhenReady || player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) return false
        val isAudio = queue.entries.getOrNull(currentIndex)?.isAudio == true ||
            player.currentTracks.groups.none { it.type == C.TRACK_TYPE_VIDEO }
        return isAudio || settings.value.backgroundAudio
    }

    override fun onCleared() {
        saveHistory()
        val player = _state.value.player
        if (player != null && shouldContinueInBackground()) {
            // Hand the running player to the media service instead of releasing it.
            player.removeListener(listener)
            loudnessEnhancer?.release()
            loudnessEnhancer = null
            holder.detach(settings.value.autoPlayNext)
        } else {
            releasePlayer()
        }
    }

    private fun nowPlayingFor(player: Player, index: Int): NowPlaying {
        val entry = queue.entries.getOrNull(index)
        return NowPlaying(
            player = player,
            uri = entry?.uri ?: openedUri ?: Uri.EMPTY,
            mimeType = entry?.mimeType,
            title = entry?.title ?: _state.value.title,
            isAudio = entry?.isAudio == true,
        )
    }

    companion object {
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 4f
        const val MAX_SUBTITLE_DELAY_MS = 30_000L
        private const val MAX_BOOST_MB = 1200
        private const val TICK_MS = 100L
        private const val SAVE_INTERVAL_MS = 5_000L
        private const val MIN_RESUME_MS = 5_000L
        private const val END_MARGIN_MS = 15_000L
        private const val COMPLETE_FRACTION = 0.95
        private const val RESUME_COUNTDOWN = 8
        private const val UP_NEXT_COUNTDOWN = 5
        private const val MESSAGE_MS = 2_500L
        private const val TEXT_OFF = "off"
        private const val EARLY_END_MS = 3_000L
        private const val MIN_REAL_LENGTH_MS = 10_000L
    }
}

private fun Tracks.hasNoPlayable(type: Int): Boolean =
    groups.any { it.type == type } && groups.none { it.type == type && it.isSupported }

private fun Format.trackKey(): String? =
    language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED } ?: label?.takeIf { it.isNotBlank() }

private fun Player.select(group: Tracks.Group, type: Int) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        .setTrackTypeDisabled(type, false)
        .build()
}

private fun Player.setTextEnabled(enabled: Boolean) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .apply { if (!enabled) clearOverridesOfType(C.TRACK_TYPE_TEXT) }
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
        .build()
}

/**
 * Containers Media3 handles poorly or not at all (AVI with DivX/Xvid packed bitstreams, Windows
 * Media, RealMedia, DVD VOB, MPEG program streams, OGM) start straight on the VLC engine instead
 * of failing first and falling back.
 */
private val VLC_FIRST_EXTENSIONS = setOf(
    "avi", "divx", "xvid", "wmv", "wma", "asf", "rm", "rmvb", "ra", "vob", "mpg", "mpeg", "m2v", "mpe", "ogm", "dat", "f4v", "amv",
)
private val VLC_FIRST_MIME_TYPES = setOf(
    "video/avi", "video/x-msvideo", "video/msvideo", "video/divx", "video/x-ms-wmv", "video/x-ms-asf", "audio/x-ms-wma",
    "application/vnd.rn-realmedia", "application/vnd.rn-realmedia-vbr", "video/mpeg", "video/x-mpeg",
)

private fun QueueEntry.prefersVlc(): Boolean =
    title.substringAfterLast('.', "").lowercase() in VLC_FIRST_EXTENSIONS ||
        mimeType?.lowercase() in VLC_FIRST_MIME_TYPES
