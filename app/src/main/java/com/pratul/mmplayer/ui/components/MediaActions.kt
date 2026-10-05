package com.pratul.mmplayer.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratul.mmplayer.ModernMediaApp
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.data.model.MediaType
import com.pratul.mmplayer.data.repository.CreatePlaylistResult
import com.pratul.mmplayer.player.PlayerActivity
import com.pratul.mmplayer.ui.theme.GradientIconBadge
import com.pratul.mmplayer.ui.theme.glass
import com.pratul.mmplayer.utils.formatDuration
import com.pratul.mmplayer.utils.formatFileSize
import com.pratul.mmplayer.utils.resolutionLabel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

fun Context.playMedia(media: MediaFile) {
    startActivity(PlayerActivity.intent(this, Uri.parse(media.uri), media.mimeType))
}

fun Context.shareMedia(media: MediaFile) = shareMedia(listOf(media))

/** Shares one or many files through the system share sheet. */
fun Context.shareMedia(items: List<MediaFile>) {
    if (items.isEmpty()) return
    val uris = items.map { Uri.parse(it.uri) }
    val type = when {
        items.all { it.type == MediaType.VIDEO } -> if (items.size == 1) items.first().mimeType ?: "video/*" else "video/*"
        items.all { it.type == MediaType.AUDIO } -> if (items.size == 1) items.first().mimeType ?: "audio/*" else "audio/*"
        else -> "*/*"
    }
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }
    send.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // ClipData carries the read grant to every file, not just the first.
    send.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    val title = if (items.size == 1) "Share ${items.first().displayName}" else "Share ${items.size} files"
    (applicationContext as ModernMediaApp).container.appLock.expectExternalReturn()
    startActivity(Intent.createChooser(send, title))
}

enum class MediaSheetPage { MAIN, PLAYLISTS, MOVE, COPY }

private enum class SheetDialog { NEW_PLAYLIST, NEW_FOLDER, RENAME, DELETE, INFO }

