package com.pratul.mmplayer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.data.model.MediaFolder
import com.pratul.mmplayer.data.model.MediaType
import com.pratul.mmplayer.data.model.PlaylistSummary
import com.pratul.mmplayer.data.repository.MediaRepository
import com.pratul.mmplayer.data.repository.PlaylistRepository
import com.pratul.mmplayer.media.scanner.MediaScanner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import com.pratul.mmplayer.utils.SmartNames

enum class HomeTab { VIDEOS, AUDIO, FOLDERS }

/** A show detected from episode file names, with the episode to watch next. */
data class SeriesSummary(
    val name: String,
    val episodeCount: Int,
    val next: MediaFile,
    val nextLabel: String,
    val lastActivity: Long,
)

data class VideoSectionState(
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val continueWatching: List<MediaFile> = emptyList(),
    val recentlyPlayed: List<MediaFile> = emptyList(),
    val favorites: List<MediaFile> = emptyList(),
    val downloads: List<MediaFile> = emptyList(),
    val recentlyAdded: List<MediaFile> = emptyList(),
    val series: List<SeriesSummary> = emptyList(),
    val newVideos: List<MediaFile> = emptyList(),
)

data class AudioSectionState(
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val recentlyPlayed: List<MediaFile> = emptyList(),
    val favorites: List<MediaFile> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val recentlyAdded: List<MediaFile> = emptyList(),
)

data class FolderSectionState(
    val isLoading: Boolean = true,
    val folders: List<MediaFolder> = emptyList(),
)

class HomeViewModel(
    private val mediaRepository: MediaRepository,
    playlistRepository: PlaylistRepository,
    private val scanner: MediaScanner,
) : ViewModel() {

    val videos: StateFlow<VideoSectionState> = combine(
        mediaRepository.observeContinueWatching(SHELF_LIMIT),
        mediaRepository.observeRecentlyPlayed(MediaType.VIDEO, SHELF_LIMIT),
        mediaRepository.observeFavorites(MediaType.VIDEO, SHELF_LIMIT),
        mediaRepository.observeDownloads(MediaType.VIDEO, SHELF_LIMIT),
        mediaRepository.observeRecentlyAdded(MediaType.VIDEO, LIST_PREVIEW_LIMIT),
    ) { continueWatching, recent, favorites, downloads, added ->
        VideoSectionState(
            isLoading = false,
            continueWatching = continueWatching,
            recentlyPlayed = recent,
            favorites = favorites,
            downloads = downloads,
            recentlyAdded = added,
        )
    }.combine(mediaRepository.observeCount(MediaType.VIDEO)) { state, count ->
        state.copy(totalCount = count)
    }.combine(mediaRepository.observeAll(MediaType.VIDEO).map { all -> smartShelves(all) }.flowOn(Dispatchers.Default)) { state, shelves ->
        state.copy(series = shelves.first, newVideos = shelves.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), VideoSectionState())

    val audio: StateFlow<AudioSectionState> = combine(
        mediaRepository.observeRecentlyPlayed(MediaType.AUDIO, SHELF_LIMIT),
        mediaRepository.observeFavorites(MediaType.AUDIO, SHELF_LIMIT),
        playlistRepository.observePlaylists(),
        mediaRepository.observeRecentlyAdded(MediaType.AUDIO, LIST_PREVIEW_LIMIT),
        mediaRepository.observeCount(MediaType.AUDIO),
    ) { recent, favorites, playlists, added, count ->
        AudioSectionState(
            isLoading = false,
            totalCount = count,
            recentlyPlayed = recent,
            favorites = favorites,
            playlists = playlists,
            recentlyAdded = added,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AudioSectionState())

    val folders: StateFlow<FolderSectionState> = mediaRepository.observeFolders()
        .map { FolderSectionState(isLoading = false, folders = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FolderSectionState())

    /** Full rescan from the "Scan media" action; reports what was found. */
    fun scan(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = scanner.scan(force = true)
            onResult(
                if (result.skipped) {
                    "Allow media access to scan your phone"
                } else {
                    "Found ${result.videos} videos and ${result.audio} audio files"
                },
            )
        }
    }

    fun toggleFavorite(media: MediaFile) {
        viewModelScope.launch { mediaRepository.setFavorite(media.id, !media.isFavorite) }
    }

    private companion object {
        const val SHELF_LIMIT = 12
        const val LIST_PREVIEW_LIMIT = 8
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Builds the smart shelves from the whole video library:
 * - series: episodes grouped by show; "next" is the episode in progress, else the one after the
 *   last watched, else the first;
 * - new: unwatched videos added in the last week.
 */
private fun smartShelves(videos: List<MediaFile>): Pair<List<SeriesSummary>, List<MediaFile>> {
    val series = videos
        .mapNotNull { media -> SmartNames.parseEpisode(media.displayName)?.let { media to it } }
        .groupBy { it.second.showKey }
        .values
        .filter { it.size >= 2 }
        .map { group ->
            val episodes = group.sortedWith(compareBy({ it.second.season ?: 0 }, { it.second.episode }))
            val inProgress = episodes
                .filter { (m, _) -> m.positionMs > 0 && !m.completed }
                .maxByOrNull { (m, _) -> m.lastPlayedAt ?: 0L }
            val lastWatched = episodes.indices.filter { episodes[it].first.lastPlayedAt != null }
                .maxByOrNull { episodes[it].first.lastPlayedAt ?: 0L }
            val next = inProgress
                ?: lastWatched?.let { i -> if (episodes[i].first.completed) episodes.getOrNull(i + 1) ?: episodes[i] else episodes[i] }
                ?: episodes.first()
            SeriesSummary(
                name = episodes.first().second.show,
                episodeCount = episodes.size,
                next = next.first,
                nextLabel = next.second.label,
                lastActivity = episodes.maxOf { (m, _) -> m.lastPlayedAt ?: (m.dateAddedSec * 1000) },
            )
        }
        .sortedByDescending { it.lastActivity }
        .take(MAX_SERIES)

    val weekAgoSec = System.currentTimeMillis() / 1000 - NEW_WINDOW_SEC
    val newVideos = videos
        .filter { it.lastPlayedAt == null && it.dateAddedSec >= weekAgoSec }
        .sortedByDescending { it.dateAddedSec }
        .take(MAX_SERIES)
    return series to newVideos
}

private const val MAX_SERIES = 12
private const val NEW_WINDOW_SEC = 7L * 24 * 60 * 60
