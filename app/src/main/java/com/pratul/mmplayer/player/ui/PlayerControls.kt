package com.pratul.mmplayer.player.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenLockRotation
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.pratul.mmplayer.player.PlayerUiState
import com.pratul.mmplayer.utils.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Actions the control layer can trigger; implemented by [PlayerScreen]. */
class PlayerControlActions(
    val onBack: () -> Unit,
    val onLock: () -> Unit,
    val onRotate: () -> Unit,
    val onPip: () -> Unit,
    val onMore: () -> Unit,
    val onSpeed: () -> Unit,
    val onSubtitles: () -> Unit,
    val onAspect: () -> Unit,
    val onInteraction: () -> Unit,
)

/**
 * The player's own controls (Media3's stock controller is turned off), fully see-through: shadowed
 * white symbols and text over the video, a thin white seek bar and labelled actions, no panels.
 */
@Composable
fun PlayerControls(
    visible: Boolean,
    state: PlayerUiState,
    isPlaying: Boolean,
    speedLabel: String,
    aspectLabel: String,
    subtitlesOn: Boolean,
    actions: PlayerControlActions,
    thumbnails: SeekThumbnails? = null,
) {
    val player = state.player
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var showRemaining by remember { mutableStateOf(false) }

    // Poll position only while the controls are on screen.
    LaunchedEffect(visible, player) {
        while (visible && player != null) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            buffered = player.bufferedPosition
            delay(250)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Top: back, title, decoder badge, more.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleControl(Icons.AutoMirrored.Rounded.ArrowBack, "Back", actions.onBack, size = 44, iconSize = 24)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = state.title.substringBeforeLast('.'),
                    style = MaterialTheme.typography.titleSmall.onVideo(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = state.engine.badge,
                    style = MaterialTheme.typography.labelSmall.onVideo(),
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                        .semantics { contentDescription = "Decoder ${state.engine.badge}" },
                )
                Spacer(Modifier.width(2.dp))
                CircleControl(Icons.Rounded.MoreVert, "More options", actions.onMore, size = 44, iconSize = 24)
            }
        }

        // Centre transport.
        AnimatedVisibility(
            visible = visible && player != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.queueSize > 1) {
                    CircleControl(Icons.Rounded.SkipPrevious, "Previous", {
                        actions.onInteraction()
                        player?.seekToPreviousMediaItem()
                    }, enabled = state.queueIndex > 0)
                }
                CircleControl(Icons.Rounded.Replay10, "Rewind", {
                    actions.onInteraction()
                    player?.seekBack()
                })
                // Play / pause: just the symbol, larger than the others.
                CircleControl(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    label = if (isPlaying) "Pause" else "Play",
                    onClick = {
                        actions.onInteraction()
                        player?.let { it.playWhenReady = !it.playWhenReady }
                    },
                    size = 72,
                    iconSize = 56,
                )
                CircleControl(Icons.Rounded.Forward10, "Fast forward", {
                    actions.onInteraction()
                    player?.seekForward()
                })
                if (state.queueSize > 1) {
                    CircleControl(Icons.Rounded.SkipNext, "Next", {
                        actions.onInteraction()
                        player?.seekToNextMediaItem()
                    }, enabled = state.queueIndex < state.queueSize - 1)
                }
            }
        }

        // Bottom panel: times, seek bar, actions.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    // Taps around the bar must not fall through and hide the controls.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.onInteraction() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(position), style = MaterialTheme.typography.labelLarge.onVideo())
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (showRemaining && duration > 0) "−${formatDuration(duration - position)}" else formatDuration(duration),
                        style = MaterialTheme.typography.labelLarge.onVideo(),
                        modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Toggle remaining time") {
                            showRemaining = !showRemaining
                            actions.onInteraction()
                        },
                    )
                }
                Spacer(Modifier.height(2.dp))
                NeonSeekBar(
                    position = position,
                    duration = duration,
                    buffered = buffered,
                    thumbnails = thumbnails,
                    onScrub = { actions.onInteraction() },
                    onSeek = { target ->
                        player?.seekTo(target)
                        position = target
                        actions.onInteraction()
                    },
                )
                Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ActionChip(Icons.Rounded.Lock, "Lock", actions.onLock)
                    ActionChip(Icons.Rounded.Speed, speedLabel, actions.onSpeed, highlighted = speedLabel != "1×")
                    ActionChip(Icons.Rounded.ClosedCaption, if (subtitlesOn) "Subtitles on" else "Subtitles", actions.onSubtitles, highlighted = subtitlesOn)
                    ActionChip(Icons.Rounded.AspectRatio, aspectLabel, actions.onAspect)
                    ActionChip(
                        if (state.orientationLocked) Icons.Rounded.ScreenLockRotation else Icons.Rounded.ScreenRotation,
                        if (state.orientationLocked) "Rotation locked" else "Rotate",
                        actions.onRotate,
                        highlighted = state.orientationLocked,
                    )
                    ActionChip(Icons.Rounded.PictureInPictureAlt, "Pop-out", actions.onPip)
                }
            }
        }
    }
}