/**
 * The ⋮ menu for one file or a selection: play, select, favorite, add to playlist, share, rename,
 * copy, cut, copy to / move to a folder, delete and properties. Everything works for many files
 * at once except Play and Rename. [onDone] runs after an action is carried out (e.g. to leave
 * selection mode); [onDismiss] whenever the menu closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaActionsSheet(
    items: List<MediaFile>,
    onDismiss: () -> Unit,
    onSelect: (() -> Unit)? = null,
    onDone: () -> Unit = {},
    startPage: MediaSheetPage = MediaSheetPage.MAIN,
) {
    if (items.isEmpty()) return
    val actions = fileActionsViewModel()
    val consent = LocalSystemConsent.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(startPage) }
    var dialog by remember { mutableStateOf<SheetDialog?>(null) }
    var playlistError by remember { mutableStateOf<String?>(null) }
    val playlists by actions.playlistList.collectAsStateWithLifecycle()
    val folders by actions.folders.collectAsStateWithLifecycle()
    val single = items.singleOrNull()
    val types = items.map { it.type }.toSet()
    val allFavorite = items.all { it.isFavorite }

    fun finish() {
        onDone()
        onDismiss()
    }

    fun moveTo(target: FolderTarget) {
        consent.ask(actions.writeRequest(items)) { actions.move(items, target) }
        finish()
    }

    fun copyTo(target: FolderTarget) {
        actions.copy(items, target)
        finish()
    }

    fun delete() {
        val request = actions.deleteRequest(items)
        if (request == null) {
            dialog = SheetDialog.DELETE // Android 10: ask ourselves.
        } else {
            consent.ask(request) { actions.afterDelete(items) }
            finish()
        }
    }

    when (dialog) {
        null -> ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(Modifier.navigationBarsPadding()) {
                SheetHeader(
                    title = when (page) {
                        MediaSheetPage.MAIN -> single?.displayName ?: "${items.size} files selected"
                        MediaSheetPage.PLAYLISTS -> "Add to playlist"
                        MediaSheetPage.MOVE -> "Move to folder"
                        MediaSheetPage.COPY -> "Copy to folder"
                    },
                    subtitle = if (page == MediaSheetPage.MAIN) summaryOf(items) else null,
                    onBack = if (page != MediaSheetPage.MAIN && startPage == MediaSheetPage.MAIN) ({ page = MediaSheetPage.MAIN }) else null,
                )
                Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                    when (page) {
                        MediaSheetPage.MAIN -> {
                            // Most-used actions as big tiles, the rest as a list.
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                val tile = Modifier.weight(1f)
                                if (single != null) {
                                    QuickTile(Icons.Rounded.PlayArrow, "Play", tile) {
                                        context.playMedia(single)
                                        onDismiss()
                                    }
                                } else {
                                    QuickTile(Icons.Rounded.PlayArrow, "Play all", tile) {
                                        context.playQueue(items)
                                        finish()
                                    }
                                }
                                QuickTile(if (allFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (allFavorite) "Unfavorite" else "Favorite", tile) {
                                    actions.toggleFavorite(items)
                                    finish()
                                }
                                QuickTile(Icons.Rounded.Share, "Share", tile) {
                                    context.shareMedia(items)
                                    finish()
                                }
                                if (onSelect != null) {
                                    QuickTile(Icons.Rounded.CheckCircleOutline, "Select", tile) {
                                        onSelect()
                                        onDismiss()
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            SheetRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") { page = MediaSheetPage.PLAYLISTS }
                            if (single != null) SheetRow(Icons.Rounded.DriveFileRenameOutline, "Rename") { dialog = SheetDialog.RENAME }
                            SheetRow(Icons.Rounded.ContentCopy, "Copy", "Then paste it into any folder") {
                                actions.setClip(items, cut = false)
                                finish()
                            }
                            SheetRow(Icons.Rounded.ContentCut, "Cut", "Then paste it into any folder") {
                                actions.setClip(items, cut = true)
                                finish()
                            }
                            SheetRow(Icons.Rounded.FileCopy, "Copy to folder…") { page = MediaSheetPage.COPY }
                            SheetRow(Icons.AutoMirrored.Rounded.DriveFileMove, "Move to folder…") { page = MediaSheetPage.MOVE }
                            SheetRow(Icons.Rounded.Info, "Properties") { dialog = SheetDialog.INFO }
                            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            SheetRow(Icons.Rounded.DeleteOutline, if (single != null) "Delete" else "Delete ${items.size} files", danger = true) { delete() }
                        }
                        MediaSheetPage.PLAYLISTS -> {
                            SheetRow(Icons.Rounded.Add, "New playlist…") {
                                playlistError = null
                                dialog = SheetDialog.NEW_PLAYLIST
                            }
                            playlists.forEach { playlist ->
                                SheetRow(Icons.AutoMirrored.Rounded.QueueMusic, playlist.name, "${playlist.itemCount} items") {
                                    actions.addToPlaylist(playlist, items)
                                    finish()
                                }
                            }
                        }
                        MediaSheetPage.MOVE, MediaSheetPage.COPY -> {
                            val move = page == MediaSheetPage.MOVE
                            SheetRow(Icons.Rounded.CreateNewFolder, "New folder…") { dialog = SheetDialog.NEW_FOLDER }
                            val targets = folders.filter { f ->
                                types.all { f.accepts(it) } && !(move && items.all { it.folderPath == f.relativePath })
                            }
                            if (targets.isEmpty()) {
                                Text(
                                    "No suitable folders yet. Create one above.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                )
                            }
                            targets.forEach { folder ->
                                SheetRow(Icons.Rounded.Folder, folder.name, folder.relativePath.trimEnd('/') + if (folder.isNew) "  ·  new" else "") {
                                    if (move) moveTo(folder) else copyTo(folder)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        SheetDialog.NEW_PLAYLIST -> NameDialog(
            title = "New playlist",
            label = "Playlist name",
            confirmLabel = "Create & add",
            error = playlistError,
            onDismiss = onDismiss,
            onConfirm = { name ->
                scope.launch {
                    when (actions.createPlaylistWith(name, items)) {
                        is CreatePlaylistResult.Created -> finish()
                        CreatePlaylistResult.Duplicate -> playlistError = "A playlist with this name already exists"
                        CreatePlaylistResult.EmptyName -> playlistError = "Enter a name"
                    }
                }
            },
        )
        SheetDialog.NEW_FOLDER -> CreateFolderDialog(
            allowedFor = types,
            viewModel = actions,
            onDismiss = onDismiss,
            onCreated = { folder ->
                val target = FolderTarget(folder.relativePath, folder.name, isNew = true)
                if (page == MediaSheetPage.COPY) copyTo(target) else moveTo(target)
            },
        )
        SheetDialog.RENAME -> single?.let { media ->
            val ext = media.displayName.substringAfterLast('.', "")
            NameDialog(
                title = "Rename",
                label = if (ext.isNotEmpty()) "Name (.$ext is kept)" else "Name",
                confirmLabel = "Rename",
                initial = media.displayName.substringBeforeLast('.', media.displayName),
                validate = actions.files::validateFileName,
                onDismiss = onDismiss,
                onConfirm = { name ->
                    consent.ask(actions.writeRequest(listOf(media))) { actions.rename(media, name) }
                    finish()
                },
            )
        }
        SheetDialog.DELETE -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
            title = { Text(if (single != null) "Delete this file?" else "Delete ${items.size} files?") },
            text = { Text((single?.displayName ?: summaryOf(items)) + "\n\nThis permanently removes it from your phone.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.afterDelete(items)
                    finish()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        SheetDialog.INFO -> MediaPropertiesDialog(items, onDismiss)
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String?, onBack: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) 8.dp else 24.dp, end = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun QuickTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .glass(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GradientIconBadge(icon, size = 38.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, supporting: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(label, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp)) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun summaryOf(items: List<MediaFile>): String = buildList {
    val videos = items.count { it.type == MediaType.VIDEO }
    val audio = items.size - videos
    if (items.size > 1) {
        if (videos > 0) add(if (videos == 1) "1 video" else "$videos videos")
        if (audio > 0) add(if (audio == 1) "1 track" else "$audio tracks")
    }
    add(formatFileSize(items.sumOf { it.sizeBytes }))
    val duration = items.sumOf { it.durationMs }
    if (duration > 0) add(formatDuration(duration))
}.joinToString("  ·  ")

/** Properties of one file, or totals for a selection. */
@Composable
fun MediaPropertiesDialog(items: List<MediaFile>, onDismiss: () -> Unit) {
    val single = items.singleOrNull()
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val rows = buildList {
        if (single != null) {
            val media = single
            add("Name" to media.displayName)
            add("Location" to (media.path?.substringBeforeLast('/') ?: media.folderPath.ifBlank { "/" }))
            add("Size" to "${formatFileSize(media.sizeBytes)}  (${"%,d".format(media.sizeBytes)} bytes)")
            if (media.durationMs > 0) add("Duration" to formatDuration(media.durationMs))
            resolutionLabel(media.width, media.height)?.let { add("Resolution" to "${media.width} × ${media.height}  ($it)") }
            media.artist?.let { add("Artist" to it) }
            media.album?.let { add("Album" to it) }
            add("Format" to (media.mimeType ?: media.displayName.substringAfterLast('.', "unknown").uppercase()))
            add("Modified" to date.format(Date(media.dateModifiedSec * 1000)))
            add("Added" to date.format(Date(media.dateAddedSec * 1000)))
            media.lastPlayedAt?.let { add("Last played" to date.format(Date(it))) }
            if (media.positionMs > 0 && media.durationMs > 0) add("Progress" to "${formatDuration(media.positionMs)} of ${formatDuration(media.durationMs)}")
        } else {
            val videos = items.count { it.type == MediaType.VIDEO }
            add("Selected" to "${items.size} files  ($videos video, ${items.size - videos} audio)")
            add("Total size" to formatFileSize(items.sumOf { it.sizeBytes }))
            val duration = items.sumOf { it.durationMs }
            if (duration > 0) add("Total duration" to formatDuration(duration))
            val folders = items.map { it.folderName }.distinct()
            add(if (folders.size == 1) "Folder" to folders.first() else "Folders" to "${folders.size} folders")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

/** Plays [items] as a queue starting at [start] (playlists, favorites, shuffle, selections). */
fun Context.playQueue(items: List<MediaFile>, start: Int = 0) {
    val first = items.getOrNull(start) ?: return
    startActivity(PlayerActivity.intent(this, Uri.parse(first.uri), first.mimeType, items.map { it.id }))
}

