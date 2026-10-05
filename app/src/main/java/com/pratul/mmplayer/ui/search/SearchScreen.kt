package com.pratul.mmplayer.ui.search

import com.pratul.mmplayer.ui.theme.auroraTopBarColors
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.data.model.MediaFolder
import com.pratul.mmplayer.data.model.MediaType
import com.pratul.mmplayer.data.model.PlaylistSummary
import com.pratul.mmplayer.data.repository.MediaRepository
import com.pratul.mmplayer.data.repository.PlaylistRepository
import com.pratul.mmplayer.ui.components.EmptyState
import com.pratul.mmplayer.ui.components.FolderListItem
import com.pratul.mmplayer.ui.components.MediaListItem
import com.pratul.mmplayer.ui.components.MediaActionsSheet
import com.pratul.mmplayer.ui.components.SectionHeader
import com.pratul.mmplayer.ui.components.playMedia
import com.pratul.mmplayer.ui.modernMediaViewModel
import com.pratul.mmplayer.utils.SmartSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SearchResults(
    val query: String = "",
    val videos: List<MediaFile> = emptyList(),
    val audio: List<MediaFile> = emptyList(),
    val folders: List<MediaFolder> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
) {
    val isEmpty get() = videos.isEmpty() && audio.isEmpty() && folders.isEmpty() && playlists.isEmpty()
}

/** Pre-normalised search text, built once per library change rather than on every keystroke. */
private class Indexed<T>(val item: T, val fields: List<Pair<String, Int>>)

@OptIn(FlowPreview::class)
class SearchViewModel(
    context: Context,
    mediaRepository: MediaRepository,
    playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("search", Context.MODE_PRIVATE)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _recent = MutableStateFlow(loadRecent())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    // Field weights: a match in the file name counts more than one in the folder or album.
    private val mediaIndex = combine(mediaRepository.observeAll(MediaType.VIDEO), mediaRepository.observeAll(MediaType.AUDIO)) { v, a ->
        (v + a).map { m ->
            Indexed(
                m,
                listOfNotNull(
                    SmartSearch.normalize(m.displayName) to 100,
                    SmartSearch.normalize(m.title) to 95,
                    m.artist?.let { SmartSearch.normalize(it) to 80 },
                    m.album?.let { SmartSearch.normalize(it) to 70 },
                    SmartSearch.normalize(m.folderName) to 50,
                ),
            )
        }
    }.flowOn(Dispatchers.Default)

    private val folderIndex = mediaRepository.observeFolders().map { folders ->
        folders.map { Indexed(it, listOf(SmartSearch.normalize(it.name) to 100, SmartSearch.normalize(it.relativePath) to 60)) }
    }

    private val playlistIndex = playlistRepository.observePlaylists().map { list ->
        list.map { Indexed(it, listOf(SmartSearch.normalize(it.name) to 100)) }
    }

    val results: StateFlow<SearchResults> = combine(
        _query.debounce(120),
        mediaIndex,
        folderIndex,
        playlistIndex,
    ) { query, media, folders, playlists ->
        val tokens = SmartSearch.tokens(query)
        if (tokens.isEmpty()) return@combine SearchResults(query)
        val rankedMedia = rank(media, tokens)
        SearchResults(
            query = query,
            videos = rankedMedia.filter { it.type == MediaType.VIDEO }.take(MAX_MEDIA),
            audio = rankedMedia.filter { it.type == MediaType.AUDIO }.take(MAX_MEDIA),
            folders = rank(folders, tokens).take(MAX_OTHER),
            playlists = rank(playlists, tokens).take(MAX_OTHER),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    private fun <T> rank(items: List<Indexed<T>>, tokens: List<String>): List<T> =
        items.mapNotNull { indexed ->
            val best = indexed.fields.maxOf { (text, weight) -> SmartSearch.score(tokens, text) * weight }
            if (best > 0) indexed.item to best else null
        }
            .sortedByDescending { it.second }
            .map { it.first }

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Remember a query once the user acts on a result or presses search. */
    fun commit() {
        val q = _query.value.trim()
        if (q.length < 2) return
        val updated = (listOf(q) + _recent.value.filterNot { it.equals(q, ignoreCase = true) }).take(MAX_RECENT)
        _recent.value = updated
        prefs.edit().putString(KEY_RECENT, updated.joinToString("\n")).apply()
    }

    fun clearRecent() {
        _recent.value = emptyList()
        prefs.edit().remove(KEY_RECENT).apply()
    }

    private fun loadRecent(): List<String> =
        prefs.getString(KEY_RECENT, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val KEY_RECENT = "recent_queries"
        const val MAX_RECENT = 8
        const val MAX_MEDIA = 50
        const val MAX_OTHER = 20
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenFolder: (MediaFolder) -> Unit,
    onOpenPlaylists: () -> Unit,
    viewModel: SearchViewModel = modernMediaViewModel {
        SearchViewModel(it.appContext, it.mediaRepository, it.playlistRepository)
    },
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sheetMedia by remember { mutableStateOf<MediaFile?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val play: (MediaFile) -> Unit = {
        viewModel.commit()
        context.playMedia(it)
    }

    Scaffold(

        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = auroraTopBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text("Search videos, music, folders") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            viewModel.commit()
                            keyboard?.hide()
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                },
            )
        },
    ) { padding ->
        when {
            query.isBlank() -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (recent.isNotEmpty()) {
                    SectionHeader("Recent searches", actionLabel = "Clear", onAction = viewModel::clearRecent)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(recent) { text ->
                            AssistChip(
                                onClick = { viewModel.setQuery(text) },
                                label = { Text(text) },
                                leadingIcon = { Icon(Icons.Rounded.History, null) },
                            )
                        }
                    }
                }
                Text(
                    text = "Tip: search is forgiving — try part of a name, an artist, a folder, or even a typo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            results.query == query && results.isEmpty -> EmptyState(
                icon = Icons.Rounded.Search,
                title = "No results for \"$query\"",
                message = "Try fewer or different words.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
            ) {
                if (results.videos.isNotEmpty()) {
                    item(key = "h-videos") { SectionHeader("Videos · ${results.videos.size}") }
                    items(results.videos, key = { "v${it.id}" }) { MediaListItem(it, onClick = { play(it) }, onLongClick = { sheetMedia = it }, onMore = { sheetMedia = it }) }
                }
                if (results.audio.isNotEmpty()) {
                    item(key = "h-audio") { SectionHeader("Music & audio · ${results.audio.size}") }
                    items(results.audio, key = { "a${it.id}" }) { MediaListItem(it, onClick = { play(it) }, onLongClick = { sheetMedia = it }, onMore = { sheetMedia = it }) }
                }
                if (results.folders.isNotEmpty()) {
                    item(key = "h-folders") { SectionHeader("Folders") }
                    items(results.folders, key = { "f${it.volumeName}/${it.relativePath}" }) { folder ->
                        FolderListItem(folder, onClick = {
                            viewModel.commit()
                            onOpenFolder(folder)
                        })
                    }
                }
                if (results.playlists.isNotEmpty()) {
                    item(key = "h-playlists") { SectionHeader("Playlists") }
                    items(results.playlists, key = { "p${it.id}" }) { playlist ->
                        androidx.compose.material3.ListItem(
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { Text("${playlist.itemCount} items") },
                            modifier = Modifier.clickable(onClick = onOpenPlaylists),
                        )
                    }
                }
            }
        }
    }
    sheetMedia?.let { media ->
        MediaActionsSheet(items = listOf(media), onDismiss = { sheetMedia = null })
    }
}
