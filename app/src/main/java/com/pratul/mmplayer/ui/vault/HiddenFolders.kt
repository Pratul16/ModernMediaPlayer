package com.pratul.mmplayer.ui.vault

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.data.model.MediaType
import com.pratul.mmplayer.media.files.FileOperations
import com.pratul.mmplayer.media.files.FileTask
import com.pratul.mmplayer.media.scanner.MediaScanner
import com.pratul.mmplayer.media.vault.HiddenFolder
import com.pratul.mmplayer.media.vault.HiddenItem
import com.pratul.mmplayer.media.vault.Vault
import com.pratul.mmplayer.player.PlayerActivity
import com.pratul.mmplayer.security.AppLock
import com.pratul.mmplayer.ui.components.EmptyState
import com.pratul.mmplayer.ui.components.LocalSystemConsent
import com.pratul.mmplayer.ui.components.SystemConsent
import com.pratul.mmplayer.ui.lock.rememberOwnerCheck
import com.pratul.mmplayer.ui.modernMediaViewModel
import com.pratul.mmplayer.ui.theme.GlowButton
import com.pratul.mmplayer.ui.theme.GradientIconBadge
import com.pratul.mmplayer.ui.theme.auroraTopBarColors
import com.pratul.mmplayer.ui.theme.glass
import com.pratul.mmplayer.utils.formatDuration
import com.pratul.mmplayer.utils.formatFileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class VaultViewModel(
    private val vault: Vault,
    private val files: FileOperations,
    private val scanner: MediaScanner,
    val appLock: AppLock,
    private val appScope: CoroutineScope,
    private val appContext: Context,
) : ViewModel() {

    val folders: StateFlow<List<HiddenFolder>> = vault.folders
    val task: StateFlow<FileTask?> = vault.task

    private fun toast(text: String) {
        appScope.launch(Dispatchers.Main) { Toast.makeText(appContext, text, Toast.LENGTH_LONG).show() }
    }

    /**
     * Hides a folder: copy into private storage, then remove the originals (Android asks the user
     * to allow that). If removing is refused, the copies are dropped and nothing changes.
     */
    fun hide(name: String, path: String, items: List<MediaFile>, consent: SystemConsent) {
        if (items.isEmpty()) return
        if (vault.spaceAfter(items) < 0) {
            toast("Not enough free space to hide this folder")
            return
        }
        appScope.launch {
            val pending = vault.copyIn(name, path, items)
            if (pending == null) {
                toast("Couldn't hide $name")
                return@launch
            }
            val uris = items.map { Uri.parse(it.uri) }
            val request = files.deleteRequest(uris)
            withContext(Dispatchers.Main) {
                if (request == null) {
                    // Android 10: remove directly.
                    appScope.launch {
                        val removed = files.delete(uris)
                        if (removed == 0) {
                            vault.rollback(pending)
                            toast("Couldn't hide $name")
                        } else {
                            vault.completeHide(pending)
                            scanner.scan(force = true)
                            toast("$name is hidden")
                        }
                    }
                } else {
                    consent.ask(
                        request,
                        onDenied = {
                            appScope.launch { vault.rollback(pending) }
                            toast("Not hidden: removing the visible copies wasn't allowed")
                        },
                    ) {
                        appScope.launch {
                            vault.completeHide(pending)
                            scanner.scan(force = true)
                            toast("$name is hidden. Find it in Folders → Hidden folders")
                        }
                    }
                }
            }
        }
    }

    fun unhide(folder: HiddenFolder) {
        appScope.launch {
            val restored = vault.unhide(folder)
            scanner.scan(force = true)
            toast(
                when (restored) {
                    folder.items.size -> "${folder.name} is visible again"
                    0 -> "Couldn't unhide ${folder.name}"
                    else -> "Restored $restored of ${folder.items.size} files"
                },
            )
        }
    }

    fun delete(folder: HiddenFolder) {
        appScope.launch {
            vault.delete(folder)
            toast("Deleted ${folder.name}")
        }
    }

    fun play(context: Context, folder: HiddenFolder, item: HiddenItem) {
        // A private content link: Android forbids passing file:// links between screens.
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.vault", vault.fileOf(folder, item))
        context.startActivity(PlayerActivity.intent(context, uri, item.mimeType))
    }
}

