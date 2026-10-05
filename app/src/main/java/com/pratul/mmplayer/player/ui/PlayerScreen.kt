@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package com.pratul.mmplayer.player.ui

import android.content.Context
import android.graphics.Typeface
import android.media.AudioManager
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.media3.common.text.Cue
import com.pratul.mmplayer.player.ResumePrompt
import com.pratul.mmplayer.player.SubtitleUiState
import com.pratul.mmplayer.player.UpNext
import com.pratul.mmplayer.ui.theme.GlassButton
import com.pratul.mmplayer.ui.theme.GlowButton
import com.pratul.mmplayer.ui.theme.glass
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.pratul.mmplayer.data.settings.AppSettings
import com.pratul.mmplayer.data.settings.AspectRatioMode
import com.pratul.mmplayer.data.settings.OrientationMode
import com.pratul.mmplayer.player.Engine
import com.pratul.mmplayer.player.FileInfo
import com.pratul.mmplayer.player.PlayerUiState
import com.pratul.mmplayer.player.PlayerViewModel
import com.pratul.mmplayer.utils.formatDuration
import com.pratul.mmplayer.utils.formatFileSize
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Window-level operations the screen needs from its Activity. */
interface PlayerHost {
    fun exit()
    fun enterPip()
    fun brightness(): Float
    fun setBrightness(value: Float)
    fun setOrientationOverride(mode: OrientationMode?)
    fun openWithAnotherApp(file: FileInfo)
    fun onPlaybackChanged(isPlaying: Boolean, videoSize: VideoSize)
}

private data class Feedback(val icon: ImageVector, val text: String, val progress: Float? = null, val sticky: Boolean = false)

private val SPEEDS = (1..12).map { it * 0.25f }
private const val SEEK_RANGE_MS = 90_000L
private const val STREAK_RESET_MS = 1_300L
private val QUICK_SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
private const val LONG_PRESS_STEP_WIDTH = 0.07f

/** Speeds reachable by sliding during a long press (0.25x steps). */
private val LONG_PRESS_SPEEDS = (1..12).map { it * 0.25f }

private fun nearestSpeedIndex(speed: Float): Int =
    LONG_PRESS_SPEEDS.indices.minBy { abs(LONG_PRESS_SPEEDS[it] - speed) }

