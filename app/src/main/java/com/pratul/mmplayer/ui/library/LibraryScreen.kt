package com.pratul.mmplayer.ui.library

import com.pratul.mmplayer.ui.theme.auroraTopBarColors
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.data.model.MediaType
import com.pratul.mmplayer.data.repository.MediaRepository
import com.pratul.mmplayer.data.settings.AppSettings
import com.pratul.mmplayer.data.settings.LibraryViewMode
import com.pratul.mmplayer.data.settings.SettingKeys
import com.pratul.mmplayer.data.settings.SettingsRepository
import com.pratul.mmplayer.data.settings.SortOrder
import com.pratul.mmplayer.media.scanner.MediaScanner
import com.pratul.mmplayer.ui.components.EmptyState
import com.pratul.mmplayer.utils.sortedNatural
import com.pratul.mmplayer.ui.components.MediaActionsSheet
import com.pratul.mmplayer.ui.components.SelectionTopBar
import com.pratul.mmplayer.ui.components.click
import com.pratul.mmplayer.ui.components.rememberMediaSelection
import com.pratul.mmplayer.ui.components.MediaCard
import com.pratul.mmplayer.ui.components.MediaListItem
import com.pratul.mmplayer.ui.components.playMedia
import com.pratul.mmplayer.ui.modernMediaViewModel
import com.pratul.mmplayer.ui.permissions.rememberMediaAccessState
import com.pratul.mmplayer.utils.MediaAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) { ALL("All"), UNWATCHED("Not played"), WATCHED("Played"), FAVORITES("Favorites") }

data class LibraryUiState(
    val isLoading: Boolean = true,
    val items: List<MediaFile> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val filter: LibraryFilter = LibraryFilter.ALL,
)