@Composable
fun vaultViewModel(): VaultViewModel = modernMediaViewModel(key = "vault") {
    VaultViewModel(it.vault, it.fileOperations, it.mediaScanner, it.appLock, it.appScope, it.appContext)
}

/** Confirmation shown before hiding a folder; explains where the files go. */
@Composable
fun HideFolderDialog(name: String, count: Int, size: Long, lockOn: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
        title = { Text("Hide “$name”?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$count files · ${formatFileSize(size)} move into this app's private storage.")
                Text("Gallery, file managers, other players and computers won't see or play them. You can watch them here under Folders → Hidden folders, and unhide any time.")
                Text(
                    "Uninstalling the app deletes hidden files, so unhide them first.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!lockOn) {
                    Text(
                        "Tip: turn on App lock in Settings → Privacy so only you can open them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Hide") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Lists hidden folders, after the owner confirms it's them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenFoldersScreen(onBack: () -> Unit, viewModel: VaultViewModel = vaultViewModel()) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val task by viewModel.task.collectAsStateWithLifecycle()
    val ownerCheck = rememberOwnerCheck(viewModel.appLock)
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<HiddenFolder?>(null) }
    val context = LocalContext.current
    val open = folders.firstOrNull { it.id == openId }

    fun unlock() = ownerCheck.confirm("Open hidden folders") { unlocked = true }
    LaunchedEffect(Unit) { if (!unlocked) unlock() }

    androidx.activity.compose.BackHandler(enabled = open != null) { openId = null }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopBarColors(),
                title = { Text(open?.name ?: "Hidden folders", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (open != null) openId = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            task?.let { t ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(t.label, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { t.fraction }, modifier = Modifier.fillMaxWidth())
                }
            }
            when {
                !unlocked -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GradientIconBadge(Icons.Rounded.Lock, size = 64.dp)
                        Spacer(Modifier.height(16.dp))
                        Text("Hidden folders are locked", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(20.dp))
                        GlowButton(text = "Unlock", icon = Icons.Rounded.Lock, onClick = ::unlock)
                    }
                }
                folders.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.VisibilityOff,
                    title = "Nothing hidden",
                    message = "Open any folder and tap the hide icon at the top. Its videos and music disappear from every other app and play only here.",
                )
                open != null -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
                    items(open.items, key = { it.fileName }) { item ->
                        HiddenItemRow(item) { viewModel.play(context, open, item) }
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.navigationBarsPadding()) {
                    item {
                        Text(
                            "Only visible inside Modern Media Player. Uninstalling the app deletes these files — unhide them first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                    items(folders, key = { it.id }) { folder ->
                        HiddenFolderRow(
                            folder = folder,
                            onOpen = { openId = folder.id },
                            onUnhide = { viewModel.unhide(folder) },
                            onDelete = { confirmDelete = folder },
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null) },
            title = { Text("Delete “${folder.name}” forever?") },
            text = { Text("${folder.items.size} hidden files will be permanently deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(folder)
                    confirmDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HiddenFolderRow(folder: HiddenFolder, onOpen: () -> Unit, onUnhide: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .glass(RoundedCornerShape(22.dp))
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientIconBadge(Icons.Rounded.VisibilityOff, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${folder.items.size} files · ${formatFileSize(folder.items.sumOf { it.sizeBytes })} · hidden ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(folder.hiddenAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${folder.name}") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Unhide") }, leadingIcon = { Icon(Icons.Rounded.Visibility, null) }, onClick = { menu = false; onUnhide() })
                DropdownMenuItem(text = { Text("Delete forever") }, leadingIcon = { Icon(Icons.Rounded.DeleteForever, null) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun HiddenItemRow(item: HiddenItem, onPlay: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .glass(RoundedCornerShape(20.dp))
            .clickable(onClick = onPlay)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientIconBadge(if (item.type == MediaType.VIDEO) Icons.Rounded.Movie else Icons.Rounded.MusicNote, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    item.durationMs.takeIf { it > 0 }?.let(::formatDuration),
                    formatFileSize(item.sizeBytes),
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
    }
}
