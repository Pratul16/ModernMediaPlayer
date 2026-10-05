package com.pratul.mmplayer.ui.components

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pratul.mmplayer.ModernMediaApp
import androidx.compose.ui.platform.LocalContext
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.data.model.MediaType
import com.pratul.mmplayer.data.model.PlaylistSummary
import com.pratul.mmplayer.data.repository.CreatePlaylistResult
import com.pratul.mmplayer.data.repository.MediaRepository
import com.pratul.mmplayer.data.repository.PlaylistRepository
import com.pratul.mmplayer.media.files.CreatedFolder
import com.pratul.mmplayer.media.files.FileClip
import com.pratul.mmplayer.media.files.FileOperations
import com.pratul.mmplayer.media.files.FileTask
import com.pratul.mmplayer.media.files.FolderBase
import com.pratul.mmplayer.media.scanner.MediaScanner
import com.pratul.mmplayer.ui.modernMediaViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A place a file can be moved or copied to. */
data class FolderTarget(val relativePath: String, val name: String, val isNew: Boolean)

/**
 * Every file action offered by the ⋮ menu and the selection bar, for one or many files.
 * Work runs in the app scope so it finishes (and reports back with a toast) even if the screen
 * that started it is closed.
 */
class FileActionsViewModel(
    val files: FileOperations,
    private val playlists: PlaylistRepository,
    private val media: MediaRepository,
    private val scanner: MediaScanner,
    private val appScope: CoroutineScope,
    private val appContext: Context,
) : ViewModel() {

    val playlistList: StateFlow<List<PlaylistSummary>> = playlists.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Existing media folders plus empty folders created in the app. */
    val folders: StateFlow<List<FolderTarget>> = combine(media.observeFolders(), files.createdFolders) { existing, created ->
        val known = existing.map { FolderTarget(it.relativePath, it.name, isNew = false) }
        val fresh = created.filter { c -> known.none { it.relativePath == c.relativePath } }.map { FolderTarget(it.relativePath, it.name, isNew = true) }
        (fresh + known).distinctBy { it.relativePath }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val clipboard: StateFlow<FileClip?> = files.clipboard
    val task: StateFlow<FileTask?> = files.task

    private fun toast(text: String) {
        appScope.launch(Dispatchers.Main) { Toast.makeText(appContext, text, Toast.LENGTH_SHORT).show() }
    }

    private fun uris(items: List<MediaFile>) = items.map { Uri.parse(it.uri) }

    private fun count(n: Int) = if (n == 1) "1 file" else "$n files"

    /** Adds all to favorites, or removes them when every one is already a favorite. */
    fun toggleFavorite(items: List<MediaFile>) {
        val add = items.any { !it.isFavorite }
        appScope.launch {
            items.forEach { media.setFavorite(it.id, add) }
            toast(
                when {
                    items.size == 1 && add -> "Added to favorites"
                    items.size == 1 -> "Removed from favorites"
                    add -> "${count(items.size)} added to favorites"
                    else -> "${count(items.size)} removed from favorites"
                },
            )
        }
    }

    fun addToPlaylist(playlist: PlaylistSummary, items: List<MediaFile>) {
        appScope.launch {
            val added = playlists.add(playlist.id, items.map { it.id })
            toast(if (added > 0) "Added ${if (items.size == 1) "" else count(added) + " "}to ${playlist.name}" else "Already in ${playlist.name}")
        }
    }

    suspend fun createPlaylistWith(name: String, items: List<MediaFile>): CreatePlaylistResult {
        val result = playlists.create(name)
        if (result is CreatePlaylistResult.Created) {
            playlists.add(result.id, items.map { it.id })
            toast("Added to ${name.trim()}")
        }
        return result
    }

    suspend fun createFolder(base: FolderBase, name: String): Result<CreatedFolder> = files.createFolder(base, name)

    // Android 11+ consent dialogs; null means no dialog is needed.
    fun writeRequest(items: List<MediaFile>): IntentSender? = files.writeRequest(uris(items))
    fun deleteRequest(items: List<MediaFile>): IntentSender? = files.deleteRequest(uris(items))

    /** Call after the write consent was granted (or wasn't needed). */
    fun move(items: List<MediaFile>, target: FolderTarget) {
        appScope.launch {
            val moved = files.move(uris(items), target.relativePath)
            if (moved > 0) scanner.scan(force = true)
            toast(if (moved == items.size) "Moved ${count(moved)} to ${target.name}" else if (moved > 0) "Moved $moved of ${items.size}" else "Couldn't move ${if (items.size == 1) "this file" else "these files"}")
        }
    }

    fun copy(items: List<MediaFile>, target: FolderTarget) {
        appScope.launch {
            val copied = files.copy(items, target.relativePath)
            if (copied > 0) scanner.scan(force = true)
            toast(if (copied == items.size) "Copied ${count(copied)} to ${target.name}" else if (copied > 0) "Copied $copied of ${items.size}" else "Couldn't copy ${if (items.size == 1) "this file" else "these files"}")
        }
    }

    /**
     * Android 11+: call after the system delete dialog returned OK (it already deleted the files).
     * Android 10: deletes the files now.
     */
    fun afterDelete(items: List<MediaFile>) {
        appScope.launch {
            val removed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) items.size else files.delete(uris(items))
            // Anything still on the clipboard that was deleted can no longer be pasted.
            files.clipboard.value?.let { clip ->
                val left = clip.items.filter { c -> items.none { it.id == c.id } }
                files.setClip(if (left.isEmpty()) null else clip.copy(items = left))
            }
            scanner.scan(force = true)
            toast(if (removed > 0) "Deleted ${count(removed)}" else "Couldn't delete")
        }
    }

    /** [newBaseName] is the name without its extension; the extension is kept. */
    fun rename(item: MediaFile, newBaseName: String) {
        val ext = item.displayName.substringAfterLast('.', "")
        val newName = newBaseName.trim() + if (ext.isNotEmpty()) ".$ext" else ""
        if (newName == item.displayName) return
        appScope.launch {
            val ok = files.rename(Uri.parse(item.uri), newName)
            if (ok) scanner.scan(force = true)
            toast(if (ok) "Renamed to $newName" else "Couldn't rename (a file with that name may exist)")
        }
    }

    fun setClip(items: List<MediaFile>, cut: Boolean) {
        files.setClip(FileClip(items, cut))
        toast("${if (cut) "Cut" else "Copied"} ${count(items.size)} · open a folder and tap Paste")
    }

    fun clearClip() = files.setClip(null)

    /** Pastes the clipboard into [target]. For cut items, call after write consent. */
    fun paste(target: FolderTarget) {
        val clip = files.clipboard.value ?: return
        val usable = clip.items.filter { target.accepts(it.type) && (!clip.cut || it.folderPath != target.relativePath) }
        if (usable.isEmpty()) {
            toast(if (clip.cut) "These files are already here" else "This folder can't hold these files")
            return
        }
        if (clip.cut) {
            files.setClip(null)
            move(usable, target)
        } else {
            copy(usable, target)
        }
    }
}

