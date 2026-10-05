package com.pratul.mmplayer.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.data.model.MediaType
import com.pratul.mmplayer.data.model.PlaylistSummary
import com.pratul.mmplayer.data.repository.CreatePlaylistResult
import com.pratul.mmplayer.data.repository.MediaRepository
import com.pratul.mmplayer.data.repository.PlaylistEntry
import com.pratul.mmplayer.data.repository.PlaylistRepository
import com.pratul.mmplayer.ui.components.EmptyState
import com.pratul.mmplayer.ui.components.MediaListItem
import com.pratul.mmplayer.ui.components.MediaActionsSheet
import com.pratul.mmplayer.ui.components.NameDialog
import com.pratul.mmplayer.ui.components.SectionHeader
import com.pratul.mmplayer.ui.components.playQueue
import com.pratul.mmplayer.ui.modernMediaViewModel
import com.pratul.mmplayer.ui.theme.GlassButton
import com.pratul.mmplayer.ui.theme.GlowButton
import com.pratul.mmplayer.ui.theme.GradientIconBadge
import com.pratul.mmplayer.ui.theme.auroraTopBarColors
import com.pratul.mmplayer.ui.theme.glass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Automatic collections shown next to user playlists. Negative ids so they never clash. */
object SmartPlaylist {
    const val FAVORITES = -1L
    const val RECENT = -2L
}