class LibraryViewModel(
    val type: MediaType,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
    private val scanner: MediaScanner,
) : ViewModel() {

    private val filter = MutableStateFlow(LibraryFilter.ALL)
    val isScanning: StateFlow<Boolean> = scanner.isScanning

    val state: StateFlow<LibraryUiState> = combine(
        mediaRepository.observeAll(type),
        settingsRepository.settings,
        filter,
    ) { all, settings, filter ->
        LibraryUiState(
            isLoading = false,
            items = all.filter(filter).sortedFor(settings.sortOrder),
            settings = settings,
            filter = filter,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setFilter(value: LibraryFilter) {
        filter.value = value
    }

    fun setSort(order: SortOrder) = viewModelScope.launch { settingsRepository.set(SettingKeys.SORT_ORDER, order) }

    fun toggleViewMode(current: LibraryViewMode) = viewModelScope.launch {
        settingsRepository.set(
            SettingKeys.VIEW_MODE,
            if (current == LibraryViewMode.LIST) LibraryViewMode.GRID else LibraryViewMode.LIST,
        )
    }

    fun toggleFavorite(media: MediaFile) = viewModelScope.launch { mediaRepository.setFavorite(media.id, !media.isFavorite) }

    fun rescan() = viewModelScope.launch { scanner.scan(force = true) }
}

private fun List<MediaFile>.filter(filter: LibraryFilter): List<MediaFile> = when (filter) {
    LibraryFilter.ALL -> this
    LibraryFilter.UNWATCHED -> filter { it.lastPlayedAt == null }
    LibraryFilter.WATCHED -> filter { it.lastPlayedAt != null }
    LibraryFilter.FAVORITES -> filter { it.isFavorite }
}

fun List<MediaFile>.sortedFor(order: SortOrder): List<MediaFile> = when (order) {
    // Natural order: "Class 2" before "Class 11", episodes by season and number.
    SortOrder.NAME_ASC -> sortedNatural { it.displayName }
    SortOrder.NAME_DESC -> sortedNatural(descending = true) { it.displayName }
    SortOrder.DATE_ADDED -> sortedByDescending { it.dateAddedSec }
    SortOrder.DATE_MODIFIED -> sortedByDescending { it.dateModifiedSec }
    SortOrder.SIZE -> sortedByDescending { it.sizeBytes }
    SortOrder.DURATION -> sortedByDescending { it.durationMs }
    SortOrder.RECENTLY_PLAYED -> sortedByDescending { it.lastPlayedAt ?: 0L }
}

val SortOrder.label: String
    get() = when (this) {
        SortOrder.NAME_ASC -> "Name A–Z"
        SortOrder.NAME_DESC -> "Name Z–A"
        SortOrder.DATE_ADDED -> "Date added"
        SortOrder.DATE_MODIFIED -> "Date modified"
        SortOrder.SIZE -> "Size"
        SortOrder.DURATION -> "Duration"
        SortOrder.RECENTLY_PLAYED -> "Recently played"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    type: MediaType,
    viewModel: LibraryViewModel = modernMediaViewModel(key = type.name) {
        LibraryViewModel(type, it.mediaRepository, it.settingsRepository, it.mediaScanner)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val access = rememberMediaAccessState(onAccessChanged = { if (it != MediaAccess.NONE) viewModel.rescan() })
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var sortMenu by remember { mutableStateOf(false) }
    var sheetMedia by remember { mutableStateOf<MediaFile?>(null) }
    val selection = rememberMediaSelection()
    val isVideo = type == MediaType.VIDEO
    val settings = state.settings

    Scaffold(

        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (selection.active) SelectionTopBar(selection, state.items) else
            TopAppBar(
                colors = auroraTopBarColors(),
                title = { Text(if (isVideo) "Videos" else "Audio") },
                actions = {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                trailingIcon = { if (order == settings.sortOrder) Icon(Icons.Rounded.Check, null) },
                                onClick = {
                                    sortMenu = false
                                    viewModel.setSort(order)
                                },
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.toggleViewMode(settings.viewMode) }) {
                        Icon(
                            imageVector = if (settings.viewMode == LibraryViewMode.LIST) Icons.Rounded.GridView else Icons.AutoMirrored.Rounded.ViewList,
                            contentDescription = if (settings.viewMode == LibraryViewMode.LIST) "Show as grid" else "Show as list",
                        )
                    }
                    IconButton(onClick = viewModel::rescan) {
                        Icon(Icons.Rounded.Sync, contentDescription = "Rescan media")
                    }
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
            if (isVideo) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(LibraryFilter.entries) { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter.label) },
                        )
                    }
                }
            }
            when {
                access.access == MediaAccess.NONE -> EmptyState(
                    icon = if (isVideo) Icons.Rounded.VideoLibrary else Icons.Rounded.LibraryMusic,
                    title = "Allow access to your media",
                    message = "Modern Media Player needs permission to find the ${if (isVideo) "videos" else "music"} on this phone.",
                    actionLabel = if (access.permanentlyDenied) "Open settings" else "Allow access",
                    onAction = { if (access.permanentlyDenied) access.openAppSettings() else access.request() },
                )
                !state.isLoading && state.items.isEmpty() -> EmptyState(
                    icon = if (isVideo) Icons.Rounded.VideoLibrary else Icons.Rounded.LibraryMusic,
                    title = if (state.filter == LibraryFilter.ALL) "Nothing found" else "No ${state.filter.label.lowercase()} items",
                    message = if (scanning) "Scanning your phone…" else "No ${if (isVideo) "videos" else "audio files"} were found on this phone.",
                    actionLabel = "Scan again",
                    onAction = viewModel::rescan,
                )
                settings.viewMode == LibraryViewMode.GRID -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (isVideo) 168.dp else 128.dp),
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { media ->
                        MediaCard(
                            media = media,
                            onClick = { selection.click(media) { context.playMedia(media) } },
                            onLongClick = { selection.toggle(media) },
                            selected = if (selection.active) selection.isSelected(media) else null,
                            onMore = { sheetMedia = media },
                            modifier = Modifier.fillMaxWidth(),
                            width = Dp.Unspecified,
                            showThumbnail = settings.showThumbnails,
                            showExtension = settings.showFileExtensions,
                        )
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item(key = "count") {
                        Text(
                            text = "${state.items.size} ${if (isVideo) "videos" else "audio files"}",
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(state.items, key = { it.id }) { media ->
                        MediaListItem(
                            media = media,
                            onClick = { selection.click(media) { context.playMedia(media) } },
                            onLongClick = { selection.toggle(media) },
                            selected = if (selection.active) selection.isSelected(media) else null,
                            onMore = { sheetMedia = media },
                            showThumbnail = settings.showThumbnails,
                            showExtension = settings.showFileExtensions,
                        )
                    }
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
}
