package com.pratul.mmplayer.player.ui

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
import androidx.compose.ui.graphics.Brush
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
import com.pratul.mmplayer.ui.theme.Aurora
import com.pratul.mmplayer.ui.theme.glass
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
 * The player's own controls (Media3's stock controller is turned off): a glass top bar, a glowing
 * centre transport, and a frosted bottom panel with a neon seek bar and labelled action chips.
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
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleControl(Icons.AutoMirrored.Rounded.ArrowBack, "Back", actions.onBack, size = 34)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = state.title.substringBeforeLast('.'),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = state.engine.badge,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Aurora.gradient)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .semantics { contentDescription = "Decoder ${state.engine.badge}" },
                )
                Spacer(Modifier.width(8.dp))
                CircleControl(Icons.Rounded.MoreVert, "More options", actions.onMore, size = 34)
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
                horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                // Glowing play / pause.
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .shadow(18.dp, CircleShape, ambientColor = Aurora.accent, spotColor = Aurora.accentEnd)
                        .clip(CircleShape)
                        .background(Aurora.gradient)
                        .clickable(role = Role.Button, onClickLabel = if (isPlaying) "Pause" else "Play") {
                            actions.onInteraction()
                            player?.let { it.playWhenReady = !it.playWhenReady }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
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
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .widthIn(max = 720.dp)
                    .glass(RoundedCornerShape(20.dp))
                    // Taps on the panel's background must not fall through and hide the controls.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { actions.onInteraction() }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (showRemaining && duration > 0) "−${formatDuration(duration - position)}" else formatDuration(duration),
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium,
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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

@Composable
private fun CircleControl(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Int = 40,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .glass(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit, highlighted: Boolean = false) {
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(50))
            .then(if (highlighted) Modifier.background(Aurora.gradient(0.85f)) else Modifier.background(Color.White.copy(alpha = 0.08f)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/**
 * Seek bar with a neon gradient fill, buffered track and a glowing thumb. Dragging shows the
 * target time in a bubble and seeks when released; tapping jumps straight there.
 */
@Composable
private fun NeonSeekBar(
    position: Long,
    duration: Long,
    buffered: Long,
    onScrub: () -> Unit,
    onSeek: (Long) -> Unit,
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
        val seekColors = Aurora.colors
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(if (dragging) 6.dp else 4.dp),
        ) {
            val radius = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = radius)
            drawRoundRect(Color.White.copy(alpha = 0.30f), size = Size(size.width * bufferedFraction, size.height), cornerRadius = radius)
            drawRoundRect(
                brush = Brush.horizontalGradient(seekColors),
                size = Size(size.width * fraction, size.height),
                cornerRadius = radius,
            )
        }
        // Thumb with a soft glow.
        val thumb = if (dragging) 18.dp else 12.dp
        Box(
            Modifier
                .offset { IntOffset((widthPx * fraction - with(density) { thumb.toPx() } / 2).roundToInt(), 0) }
                .size(thumb)
                .shadow(12.dp, CircleShape, ambientColor = Aurora.accentEnd, spotColor = Aurora.accentEnd)
                .clip(CircleShape)
                .background(Color.White),
        )
        if (dragging && duration > 0) {
            Text(
                text = formatDuration((dragFraction * duration).toLong()),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .offset { IntOffset((widthPx * dragFraction).roundToInt() - 60, -96) }
                    .clip(RoundedCornerShape(50))
                    .background(Aurora.gradient)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