class PlaylistsViewModel(
    private val playlists: PlaylistRepository,
    media: MediaRepository,
) : ViewModel() {
    val list: StateFlow<List<PlaylistSummary>?> = playlists.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val favoriteCount: StateFlow<Int> = combine(
        media.observeFavorites(MediaType.VIDEO, Int.MAX_VALUE),
        media.observeFavorites(MediaType.AUDIO, Int.MAX_VALUE),
    ) { v, a -> v.size + a.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    suspend fun create(name: String) = playlists.create(name)
    suspend fun rename(id: Long, name: String) = playlists.rename(id, name)
    fun delete(id: Long) = viewModelScope.launch { playlists.delete(id) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onOpen: (id: Long, name: String) -> Unit,
    viewModel: PlaylistsViewModel = modernMediaViewModel { PlaylistsViewModel(it.playlistRepository, it.mediaRepository) },
) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteCount.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<PlaylistSummary?>(null) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<PlaylistSummary?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = auroraTopBarColors(),
                title = { Text("Playlists") },
                actions = {
                    IconButton(onClick = { creating = true }) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "New playlist") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "smart") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmartTile(Icons.Rounded.Favorite, "Favorites", "$favorites items", Modifier.weight(1f)) {
                        onOpen(SmartPlaylist.FAVORITES, "Favorites")
                    }
                    SmartTile(Icons.Rounded.History, "Recently played", "Last 100", Modifier.weight(1f)) {
                        onOpen(SmartPlaylist.RECENT, "Recently played")
                    }
                }
            }
            item(key = "header") {
                SectionHeader("Your playlists", actionLabel = "New", onAction = { creating = true }, modifier = Modifier.padding(horizontal = 0.dp))
            }
            val playlists = list
            if (playlists != null && playlists.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        title = "No playlists yet",
                        message = "Create one for workouts, movies, study music… then add files from any long-press menu.",
                        actionLabel = "Create playlist",
                        onAction = { creating = true },
                    )
                }
            }
            items(playlists.orEmpty(), key = { it.id }) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onOpen = { onOpen(playlist.id, playlist.name) },
                    onRename = {
                        renameError = null
                        renaming = playlist
                    },
                    onDelete = { deleting = playlist },
                )
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "New playlist",
            label = "Playlist name",
            confirmLabel = "Create",
            error = createError,
            onDismiss = {
                creating = false
                createError = null
            },
            onConfirm = { name ->
                scope.launch {
                    when (viewModel.create(name)) {
                        is CreatePlaylistResult.Created -> {
                            creating = false
                            createError = null
                        }
                        CreatePlaylistResult.Duplicate -> createError = "A playlist with this name already exists"
                        CreatePlaylistResult.EmptyName -> createError = "Enter a name"
                    }
                }
            },
        )
    }
    renaming?.let { playlist ->
        NameDialog(
            title = "Rename playlist",
            label = "Playlist name",
            confirmLabel = "Rename",
            initial = playlist.name,
            error = renameError,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                scope.launch {
                    if (name == playlist.name || viewModel.rename(playlist.id, name)) renaming = null
                    else renameError = "A playlist with this name already exists"
                }
            },
        )
    }
    deleting?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${playlist.name}\"?") },
            text = { Text("The playlist is removed. Your files stay on the phone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(playlist.id)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SmartTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .glass(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GradientIconBadge(icon, size = 40.dp)
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PlaylistRow(playlist: PlaylistSummary, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp))
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientIconBadge(Icons.AutoMirrored.Rounded.QueueMusic, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (playlist.itemCount == 1) "1 item" else "${playlist.itemCount} items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    onClick = {
                        menu = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --- One playlist ------------------------------------------------------------------------------

class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val playlists: PlaylistRepository,
    media: MediaRepository,
) : ViewModel() {
    val entries: StateFlow<List<PlaylistEntry>?> = when (playlistId) {
        SmartPlaylist.FAVORITES -> smart(media.observeFavorites(MediaType.VIDEO, SMART_LIMIT), media.observeFavorites(MediaType.AUDIO, SMART_LIMIT))
        SmartPlaylist.RECENT -> smart(media.observeRecentlyPlayed(MediaType.VIDEO, SMART_LIMIT), media.observeRecentlyPlayed(MediaType.AUDIO, SMART_LIMIT)) {
            sortedByDescending { it.lastPlayedAt ?: 0L }
        }
        else -> playlists.observeEntries(playlistId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val editable: Boolean get() = playlistId > 0

    private fun smart(
        videos: Flow<List<MediaFile>>,
        audio: Flow<List<MediaFile>>,
        order: List<MediaFile>.() -> List<MediaFile> = { this },
    ): Flow<List<PlaylistEntry>> = combine(videos, audio) { v, a -> (v + a).order().map { PlaylistEntry(-it.id, it) } }

    fun remove(entry: PlaylistEntry) = viewModelScope.launch { playlists.remove(playlistId, entry.itemId) }

    fun move(entry: PlaylistEntry, by: Int) = viewModelScope.launch {
        val current = entries.value ?: return@launch
        val from = current.indexOfFirst { it.itemId == entry.itemId }
        val to = (from + by).coerceIn(0, current.lastIndex)
        if (from < 0 || from == to) return@launch
        val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
        playlists.reorder(playlistId, reordered.map { it.itemId })
    }

    private companion object {
        const val SMART_LIMIT = 100
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    name: String,
    onBack: () -> Unit,
    viewModel: PlaylistDetailViewModel = modernMediaViewModel(key = "playlist-$playlistId") {
        PlaylistDetailViewModel(playlistId, it.playlistRepository, it.mediaRepository)
    },
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheetMedia by remember { mutableStateOf<MediaFile?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = auroraTopBarColors(),
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val list = entries ?: return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
        ) {
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        title = "Nothing here yet",
                        message = if (viewModel.editable) "Tap ⋮ on any video or song and choose \"Add to playlist\"." else "Items will appear here as you use the app.",
                    )
                }
                return@LazyColumn
            }
            item(key = "actions") {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlowButton("Play all", onClick = { context.playQueue(list.map { it.media }) }, icon = Icons.Rounded.PlayArrow)
                    GlassButton("Shuffle", onClick = { context.playQueue(list.map { it.media }.shuffled()) }, icon = Icons.Rounded.Shuffle)
                }
            }
            items(list, key = { it.itemId }) { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MediaListItem(
                        media = entry.media,
                        onClick = { context.playQueue(list.map { it.media }, start = list.indexOf(entry)) },
                        onLongClick = { sheetMedia = entry.media },
                        onMore = if (viewModel.editable) null else ({ sheetMedia = entry.media }),
                        modifier = Modifier.weight(1f),
                    )
                    if (viewModel.editable) EntryMenu(onUp = { viewModel.move(entry, -1) }, onDown = { viewModel.move(entry, 1) }, onRemove = { viewModel.remove(entry) }, onMore = { sheetMedia = entry.media })
                }
            }
        }
    }
    sheetMedia?.let { media ->
        MediaActionsSheet(items = listOf(media), onDismiss = { sheetMedia = null })
    }
}

@Composable
private fun EntryMenu(onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit, onMore: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Item options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null) }, onClick = { open = false; onUp() })
            DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null) }, onClick = { open = false; onDown() })
            DropdownMenuItem(text = { Text("Remove from playlist") }, leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) }, onClick = { open = false; onRemove() })
            DropdownMenuItem(text = { Text("File options…") }, leadingIcon = { Icon(Icons.Rounded.MoreVert, null) }, onClick = { open = false; onMore() })
        }
    }
}