@Composable
fun fileActionsViewModel(): FileActionsViewModel = modernMediaViewModel(key = "file-actions") {
    FileActionsViewModel(it.fileOperations, it.playlistRepository, it.mediaRepository, it.mediaScanner, it.appScope, it.appContext)
}

/**
 * Runs an action after Android's "Allow Modern Media Player to modify / delete …?" dialog.
 * It lives at the app root so the answer still arrives after the menu that asked has closed.
 */
class SystemConsent internal constructor(private val launch: (IntentSender, () -> Unit, () -> Unit) -> Unit) {
    /** Runs [onGranted] right away when [request] is null (no dialog needed). */
    fun ask(request: IntentSender?, onDenied: () -> Unit = {}, onGranted: () -> Unit) {
        if (request == null) onGranted() else launch(request, onGranted, onDenied)
    }
}

@Composable
fun rememberSystemConsent(): SystemConsent {
    val appLock = (LocalContext.current.applicationContext as ModernMediaApp).container.appLock
    val pending = remember { arrayOfNulls<Pair<() -> Unit, () -> Unit>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val (granted, denied) = pending[0] ?: return@rememberLauncherForActivityResult
        pending[0] = null
        if (result.resultCode == Activity.RESULT_OK) granted() else denied()
    }
    return remember(launcher) {
        SystemConsent { sender, granted, denied ->
            appLock.expectExternalReturn()
            pending[0] = granted to denied
            runCatching { launcher.launch(IntentSenderRequest.Builder(sender).build()) }.onFailure {
                pending[0] = null
                denied()
            }
        }
    }
}

val LocalSystemConsent = staticCompositionLocalOf<SystemConsent> { error("No SystemConsent provided") }

/**
 * Android only allows each kind of media in certain shared folders (videos in Movies, DCIM,
 * Pictures, Download; audio in Music, Download, Podcasts, ...). Folders outside them can't be targets.
 */
fun FolderTarget.accepts(type: MediaType): Boolean {
    val top = relativePath.substringBefore('/')
    return when (type) {
        MediaType.VIDEO -> top in setOf("Movies", "DCIM", "Pictures", "Download")
        MediaType.AUDIO -> top in setOf("Music", "Download", "Podcasts", "Audiobooks", "Recordings", "Ringtones", "Alarms", "Notifications")
    }
}

/** "Create New Folder" with a location choice; validates the name as it is typed. */
@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onCreated: (CreatedFolder) -> Unit,
    allowedFor: Set<MediaType> = emptySet(),
    viewModel: FileActionsViewModel = fileActionsViewModel(),
) {
    val bases = FolderBase.entries.filter { base ->
        (MediaType.VIDEO !in allowedFor || base.allowsVideo) && (MediaType.AUDIO !in allowedFor || base.allowsAudio)
    }
    var base by remember { mutableStateOf(bases.first()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    NameDialog(
        title = "Create New Folder",
        label = "Folder name",
        confirmLabel = "Create",
        error = error,
        validate = { viewModel.files.validateFolderName(it, base) },
        onDismiss = onDismiss,
        onConfirm = { name ->
            scope.launch {
                viewModel.createFolder(base, name)
                    .onSuccess(onCreated)
                    .onFailure { error = it.message }
            }
        },
    ) {
        Text("Create in", modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            bases.forEach { option ->
                FilterChip(
                    selected = option == base,
                    onClick = {
                        base = option
                        error = null
                    },
                    label = { Text(option.label) },
                )
            }
        }
    }
}
