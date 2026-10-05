package com.pratul.mmplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratul.mmplayer.ModernMediaApp
import androidx.compose.ui.platform.LocalContext
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.data.model.MediaType
import com.pratul.mmplayer.ui.theme.auroraTopBarColors
import com.pratul.mmplayer.ui.theme.glass

/** Which files are ticked in a list. Selection mode is on while anything is ticked. */
@Stable
class MediaSelection {
    val ids = mutableStateSetOf<Long>()
    val active: Boolean get() = ids.isNotEmpty()

    fun toggle(media: MediaFile) {
        if (!ids.remove(media.id)) ids.add(media.id)
    }

    fun clear() = ids.clear()

    fun isSelected(media: MediaFile) = media.id in ids

    /** The ticked files, in list order; files that disappeared (deleted, moved) drop out. */
    fun selectedIn(all: List<MediaFile>): List<MediaFile> = all.filter { it.id in ids }
}

@Composable
fun rememberMediaSelection(): MediaSelection = remember { MediaSelection() }

/**
 * Click behaviour for a row: in selection mode a tap ticks the file; otherwise it plays.
 * A long press always starts or extends a selection.
 */
fun MediaSelection.click(media: MediaFile, play: () -> Unit) {
    if (active) toggle(media) else play()
}

/**
 * Replaces a screen's top bar while files are selected: count, select all, and the batch
 * actions (favorite, add to playlist, delete, and ⋮ for share, copy, cut, copy/move to, properties).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(selection: MediaSelection, all: List<MediaFile>) {
    val selected = selection.selectedIn(all)
    var sheet by remember { mutableStateOf<MediaSheetPage?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val actions = fileActionsViewModel()
    val consent = LocalSystemConsent.current
    BackHandler(enabled = selection.active) { selection.clear() }

    TopAppBar(
        colors = auroraTopBarColors(),
        navigationIcon = {
            IconButton(onClick = selection::clear) { Icon(Icons.Rounded.Close, contentDescription = "Cancel selection") }
        },
        title = { Text("${selected.size} selected") },
        actions = {
            val everything = selected.size == all.size
            IconButton(onClick = { if (everything) selection.clear() else all.forEach { selection.ids.add(it.id) } }) {
                Icon(if (everything) Icons.Rounded.Deselect else Icons.Rounded.SelectAll, contentDescription = if (everything) "Select none" else "Select all")
            }
            IconButton(onClick = {
                actions.toggleFavorite(selected)
                selection.clear()
            }) { Icon(Icons.Rounded.FavoriteBorder, contentDescription = "Favorite") }
            IconButton(onClick = { sheet = MediaSheetPage.PLAYLISTS }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add to playlist") }
            IconButton(onClick = {
                val request = actions.deleteRequest(selected)
                if (request == null) {
                    confirmDelete = true // Android 10: no system dialog, so ask here.
                } else {
                    val doomed = selected
                    consent.ask(request) { actions.afterDelete(doomed) }
                    selection.clear()
                }
            }) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete") }
            IconButton(onClick = { sheet = MediaSheetPage.MAIN }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More actions") }
        },
    )

    sheet?.let { page ->
        MediaActionsSheet(
            items = selected,
            startPage = page,
            onDismiss = { sheet = null },
            onDone = selection::clear,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${selected.size} files?") },
            text = { Text("This permanently removes them from your phone.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.afterDelete(selected)
                    confirmDelete = false
                    selection.clear()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Shown while copied or cut files wait to be pasted. [target] is the folder on screen (null when
 * the screen isn't a folder), which turns the bar into "Paste here".
 */
@Composable
fun ClipboardBar(target: FolderTarget?, modifier: Modifier = Modifier) {
    val actions = fileActionsViewModel()
    val consent = LocalSystemConsent.current
    val clip by actions.clipboard.collectAsStateWithLifecycle()
    AnimatedVisibility(clip != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(), modifier = modifier) {
        val current = clip ?: return@AnimatedVisibility
        val n = current.items.size
        val fits = target != null && current.items.all { target.accepts(it.type) }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .glass(RoundedCornerShape(20.dp), strong = true)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${if (current.cut) "Cut" else "Copied"} ${if (n == 1) current.items.first().displayName else "$n files"}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        target == null -> "Open a folder to paste"
                        !fits -> if (current.items.any { it.type == MediaType.VIDEO }) "Videos can't go in this folder" else "Audio can't go in this folder"
                        else -> "Paste into ${target.name}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            TextButton(onClick = actions::clearClip) { Text("Cancel") }
            if (target != null && fits) {
                TextButton(onClick = {
                    val clipNow = actions.clipboard.value ?: return@TextButton
                    if (clipNow.cut) {
                        consent.ask(actions.writeRequest(clipNow.items)) { actions.paste(target) }
                    } else {
                        actions.paste(target)
                    }
                }) { Text("Paste here") }
            }
        }
    }
}

/** Progress of a running copy, shown above the bottom navigation. */
@Composable
fun FileTaskBanner(modifier: Modifier = Modifier) {
    val container = (LocalContext.current.applicationContext as ModernMediaApp).container
    val copyTask by container.fileOperations.task.collectAsStateWithLifecycle()
    val vaultTask by container.vault.task.collectAsStateWithLifecycle()
    val task = copyTask ?: vaultTask
    AnimatedVisibility(task != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut(), modifier = modifier) {
        val current = task ?: return@AnimatedVisibility
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .glass(RoundedCornerShape(18.dp), strong = true)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(current.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text("${(current.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { current.fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}