@Composable
fun PlayerScreen(viewModel: PlayerViewModel, host: PlayerHost, inPip: Boolean) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var controllerVisible by remember { mutableStateOf(true) }
    var feedback by remember { mutableStateOf<Feedback?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var lockHint by remember { mutableStateOf(false) }
    var videoSize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    var isPlaying by remember { mutableStateOf(false) }
    var interaction by remember { mutableStateOf(0) }
    // Auto-hide while playing; any interaction restarts the timer. Paused video keeps controls up.
    LaunchedEffect(controllerVisible, interaction, isPlaying) {
        if (controllerVisible && isPlaying) {
            delay(settings.controllerTimeoutSeconds * 1000L)
            controllerVisible = false
        }
    }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val externalCues by viewModel.externalCues.collectAsStateWithLifecycle()
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Keep access so the choice can be remembered for this video.
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.selectSubtitleFile(uri)
        }
    }

    val latestState by rememberUpdatedState(state)
    val latestSettings by rememberUpdatedState(settings)

    DisposableEffect(state.player) {
        val player = state.player
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
                host.onPlaybackChanged(isPlaying, size)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                host.onPlaybackChanged(playing, videoSize)
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }
        }
        player?.addListener(listener)
        if (player != null) {
            videoSize = player.videoSize
            isPlaying = player.isPlaying
            tracks = player.currentTracks
            // The size may already be known (event fired before this listener existed): rotate now.
            host.onPlaybackChanged(isPlaying, videoSize)
        }
        onDispose { player?.removeListener(listener) }
    }

    LaunchedEffect(feedback) {
        val current = feedback ?: return@LaunchedEffect
        if (!current.sticky) {
            delay(700)
            feedback = null
        }
    }
    LaunchedEffect(lockHint) {
        if (lockHint) {
            delay(2500)
            lockHint = false
        }
    }

    val gestureListener = remember {
        object : PlayerGestureListener {
            private var seekStart = C.TIME_UNSET
            private var seekTarget = 0L
            private var volumeLevel = -1f
            private var brightness = -1f
            private var speedBeforeLongPress = 1f
            private var longPressActive = false
            private var longPressBaseIndex = 0
            private var longPressIndex = 0
            private var streakSide: TapZone? = null
            private var streakSeconds = 0
            private var lastStreakTap = 0L
            private var tapTarget = C.TIME_UNSET

            private val player get() = latestState.player

            override fun onSingleTap() {
                if (latestState.locked) lockHint = true else controllerVisible = !controllerVisible
            }

            override fun onDoubleTap(zone: TapZone) {
                val p = player ?: return
                controllerVisible = false
                val stepSeconds = latestSettings.doubleTapSeekSeconds
                if (zone == TapZone.CENTER) {
                    p.playWhenReady = !p.playWhenReady
                    feedback = Feedback(if (p.playWhenReady) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (p.playWhenReady) "Play" else "Pause")
                    return
                }
                // Consecutive taps on the same side add up: +10s, +20s, +30s ...
                val now = SystemClock.uptimeMillis()
                streakSeconds = if (zone == streakSide && now - lastStreakTap < STREAK_RESET_MS) streakSeconds + stepSeconds else stepSeconds
                streakSide = zone
                lastStreakTap = now
                val forward = zone == TapZone.RIGHT
                val delta = stepSeconds * 1000L * if (forward) 1 else -1
                val duration = p.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
                // Build on the previous tap's target: while a seek is still in flight the player
                // can report the old time, which would make +10, +20, +30 all land on the same spot.
                val base = if (streakSeconds > stepSeconds && tapTarget != C.TIME_UNSET) tapTarget else p.currentPosition
                tapTarget = (base + delta).coerceIn(0, duration)
                p.seekPrecisely(tapTarget)
                feedback = Feedback(
                    icon = if (forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                    text = "${if (forward) "+" else "−"}${streakSeconds}s",
                )
            }

            override fun onSeekDrag(fractionOfWidth: Float) {
                val p = player ?: return
                controllerVisible = false
                if (seekStart == C.TIME_UNSET) seekStart = p.currentPosition
                val duration = p.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
                val range = minOf(SEEK_RANGE_MS, duration)
                seekTarget = (seekStart + (fractionOfWidth * range).toLong()).coerceIn(0, duration)
                val delta = seekTarget - seekStart
                val sign = if (delta >= 0) "+" else "−"
                val total = if (duration == Long.MAX_VALUE) "" else " / ${formatDuration(duration)}"
                feedback = Feedback(
                    icon = if (delta >= 0) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                    text = "$sign${formatDuration(abs(delta))}\n${formatDuration(seekTarget)}$total",
                    sticky = true,
                )
            }

            override fun onSeekEnd() {
                player?.seekPrecisely(seekTarget)
                seekStart = C.TIME_UNSET
                feedback = feedback?.copy(sticky = false)
            }

            override fun onVerticalDrag(side: VerticalSide, deltaFractionOfHeight: Float) {
                controllerVisible = false
                val delta = deltaFractionOfHeight * 1.5f
                if (side == VerticalSide.LEFT) {
                    if (brightness < 0) brightness = host.brightness()
                    brightness = (brightness + delta).coerceIn(0.01f, 1f)
                    host.setBrightness(brightness)
                    feedback = Feedback(Icons.Rounded.Brightness6, "Brightness ${(brightness * 100).roundToInt()}%", brightness, sticky = true)
                } else {
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val ceiling = if (latestSettings.volumeBoost) 2f else 1f
                    if (volumeLevel < 0) volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                    volumeLevel = (volumeLevel + delta).coerceIn(0f, ceiling)
                    val index = (volumeLevel.coerceAtMost(1f) * max).roundToInt()
                    runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0) }
                    viewModel.setVolumeBoost((volumeLevel - 1f).coerceAtLeast(0f))
                    val label = if (volumeLevel > 1f) "Boost" else "Volume"
                    feedback = Feedback(Icons.AutoMirrored.Rounded.VolumeUp, "$label ${(volumeLevel * 100).roundToInt()}%", volumeLevel / ceiling, sticky = true)
                }
            }

            override fun onVerticalEnd() {
                if (brightness >= 0) viewModel.rememberBrightness(brightness)
                brightness = -1f
                volumeLevel = -1f
                feedback = feedback?.copy(sticky = false)
            }

            override fun onZoom(scaleFactor: Float) {
                val zoom = (latestState.zoom * scaleFactor).coerceIn(PlayerViewModel.MIN_ZOOM, PlayerViewModel.MAX_ZOOM)
                viewModel.setZoom(zoom)
                feedback = Feedback(Icons.Rounded.ZoomIn, "Zoom ${(zoom * 100).roundToInt()}%", sticky = true)
            }

            override fun onZoomEnd() {
                viewModel.commitZoom()
                feedback = feedback?.copy(sticky = false)
            }

            override fun onLongPressStart() {
                val p = player ?: return
                // Guard: a second start without an end must never overwrite the real speed with 2x.
                if (longPressActive) return
                longPressActive = true
                controllerVisible = false
                speedBeforeLongPress = p.playbackParameters.speed
                longPressBaseIndex = nearestSpeedIndex(latestSettings.longPressSpeed)
                longPressIndex = longPressBaseIndex
                applyLongPressSpeed(p)
            }

            override fun onLongPressDrag(fractionOfWidth: Float) {
                val p = player ?: return
                if (!longPressActive) return
                // Every ~7% of the screen width is one 0.25x step: left = slower, right = faster.
                val index = (longPressBaseIndex + (fractionOfWidth / LONG_PRESS_STEP_WIDTH).roundToInt())
                    .coerceIn(0, LONG_PRESS_SPEEDS.lastIndex)
                if (index != longPressIndex) {
                    longPressIndex = index
                    applyLongPressSpeed(p)
                    playerView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }

            override fun onLongPressEnd() {
                if (!longPressActive) return
                longPressActive = false
                player?.setPlaybackSpeed(speedBeforeLongPress)
                // Remember the speed the user ended on for the next long press.
                viewModel.setLongPressSpeed(LONG_PRESS_SPEEDS[longPressIndex])
                feedback = feedback?.copy(sticky = false)
            }

            private fun applyLongPressSpeed(p: Player) {
                val speed = LONG_PRESS_SPEEDS[longPressIndex]
                p.setPlaybackSpeed(speed)
                val slower = LONG_PRESS_SPEEDS.getOrNull(longPressIndex - 1)?.let { "◀ ${formatSpeed(it)}   " }.orEmpty()
                val faster = LONG_PRESS_SPEEDS.getOrNull(longPressIndex + 1)?.let { "   ${formatSpeed(it)} ▶" }.orEmpty()
                feedback = Feedback(
                    icon = Icons.Rounded.Speed,
                    text = "${formatSpeed(speed)}\n$slower$faster".trimEnd(),
                    sticky = true,
                )
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .then(if (settings.immersiveMode) Modifier else Modifier.systemBarsPadding()),
            factory = { ctx ->
                PlayerGestureLayout(ctx).apply {
                    val view = PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setShowSubtitleButton(true)
                        setKeepContentOnPlayerReset(true)
                        addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                            applyVideoTransform(v as PlayerView, latestState.aspectRatio, latestState.zoom)
                        }
                    }
                    addView(view)
                    playerView = view
                    listener = gestureListener
                    // Every touch belongs to the gesture layer; the controls overlay is Compose, above it.
                    isControllerVisible = { false }
                }
            },
            update = { layout ->
                val view = layout.getChildAt(0) as PlayerView
                if (view.player !== state.player) view.player = state.player
                // Our own Compose controls replace Media3's controller.
                view.useController = false
                // Keep embedded subtitles clear of the bottom panel while it is showing.
                view.subtitleView?.setBottomPaddingFraction(if (controllerVisible) 0.26f else 0.08f)
                view.resizeMode = when (state.aspectRatio) {
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                applySubtitleStyle(view, settings)
                layout.config = GestureConfig(
                    enabled = settings.gesturesEnabled && !inPip,
                    seek = settings.seekGesture,
                    volume = settings.volumeGesture,
                    brightness = settings.brightnessGesture,
                    doubleTapSeek = settings.doubleTapSeek,
                    doubleTapPlayPause = settings.doubleTapPlayPause,
                    pinchZoom = settings.pinchToZoom,
                    longPress = settings.longPressSpeedUp,
                    locked = state.locked,
                )
                // Read so a size change re-runs this block.
                videoSize.width
                applyVideoTransform(view, state.aspectRatio, state.zoom)
            },
        )

        // Subtitle files are drawn here (with the user's sync offset); embedded ones by PlayerView.
        if (externalCues.isNotEmpty() && !inPip) {
            ExternalSubtitleOverlay(
                cues = externalCues,
                settings = settings,
                raised = controllerVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        feedback?.let { FeedbackPill(it, Modifier.align(Alignment.Center)) }

        state.message?.let { message ->
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 64.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (!state.locked && !inPip) {
            PlayerControls(
                // Hidden while the resume question is up, so nothing underneath can be tapped by mistake.
                visible = controllerVisible && state.resumePrompt == null,
                state = state,
                isPlaying = isPlaying,
                speedLabel = formatSpeed(state.player?.playbackParameters?.speed ?: 1f),
                aspectLabel = state.aspectRatio.label,
                subtitlesOn = state.subtitles.active != null ||
                    tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected },
                actions = PlayerControlActions(
                    onBack = host::exit,
                    onLock = {
                        viewModel.setLocked(true)
                        controllerVisible = false
                        lockHint = true
                    },
                    onRotate = {
                        // Flip between landscape and portrait (and keep it until the next flip).
                        val landscapeNow = currentOrientation(context) == OrientationMode.LANDSCAPE
                        viewModel.setOrientationLocked(true)
                        host.setOrientationOverride(if (landscapeNow) OrientationMode.PORTRAIT else OrientationMode.LANDSCAPE)
                        interaction++
                    },
                    onPip = host::enterPip,
                    onMore = { showOptions = true },
                    onSpeed = {
                        // Quick cycle through the most used speeds; the full list is in the menu.
                        val current = state.player?.playbackParameters?.speed ?: 1f
                        val next = QUICK_SPEEDS.firstOrNull { it > current + 0.01f } ?: QUICK_SPEEDS.first()
                        viewModel.setSpeed(next)
                        interaction++
                    },
                    onSubtitles = { showOptions = true },
                    onAspect = {
                        val modes = AspectRatioMode.entries
                        viewModel.setAspectRatio(modes[(modes.indexOf(state.aspectRatio) + 1) % modes.size])
                        interaction++
                    },
                    onInteraction = { interaction++ },
                ),
            )
        }

        // Drawn after (above) the controls so taps on these cards never reach the seek bar.
        if (!inPip) {
            state.resumePrompt?.let { prompt ->
                ResumeCard(
                    prompt = prompt,
                    onContinue = { viewModel.answerResume(continueWatching = true) },
                    onStartOver = { viewModel.answerResume(continueWatching = false) },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.upNext?.let { upNext ->
                UpNextCard(
                    upNext = upNext,
                    onPlay = viewModel::playNext,
                    onCancel = viewModel::cancelUpNext,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
        if (state.locked && !inPip) {
            // Small unlock control in the top-left corner, out of the way of the picture.
            AnimatedVisibility(
                visible = lockHint,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            viewModel.setLocked(false)
                            lockHint = false
                            controllerVisible = true
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                            .glass(RoundedCornerShape(22.dp)),
                    ) {
                        Icon(Icons.Rounded.LockOpen, contentDescription = "Unlock controls", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tap to unlock",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }

    if (showOptions) {
        PlayerOptionsSheet(
            state = state,
            settings = settings,
            tracks = tracks,
            onDismiss = { showOptions = false },
            onAspect = viewModel::setAspectRatio,
            onEngine = {
                showOptions = false
                viewModel.switchEngine(it)
            },
            onOrientation = { mode ->
                viewModel.setOrientationLocked(mode != null)
                host.setOrientationOverride(mode)
            },
            onPip = {
                showOptions = false
                host.enterPip()
            },
            onInfo = {
                showOptions = false
                showInfo = true
            },
            subtitles = state.subtitles,
            onSpeed = viewModel::setSpeed,
            onSelectSubtitleFile = viewModel::selectSubtitleFile,
            onClearExternalSubtitle = viewModel::clearExternalSubtitle,
            onLoadSubtitleFile = { subtitlePicker.launch(arrayOf("*/*")) },
            onSubtitleDelay = viewModel::setSubtitleDelay,
        )
    }

    if (showInfo) {
        MediaInfoDialog(state = state, tracks = tracks, videoSize = videoSize, onDismiss = { showInfo = false })
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = host::exit,
            title = { Text("Can't play this file") },
            text = {
                Column {
                    Text(message)
                    if (!state.triedVlc) {
                        TextButton(onClick = { viewModel.switchEngine(Engine.VLC) }) { Text("Try the VLC engine") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::retry) { Text("Try again") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { showInfo = true }) { Text("File info") }
                    TextButton(onClick = { state.file?.let(host::openWithAnotherApp) }) { Text("Open with…") }
                }
            },
        )
    }
}


@Composable
private fun FeedbackPill(feedback: Feedback, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            .glass(RoundedCornerShape(24.dp), strong = true)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(feedback.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(6.dp))
        Text(feedback.text, color = Color.White, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        feedback.progress?.let { progress ->
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.width(140.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}

@Composable
private fun PlayerOptionsSheet(
    state: PlayerUiState,
    settings: AppSettings,
    tracks: Tracks,
    onDismiss: () -> Unit,
    onAspect: (AspectRatioMode) -> Unit,
    onEngine: (Engine) -> Unit,
    onOrientation: (OrientationMode?) -> Unit,
    onPip: () -> Unit,
    onInfo: () -> Unit,
    subtitles: SubtitleUiState,
    onSpeed: (Float) -> Unit,
    onSelectSubtitleFile: (Uri) -> Unit,
    onClearExternalSubtitle: () -> Unit,
    onLoadSubtitleFile: () -> Unit,
    onSubtitleDelay: (Long) -> Unit,
) {
    val player = state.player
    // Translucent and without a scrim, so the video stays visible while choosing options.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
        scrimColor = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (audioGroups.isNotEmpty() && player != null) {
                SheetTitle("Audio track")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    audioGroups.forEachIndexed { index, group ->
                        FilterChip(
                            selected = group.isSelected,
                            onClick = { player.selectTrack(group, C.TRACK_TYPE_AUDIO) },
                            label = { Text(trackLabel(group.getTrackFormat(0), index)) },
                        )
                    }
                }
            }

            val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            if (player != null) {
                SheetTitle("Subtitles")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val externalActive = subtitles.active != null
                    val textOff = !externalActive && (
                        C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes ||
                            textGroups.none { it.isSelected }
                        )
                    FilterChip(
                        selected = textOff,
                        onClick = {
                            onClearExternalSubtitle()
                            player.disableSubtitles()
                        },
                        label = { Text("Off") },
                    )
                    textGroups.forEachIndexed { index, group ->
                        FilterChip(
                            selected = !externalActive && group.isSelected,
                            onClick = {
                                onClearExternalSubtitle()
                                player.selectTrack(group, C.TRACK_TYPE_TEXT)
                            },
                            label = { Text(trackLabel(group.getTrackFormat(0), index)) },
                        )
                    }
                    subtitles.files.forEach { file ->
                        FilterChip(
                            selected = subtitles.active == file.uri,
                            onClick = { onSelectSubtitleFile(file.uri) },
                            label = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Rounded.Subtitles, null, Modifier.size(16.dp)) },
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = onLoadSubtitleFile,
                        label = { Text("Load file…") },
                        leadingIcon = { Icon(Icons.Rounded.FileOpen, null, Modifier.size(16.dp)) },
                    )
                }
                if (subtitles.loading) {
                    Text("Looking for subtitle files…", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                }

                // Sync: shift subtitles earlier (−) or later (+).
                val canDelay = subtitles.active != null || state.engine == Engine.VLC
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sync", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(48.dp))
                    TextButton(onClick = { onSubtitleDelay(subtitles.delayMs - 100) }, enabled = canDelay) { Text("−0.1s") }
                    Text(
                        text = formatDelay(subtitles.delayMs),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(72.dp),
                    )
                    TextButton(onClick = { onSubtitleDelay(subtitles.delayMs + 100) }, enabled = canDelay) { Text("+0.1s") }
                    if (subtitles.delayMs != 0L) {
                        TextButton(onClick = { onSubtitleDelay(0) }) { Text("Reset") }
                    }
                }
                Slider(
                    value = subtitles.delayMs.toFloat(),
                    onValueChange = { onSubtitleDelay((it / 100).roundToInt() * 100L) },
                    valueRange = -10_000f..10_000f,
                    enabled = canDelay,
                )
                if (!canDelay) {
                    Text(
                        "Sync works with subtitle files and with the VLC engine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            SheetTitle("Playback speed")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val current = player?.playbackParameters?.speed ?: 1f
                SPEEDS.forEach { speed ->
                    FilterChip(
                        selected = abs(current - speed) < 0.01f,
                        onClick = { onSpeed(speed) },
                        label = { Text(formatSpeed(speed)) },
                        leadingIcon = if (speed == 1f) ({ Icon(Icons.Rounded.Speed, null, Modifier.size(16.dp)) }) else null,
                    )
                }
            }

            SheetTitle("Aspect ratio")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectRatioMode.entries.forEach { mode ->
                    FilterChip(selected = state.aspectRatio == mode, onClick = { onAspect(mode) }, label = { Text(mode.label) })
                }
            }

            SheetTitle("Decoder")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Engine.entries.forEach { engine ->
                    FilterChip(
                        selected = state.engine == engine,
                        onClick = { onEngine(engine) },
                        label = { Text(engine.description) },
                        leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(16.dp)) },
                    )
                }
            }

            SheetTitle("Screen rotation")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !state.orientationLocked, onClick = { onOrientation(null) }, label = { Text("Default (${settings.orientation.label})") })
                FilterChip(selected = false, onClick = { onOrientation(OrientationMode.LANDSCAPE) }, label = { Text("Landscape") })
                FilterChip(selected = false, onClick = { onOrientation(OrientationMode.PORTRAIT) }, label = { Text("Portrait") })
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetAction("Picture-in-picture", onPip)
            SheetAction("Video information", onInfo)
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun SheetAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
}

@Composable
private fun MediaInfoDialog(state: PlayerUiState, tracks: Tracks, videoSize: VideoSize, onDismiss: () -> Unit) {
    val rows = buildList {
        state.file?.let { file ->
            add("File" to file.name)
            file.sizeBytes?.let { add("Size" to formatFileSize(it)) }
            file.mimeType?.let { add("Type" to it) }
            add("Location" to file.uri.toString())
        }
        state.player?.duration?.takeIf { it != C.TIME_UNSET }?.let { add("Duration" to formatDuration(it)) }
        if (videoSize.width > 0) add("Resolution" to "${videoSize.width} × ${videoSize.height}")
        add("Decoder" to state.engine.description)
        tracks.groups.filter { it.isSelected }.forEach { group ->
            val format = group.getTrackFormat(0)
            val kind = when (group.type) {
                C.TRACK_TYPE_VIDEO -> "Video"
                C.TRACK_TYPE_AUDIO -> "Audio"
                C.TRACK_TYPE_TEXT -> "Subtitle"
                else -> "Track"
            }
            add(kind to formatDescription(format))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Video information") },
        containerColor = Color.Black.copy(alpha = 0.7f),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun Player.selectTrack(group: Tracks.Group, type: Int) {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        .setTrackTypeDisabled(type, false)
        .build()
}

private fun Player.disableSubtitles() {
    trackSelectionParameters = trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()
}

private fun trackLabel(format: Format, index: Int): String {
    val language = format.language?.takeIf { it != C.LANGUAGE_UNDETERMINED && it.isNotBlank() }
        ?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
    val channels = when (format.channelCount) {
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> null
    }
    return listOfNotNull(format.label ?: language ?: "Track ${index + 1}", channels).joinToString(" · ")
}

private fun formatDescription(format: Format): String = listOfNotNull(
    codecName(format.sampleMimeType ?: format.codecs),
    format.label,
    format.language,
    if (format.width > 0) "${format.width}×${format.height}" else null,
    if (format.frameRate > 0) "${format.frameRate.roundToInt()} fps" else null,
    if (format.channelCount > 0) "${format.channelCount} ch" else null,
    if (format.sampleRate > 0) "${format.sampleRate} Hz" else null,
    if (format.bitrate > 0) "${format.bitrate / 1000} kbps" else null,
).joinToString(" · ")

private fun codecName(mime: String?): String? = when (mime) {
    null -> null
    "video/avc" -> "H.264"
    "video/hevc" -> "H.265 / HEVC"
    "video/av01" -> "AV1"
    "video/x-vnd.on2.vp9" -> "VP9"
    "video/x-vnd.on2.vp8" -> "VP8"
    "video/mp4v-es" -> "MPEG-4"
    "video/mpeg2" -> "MPEG-2"
    "audio/mp4a-latm" -> "AAC"
    "audio/mpeg" -> "MP3"
    "audio/ac3" -> "Dolby Digital (AC-3)"
    "audio/eac3" -> "Dolby Digital Plus"
    "audio/vnd.dts" -> "DTS"
    "audio/true-hd" -> "Dolby TrueHD"
    "audio/opus" -> "Opus"
    "audio/vorbis" -> "Vorbis"
    "audio/flac" -> "FLAC"
    "application/x-subrip" -> "SRT"
    "text/x-ssa" -> "SSA/ASS"
    "text/vtt" -> "WebVTT"
    else -> mime
}

private fun applySubtitleStyle(view: PlayerView, settings: AppSettings) {
    val subtitleView = view.subtitleView ?: return
    subtitleView.setApplyEmbeddedStyles(true)
    subtitleView.setApplyEmbeddedFontSizes(false)
    subtitleView.setStyle(
        CaptionStyleCompat(
            settings.subtitleColor.argb,
            if (settings.subtitleBackground) 0xA0000000.toInt() else android.graphics.Color.TRANSPARENT,
            android.graphics.Color.TRANSPARENT,
            if (settings.subtitleBackground) CaptionStyleCompat.EDGE_TYPE_NONE else CaptionStyleCompat.EDGE_TYPE_OUTLINE,
            android.graphics.Color.BLACK,
            if (settings.subtitleBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT,
        ),
    )
    subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, settings.subtitleSizeSp.toFloat())
}

/**
 * Fixed aspect ratios, "Original" (1:1 pixels) and pinch zoom are applied as a scale on the video
 * surface on top of PlayerView's FIT layout, so they work identically for both engines.
 */
private fun applyVideoTransform(view: PlayerView, mode: AspectRatioMode, zoom: Float) {
    val surface = view.videoSurfaceView ?: return
    val size = view.player?.videoSize
    val w = view.width.toFloat()
    val h = view.height.toFloat()
    var scaleX = 1f
    var scaleY = 1f
    if (size != null && size.width > 0 && size.height > 0 && w > 0 && h > 0) {
        val videoAspect = size.width * size.pixelWidthHeightRatio / size.height
        fun fit(aspect: Float): Pair<Float, Float> = if (w / h > aspect) h * aspect to h else w to w / aspect
        val (fitW, fitH) = fit(videoAspect)
        when (mode) {
            AspectRatioMode.RATIO_16_9, AspectRatioMode.RATIO_4_3 -> {
                val (targetW, targetH) = fit(if (mode == AspectRatioMode.RATIO_16_9) 16f / 9f else 4f / 3f)
                scaleX = targetW / fitW
                scaleY = targetH / fitH
            }
            AspectRatioMode.ORIGINAL -> {
                val pixelW = size.width * size.pixelWidthHeightRatio
                if (pixelW <= w && size.height <= h) {
                    scaleX = pixelW / fitW
                    scaleY = scaleX
                }
            }
            else -> Unit
        }
    }
    surface.scaleX = scaleX * zoom
    surface.scaleY = scaleY * zoom
}

private fun currentOrientation(context: Context): OrientationMode =
    if (context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
        OrientationMode.LANDSCAPE
    } else {
        OrientationMode.PORTRAIT
    }

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}×" else "${speed.toString().trimEnd('0')}×"


val AspectRatioMode.label: String
    get() = when (this) {
        AspectRatioMode.FIT -> "Fit"
        AspectRatioMode.FILL -> "Stretch"
        AspectRatioMode.CROP -> "Crop"
        AspectRatioMode.RATIO_16_9 -> "16:9"
        AspectRatioMode.RATIO_4_3 -> "4:3"
        AspectRatioMode.ORIGINAL -> "Original"
    }

val OrientationMode.label: String
    get() = when (this) {
        OrientationMode.FOLLOW_VIDEO -> "Follow video"
        OrientationMode.AUTO -> "Auto-rotate"
        OrientationMode.LANDSCAPE -> "Landscape"
        OrientationMode.PORTRAIT -> "Portrait"
        OrientationMode.SYSTEM -> "System setting"
    }

private val Engine.description: String
    get() = when (this) {
        Engine.EXO_HARDWARE -> "HW"
        Engine.EXO_HARDWARE_PLUS -> "HW+"
        Engine.EXO_SOFTWARE -> "SW"
        Engine.VLC -> "VLC"
    }

private fun formatDelay(delayMs: Long): String =
    if (delayMs == 0L) "0.0s" else String.format(Locale.getDefault(), "%+.1fs", delayMs / 1000f)

@Composable
private fun ExternalSubtitleOverlay(
    cues: List<Cue>,
    settings: AppSettings,
    raised: Boolean,
    modifier: Modifier = Modifier,
) {
    val text = cues.mapNotNull { it.text?.toString() }.joinToString("\n")
    if (text.isBlank()) return
    val style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = settings.subtitleSizeSp.sp,
        fontWeight = if (settings.subtitleBold) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center,
        lineHeight = (settings.subtitleSizeSp * 1.25f).sp,
    )
    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // Move up while the controls are showing so the seek bar never covers the text.
            .padding(start = 24.dp, end = 24.dp, bottom = if (raised) 96.dp else 28.dp)
            .then(
                if (settings.subtitleBackground) {
                    Modifier
                        .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        if (!settings.subtitleBackground) {
            // Outline for readability on bright scenes.
            Text(text, style = style.copy(color = Color.Black, drawStyle = Stroke(width = 6f, join = StrokeJoin.Round)))
        }
        Text(text, style = style.copy(color = Color(settings.subtitleColor.argb)))
    }
}

@Composable
private fun ResumeCard(
    prompt: ResumePrompt,
    onContinue: () -> Unit,
    onStartOver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp)
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(26.dp))
            .glass(RoundedCornerShape(26.dp), strong = true)
            // Swallow taps between the buttons so they never reach the video underneath.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Continue watching from ${formatDuration(prompt.positionMs)}?", color = Color.White, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("Start over", onClick = onStartOver)
            GlowButton("Continue (${prompt.secondsLeft})", onClick = onContinue, icon = Icons.Rounded.PlayArrow)
        }
    }
}

@Composable
private fun UpNextCard(
    upNext: UpNext,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(end = 24.dp, bottom = 96.dp)
            .width(300.dp)
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(26.dp))
            .glass(RoundedCornerShape(26.dp), strong = true)
            .padding(16.dp),
    ) {
        Text(
            text = upNext.secondsLeft?.let { "Up next in $it" } ?: "Up next",
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(upNext.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("Cancel", onClick = onCancel)
            GlowButton("Play now", onClick = onPlay, icon = Icons.Rounded.SkipNext)
        }
    }
}

/**
 * Seeks to exactly [positionMs]. "Fast seeking" snaps to the nearest keyframe, which is fine for
 * scrubbing but makes short jumps (+10 s) land back where they started in videos with few
 * keyframes, so taps and drag releases always seek precisely.
 */
private fun Player.seekPrecisely(positionMs: Long) {
    val exo = this as? androidx.media3.exoplayer.ExoPlayer
    if (exo == null) {
        seekTo(positionMs)
        return
    }
    // Applied in order on the playback thread: exact for this seek, then the user's setting again.
    val previous = exo.seekParameters
    exo.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
    exo.seekTo(positionMs)
    exo.setSeekParameters(previous)
}
