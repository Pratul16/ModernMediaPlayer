package com.pratul.mmplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.pratul.mmplayer.player.PlayerActivity
import com.pratul.mmplayer.player.service.NowPlaying
import com.pratul.mmplayer.ui.theme.GlassIconButton
import com.pratul.mmplayer.ui.theme.GradientIconBadge
import com.pratul.mmplayer.ui.theme.NeonProgress
import com.pratul.mmplayer.ui.theme.glass
import kotlinx.coroutines.delay

/**
 * "Now playing" bar shown above the navigation while something plays (including audio that kept
 * going after the player closed). Tap to reopen the full player on the same, still-running file.
 */
@Composable
fun MiniPlayer(nowPlaying: NowPlaying, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = nowPlaying.player
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var hasNext by remember(player) { mutableStateOf(player.hasNextMediaItem()) }
    var progress by remember(player) { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                hasNext = player.hasNextMediaItem()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            val duration = player.duration
            progress = if (duration != C.TIME_UNSET && duration > 0) player.currentPosition.toFloat() / duration else 0f
            delay(500)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .glass(RoundedCornerShape(24.dp), strong = true)
            .clickable(role = Role.Button, onClickLabel = "Open player") {
                context.startActivity(PlayerActivity.intent(context, nowPlaying.uri, nowPlaying.mimeType))
            },
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientIconBadge(if (nowPlaying.isAudio) Icons.Rounded.GraphicEq else Icons.Rounded.Movie, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = nowPlaying.title.substringBeforeLast('.'),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isPlaying) "Playing" else "Paused",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GlassIconButton(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                onClick = { player.playWhenReady = !player.playWhenReady },
                size = 42.dp,
                highlighted = true,
            )
            if (hasNext) {
                Spacer(Modifier.width(6.dp))
                GlassIconButton(Icons.Rounded.SkipNext, "Next", onClick = { player.seekToNextMediaItem() }, size = 42.dp)
            }
            Spacer(Modifier.width(6.dp))
            GlassIconButton(Icons.Rounded.Close, "Stop", onClick = onClose, size = 42.dp)
        }
        NeonProgress(progress, Modifier.padding(horizontal = 14.dp).padding(bottom = 8.dp), height = 2.dp)
    }
}