/** A see-through round button: a shadowed white symbol, with a full-size touch area around it. */
@Composable
private fun CircleControl(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Int = 48,
    enabled: Boolean = true,
    iconSize: Int = 30,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ShadowIcon(
            icon = icon,
            contentDescription = label,
            size = iconSize.dp,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
        )
    }
}

/** A see-through action: icon and word only. When on, it gets a thin white outline. */
@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit, highlighted: Boolean = false) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .then(if (highlighted) Modifier.border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(50)) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadowIcon(icon, contentDescription = null, size = 17.dp)
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium.onVideo(), maxLines = 1)
    }
}

/**
 * Thin white seek bar with a buffered track and a shadowed thumb. Dragging shows the
 * target time in a bubble and seeks when released; tapping jumps straight there.
 */
@Composable
private fun NeonSeekBar(
    position: Long,
    duration: Long,
    buffered: Long,
    onScrub: () -> Unit,
    onSeek: (Long) -> Unit,
    thumbnails: SeekThumbnails? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val fraction = if (dragging) dragFraction else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .semantics {
                contentDescription = "Seek bar"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { target ->
                    if (duration > 0) onSeek((target * duration).toLong())
                    true
                }
            }
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    if (duration > 0) onSeek(((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        if (duration > 0) onSeek((dragFraction * duration).toLong())
                    },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onScrub()
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(if (dragging) 5.dp else 3.dp),
        ) {
            val radius = CornerRadius(size.height / 2, size.height / 2)
            // A faint shadow line first, so the white bar shows on bright video too.
            drawRoundRect(Color.Black.copy(alpha = 0.35f), topLeft = Offset(0f, 1.5f), cornerRadius = radius)
            drawRoundRect(Color.White.copy(alpha = 0.30f), cornerRadius = radius)
            drawRoundRect(Color.White.copy(alpha = 0.50f), size = Size(size.width * bufferedFraction, size.height), cornerRadius = radius)
            drawRoundRect(Color.White, size = Size(size.width * fraction, size.height), cornerRadius = radius)
        }
        // Plain white thumb with a small shadow.
        val thumb = if (dragging) 18.dp else 13.dp
        Box(
            Modifier
                .offset { IntOffset((widthPx * fraction - with(density) { thumb.toPx() } / 2).roundToInt(), 0) }
                .size(thumb)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
        if (dragging && duration > 0 && thumbnails != null) {
            val frameW = 160.dp
            val frameWPx = with(density) { frameW.toPx() }
            val frameHPx = frameWPx * 9 / 16
            val x = (widthPx * dragFraction - frameWPx / 2).coerceIn(0f, (widthPx - frameWPx).coerceAtLeast(0f))
            SeekPreviewFrame(
                thumbnails = thumbnails,
                positionMs = (dragFraction * duration).toLong(),
                width = frameW,
                modifier = Modifier.offset { IntOffset(x.roundToInt(), (-96 - frameHPx - 16).roundToInt()) },
            )
        }
        if (dragging && duration > 0) {
            Text(
                text = formatDuration((dragFraction * duration).toLong()),
                style = MaterialTheme.typography.titleMedium.onVideo(),
                modifier = Modifier
                    .offset { IntOffset((widthPx * dragFraction).roundToInt() - 60, -96) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

