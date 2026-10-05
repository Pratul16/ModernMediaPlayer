package com.pratul.mmplayer.ui.folders

import com.pratul.mmplayer.ui.theme.auroraTopBarColors
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.pratul.mmplayer.data.settings.FolderSort
import com.pratul.mmplayer.ui.components.CreateFolderDialog
import androidx.compose.material.icons.rounded.CreateNewFolder
import android.widget.Toast
import com.pratul.mmplayer.data.settings.SettingKeys
import com.pratul.mmplayer.utils.sortedNatural
import kotlinx.coroutines.flow.map
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.data.model.MediaFolder
import com.pratul.mmplayer.data.repository.MediaRepository
import com.pratul.mmplayer.data.settings.AppSettings
import com.pratul.mmplayer.data.settings.SettingsRepository
import com.pratul.mmplayer.media.scanner.MediaScanner
import com.pratul.mmplayer.ui.components.EmptyState
import com.pratul.mmplayer.ui.components.FolderListItem
import com.pratul.mmplayer.ui.components.MediaActionsSheet
import com.pratul.mmplayer.ui.components.ClipboardBar
import com.pratul.mmplayer.ui.components.LocalSystemConsent
import com.pratul.mmplayer.ui.vault.HideFolderDialog
import com.pratul.mmplayer.ui.vault.vaultViewModel
import com.pratul.mmplayer.ui.components.FolderTarget
import com.pratul.mmplayer.ui.components.SelectionTopBar
import com.pratul.mmplayer.ui.components.click
import com.pratul.mmplayer.ui.components.rememberMediaSelection
import com.pratul.mmplayer.ui.components.MediaListItem
import com.pratul.mmplayer.ui.components.playMedia
import com.pratul.mmplayer.ui.library.label
import com.pratul.mmplayer.ui.library.sortedFor
import com.pratul.mmplayer.data.settings.SortOrder
import com.pratul.mmplayer.ui.modernMediaViewModel
import com.pratul.mmplayer.ui.permissions.rememberMediaAccessState
import com.pratul.mmplayer.utils.MediaAccess
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoldersViewModel(
    mediaRepository: MediaRepository,
    fileOperations: com.pratul.mmplayer.media.files.FileOperations,
    private val settingsRepository: SettingsRepository,
    private val scanner: MediaScanner,
) : ViewModel() {
    val sort: StateFlow<FolderSort> = settingsRepository.settings.map { it.folderSort }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderSort.NAME_ASC)

    val folders: StateFlow<List<MediaFolder>?> = combine(mediaRepository.observeFolders(), fileOperations.createdFolders, sort) { found, created, sort ->
        // Folders made in the app show up straight away, even before anything is moved into them.
        val list = found + created.filter { c -> found.none { it.relativePath == c.relativePath } }
            .map { MediaFolder("external_primary", it.relativePath, it.name, 0, 0, 0, 0L, System.currentTimeMillis() / 1000) }
        when (sort) {
            FolderSort.NAME_ASC -> list.sortedNatural { it.name }
            FolderSort.NAME_DESC -> list.sortedNatural(descending = true) { it.name }
            FolderSort.MOST_ITEMS -> list.sortedByDescending { it.itemCount }
            FolderSort.LARGEST -> list.sortedByDescending { it.totalSizeBytes }
            FolderSort.RECENT -> list.sortedByDescending { it.lastModifiedSec }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSort(value: FolderSort) = viewModelScope.launch { settingsRepository.set(SettingKeys.FOLDER_SORT, value) }
    val isScanning: StateFlow<Boolean> = scanner.isScanning

    fun rescan() = viewModelScope.launch { scanner.scan(force = true) }
}

/** Every folder on internal storage and SD cards that contains video or audio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    onOpenFolder: (MediaFolder) -> Unit,
    onOpenHidden: () -> Unit = {},
    viewModel: FoldersViewModel = modernMediaViewModel { FoldersViewModel(it.mediaRepository, it.fileOperations, it.settingsRepository, it.mediaScanner) },
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val access = rememberMediaAccessState(onAccessChanged = { if (it != MediaAccess.NONE) viewModel.rescan() })
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(

        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = auroraTopBarColors(),
                title = { Text("Folders") },
                actions = {
                    IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.CreateNewFolder, contentDescription = "New folder") }
                    IconButton(onClick = onOpenHidden) { Icon(Icons.Rounded.VisibilityOff, contentDescription = "Hidden folders") }
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort folders") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        FolderSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                trailingIcon = { if (option == sort) Icon(Icons.Rounded.Check, contentDescription = "Selected") },
                                onClick = {
                                    sortMenu = false
                                    viewModel.setSort(option)
                                },
                            )
                        }
                    }
                    IconButton(onClick = viewModel::rescan) { Icon(Icons.Rounded.Sync, contentDescription = "Rescan media") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            ClipboardBar(target = null)
            val list = folders
            when {
                access.access == MediaAccess.NONE -> EmptyState(
                    icon = Icons.Rounded.Folder,
                    title = "Allow access to your media",
                    message = "Modern Media Player needs permission to find the folders with videos and music on this phone.",
                    actionLabel = if (access.permanentlyDenied) "Open settings" else "Allow access",
                    onAction = { if (access.permanentlyDenied) access.openAppSettings() else access.request() },
                )
                list != null && list.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.Folder,
                    title = "No media folders found",
                    message = if (scanning) "Scanning your phone…" else "Folders that contain videos or audio will be listed here.",
                    actionLabel = "Scan again",
                    onAction = viewModel::rescan,
                )
                list != null -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(list, key = { "${it.volumeName}/${it.relativePath}" }) { folder ->
                        FolderListItem(folder = folder, onClick = { onOpenFolder(folder) })
                    }
                }
            }
        }
    }

    if (creating) {
        CreateFolderDialog(
            onDismiss = { creating = false },
            onCreated = { folder ->
                creating = false
                Toast.makeText(context, "Created ${folder.name}. Open it to paste, or use ⋮ on any file → Move to folder.", Toast.LENGTH_LONG).show()
            },
        )
    }
}

class FolderDetailViewModel(
    volume: String,
    path: String,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val items: StateFlow<Pair<List<MediaFile>, AppSettings>?> = combine(
        mediaRepository.observeInFolder(volume, path),
        settingsRepository.settings,
    ) { items, settings -> items.sortedFor(settings.sortOrder) to settings }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleFavorite(media: MediaFile) = viewModelScope.launch { mediaRepository.setFavorite(media.id, !media.isFavorite) }

    fun setSort(order: SortOrder) = viewModelScope.launch { settingsRepository.set(SettingKeys.SORT_ORDER, order) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    volume: String,
    path: String,
    name: String,
    onBack: () -> Unit,
    viewModel: FolderDetailViewModel = modernMediaViewModel(key = "$volume/$path") {
        FolderDetailViewModel(volume, path, it.mediaRepository, it.settingsRepository)
    },
) {
    val data by viewModel.items.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheetMedia by remember { mutableStateOf<MediaFile?>(null) }
    val selection = rememberMediaSelection()
    var hiding by remember { mutableStateOf(false) }
    val vault = vaultViewModel()
    val consent = LocalSystemConsent.current
    val lockOn = vault.appLock.config.collectAsStateWithLifecycle().value.enabled
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var sortMenu by remember { mutableStateOf(false) }

    Scaffold(

        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (selection.active) SelectionTopBar(selection, data?.first.orEmpty()) else
            TopAppBar(
                colors = auroraTopBarColors(),
                title = {
                    Column {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(path.ifBlank { "/" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { if (!data?.first.isNullOrEmpty()) hiding = true }) { Icon(Icons.Rounded.VisibilityOff, contentDescription = "Hide this folder") }
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort files") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOrder.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                trailingIcon = { if (option == data?.second?.sortOrder) Icon(Icons.Rounded.Check, contentDescription = "Selected") },
                                onClick = {
                                    sortMenu = false
                                    viewModel.setSort(option)
                                },
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val (items, settings) = data ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            ClipboardBar(target = FolderTarget(path, name, isNew = false))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(items, key = { it.id }) { media ->
                    MediaListItem(
                        media = media,
                        onClick = { selection.click(media) { context.playMedia(media) } },
                        onLongClick = { selection.toggle(media) },
                        showThumbnail = settings.showThumbnails,
                        showExtension = settings.showFileExtensions,
                        selected = if (selection.active) selection.isSelected(media) else null,
                        onMore = { sheetMedia = media },
                    )
                }
            }
        }
    }

    sheetMedia?.let { media ->
        MediaActionsSheet(
            items = listOf(media),
            onDismiss = { sheetMedia = null },
            onSelect = { selection.toggle(media) },
        )
    }
    if (hiding) {
        val items = data?.first.orEmpty()
        HideFolderDialog(
            name = name,
            count = items.size,
            size = items.sumOf { it.sizeBytes },
            lockOn = lockOn,
            onDismiss = { hiding = false },
            onConfirm = {
                hiding = false
                vault.hide(name, path, items, consent)
                onBack()
            },
        )
    }
}

private val FolderSort.label: String
    get() = when (this) {
        FolderSort.NAME_ASC -> "Name A–Z"
        FolderSort.NAME_DESC -> "Name Z–A"
        FolderSort.MOST_ITEMS -> "Most files"
        FolderSort.LARGEST -> "Largest"
        FolderSort.RECENT -> "Recently changed"
    }
