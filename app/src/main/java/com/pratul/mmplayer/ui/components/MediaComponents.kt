package com.pratul.mmplayer.ui.components

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pratul.mmplayer.ui.theme.Aurora
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.data.model.MediaFolder
import com.pratul.mmplayer.data.model.MediaType
import com.pratul.mmplayer.data.model.PlaylistSummary
import com.pratul.mmplayer.media.thumbnail.MediaThumbnail
import com.pratul.mmplayer.ui.theme.GradientIconBadge
import com.pratul.mmplayer.ui.theme.NeonProgress
import com.pratul.mmplayer.ui.theme.glass
import com.pratul.mmplayer.utils.formatDuration
import com.pratul.mmplayer.utils.formatFileSize
import com.pratul.mmplayer.utils.formatRelativeTime
import com.pratul.mmplayer.utils.resolutionLabel

/**
 * Thumbnail with a glowing gradient placeholder, a glass duration chip, a resolution tag and a
 * neon watch-progress line. Images are loaded lazily by Coil at the composable's size, so lists
 * of thousands of items stay cheap.
 */
@Composable
fun MediaThumbnailImage(
    media: MediaFile,
    modifier: Modifier = Modifier,
    showDuration: Boolean = true,
    showProgress: Boolean = true,
    cornerRadius: Dp = 18.dp,
    loadImage: Boolean = true,
    showQuality: Boolean = false,
) {
    val model = remember(media.uri, media.dateModifiedSec) {
        MediaThumbnail(Uri.parse(media.uri), media.dateModifiedSec)
    }
    val isVideo = media.type == MediaType.VIDEO
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    if (isVideo) listOf(Aurora.accent.copy(alpha = 0.55f), Aurora.accentEnd.copy(alpha = 0.35f))
                    else listOf(Aurora.highlight.copy(alpha = 0.50f), Aurora.accent.copy(alpha = 0.45f)),
                ),
            ),
    ) {
        Icon(
            imageVector = if (isVideo) Icons.Rounded.Movie else Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(28.dp),
        )
        if (loadImage) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Soft scrim so badges stay readable on bright frames.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f))),
        )
        if (showQuality) {
            resolutionLabel(media.width, media.height)?.let { label ->
                Badge(label, Modifier.align(Alignment.TopStart))
            }
        }
        if (showDuration && media.durationMs > 0) {
            Badge(formatDuration(media.durationMs), Modifier.align(Alignment.BottomEnd))
        }
        if (showProgress && media.progress > 0f) {
            NeonProgress(
                progress = media.progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = modifier
            .padding(7.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Full-width row used in libraries and on Home. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaListItem(
    media: MediaFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showThumbnail: Boolean = true,
    showExtension: Boolean = true,
    /** Null outside selection mode; otherwise whether this row is ticked. */
    selected: Boolean? = null,
    /** Shows a ⋮ button opening the actions menu. */
    onMore: (() -> Unit)? = null,
) {
    val isVideo = media.type == MediaType.VIDEO
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(22.dp))
            .then(if (selected == true) Modifier.background(Aurora.accent.copy(alpha = 0.16f)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = if (selected != null) "Select" else "More options")
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaThumbnailImage(
            media = media,
            loadImage = showThumbnail,
            showDuration = isVideo,
            showQuality = false,
            cornerRadius = if (isVideo) 16.dp else 14.dp,
            modifier = if (isVideo) Modifier.width(132.dp).aspectRatio(16f / 9f) else Modifier.size(58.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (isVideo) media.listName(showExtension) else media.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = media.metadataLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = media.folderName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (media.isFavorite) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "Favorite",
                tint = Aurora.highlight,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp),
            )
        }
        when {
            selected != null -> SelectionMark(selected, Modifier.padding(start = 10.dp, end = 6.dp))
            onMore != null -> IconButton(onClick = onMore) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${media.displayName}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
        contentDescription = if (selected) "Selected" else "Not selected",
        tint = if (selected) Aurora.accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = modifier.size(24.dp),
    )
}

/** Card used in horizontal shelves (Continue watching, New on your phone, ...). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCard(
    media: MediaFile,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    showThumbnail: Boolean = true,
    showExtension: Boolean = true,
    selected: Boolean? = null,
    onMore: (() -> Unit)? = null,
) {
    val isVideo = media.type == MediaType.VIDEO
    Column(
        modifier = modifier
            .width(width ?: if (isVideo) 216.dp else 150.dp)
            .clip(MaterialTheme.shapes.medium)
            .then(if (selected == true) Modifier.background(Aurora.accent.copy(alpha = 0.16f)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = if (selected != null) "Select" else "More options")
            .padding(4.dp),
    ) {
        Box {
            MediaThumbnailImage(
                media = media,
                loadImage = showThumbnail,
                showQuality = isVideo,
                cornerRadius = 20.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (isVideo) 16f / 9f else 1f),
            )
            if (selected != null) {
                SelectionMark(
                    selected,
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(50)),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isVideo) media.listName(showExtension) else media.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onMore != null && selected == null) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Options for ${media.displayName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(50))
                        .combinedClickable(onClick = onMore)
                        .padding(4.dp),
                )
            }
        }
        val secondLine = when {
            media.positionMs > 0 && media.durationMs > 0 ->
                "${formatDuration(media.durationMs - media.positionMs)} left"
            media.lastPlayedAt != null -> formatRelativeTime(media.lastPlayedAt)
            !isVideo && !media.artist.isNullOrBlank() -> media.artist
            else -> media.folderName
        }
        Text(
            text = secondLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun FolderListItem(
    folder: MediaFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .glass(RoundedCornerShape(22.dp))
            .combinedClickableCompat(onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientIconBadge(Icons.Rounded.Folder, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = folder.summaryLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun PlaylistCard(
    playlist: PlaylistSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(156.dp)
            .glass(MaterialTheme.shapes.medium)
            .combinedClickableCompat(onClick)
            .padding(14.dp)
            .semantics { contentDescription = "Playlist ${playlist.name}, ${playlist.itemCount} items" },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GradientIconBadge(Icons.AutoMirrored.Rounded.QueueMusic, size = 40.dp)
        Column {
            Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = "${playlist.itemCount} items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = combinedClickable(onClick = onClick)

private fun MediaFile.metadataLine(): String = buildList {
    if (durationMs > 0 && type == MediaType.AUDIO) add(formatDuration(durationMs))
    if (type == MediaType.AUDIO && !artist.isNullOrBlank()) add(artist)
    add(formatFileSize(sizeBytes))
    resolutionLabel(width, height)?.let(::add)
}.joinToString("  ·  ")

private fun MediaFolder.summaryLine(): String = if (itemCount == 0) "Empty folder" else buildList {
    if (videoCount > 0) add(if (videoCount == 1) "1 video" else "$videoCount videos")
    if (audioCount > 0) add(if (audioCount == 1) "1 track" else "$audioCount tracks")
    add(formatFileSize(totalSizeBytes))
}.joinToString("  ·  ")

private fun MediaFile.listName(showExtension: Boolean): String =
    if (showExtension) displayName else displayName.substringBeforeLast('.', displayName)

