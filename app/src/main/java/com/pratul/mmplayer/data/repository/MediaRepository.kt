package com.pratul.mmplayer.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.pratul.mmplayer.data.database.dao.FavoriteDao
import com.pratul.mmplayer.data.database.dao.MediaDao
import com.pratul.mmplayer.data.database.dao.PlaybackHistoryDao
import com.pratul.mmplayer.data.database.entity.FavoriteEntity
import com.pratul.mmplayer.data.database.relation.FolderRow
import com.pratul.mmplayer.data.database.relation.MediaWithState
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.data.model.MediaFolder
import com.pratul.mmplayer.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Read side of the media library plus favorites/history state. All lists are Room-backed Flows,
 * so the UI updates automatically when the scanner, player or file operations change the database.
 */
class MediaRepository(
    private val mediaDao: MediaDao,
    private val historyDao: PlaybackHistoryDao,
    private val favoriteDao: FavoriteDao,
) {
    fun observeAll(type: MediaType): Flow<List<MediaFile>> =
        mediaDao.observeByType(type.name).mapItems()

    fun observeRecentlyAdded(type: MediaType, limit: Int): Flow<List<MediaFile>> =
        mediaDao.observeRecentlyAdded(type.name, limit).mapItems()

    fun observeRecentlyPlayed(type: MediaType, limit: Int): Flow<List<MediaFile>> =
        mediaDao.observeRecentlyPlayed(type.name, limit).mapItems()

    fun observeContinueWatching(limit: Int): Flow<List<MediaFile>> =
        mediaDao.observeContinueWatching(limit).mapItems()

    fun observeFavorites(type: MediaType, limit: Int): Flow<List<MediaFile>> =
        mediaDao.observeFavorites(type.name, limit).mapItems()

    fun observeDownloads(type: MediaType, limit: Int): Flow<List<MediaFile>> =
        mediaDao.observeInFolderPrefix(type.name, DOWNLOAD_FOLDER, limit).mapItems()

    fun observeCount(type: MediaType): Flow<Int> = mediaDao.observeCount(type.name)

    fun observeInFolder(volume: String, folderPath: String): Flow<List<MediaFile>> =
        mediaDao.observeInFolder(volume, folderPath).mapItems()

    fun observeFolders(): Flow<List<MediaFolder>> =
        mediaDao.observeFolders()
            .map { rows -> rows.map(FolderRow::toMediaFolder) }
            .flowOn(Dispatchers.Default)

    suspend fun setFavorite(mediaId: Long, favorite: Boolean) {
        if (favorite) {
            try {
                favoriteDao.insert(FavoriteEntity(mediaId, System.currentTimeMillis()))
            } catch (e: SQLiteConstraintException) {
                // The file was deleted from the phone a moment ago.
            }
        } else {
            favoriteDao.delete(mediaId)
        }
    }

    suspend fun clearHistory() = historyDao.clearAll()

    private fun Flow<List<MediaWithState>>.mapItems(): Flow<List<MediaFile>> =
        map { rows -> rows.map(MediaWithState::toMediaFile) }.flowOn(Dispatchers.Default)

    private companion object {
        const val DOWNLOAD_FOLDER = "Download/"
    }
}

internal fun MediaWithState.toMediaFile() = MediaFile(
    id = media.id,
    uri = media.uri,
    path = media.path,
    displayName = media.displayName,
    title = media.title,
    type = media.mediaType,
    mimeType = media.mimeType,
    durationMs = media.durationMs,
    sizeBytes = media.sizeBytes,
    width = media.width,
    height = media.height,
    folderPath = media.folderPath,
    folderName = media.folderName,
    artist = media.artist,
    album = media.album,
    dateAddedSec = media.dateAddedSec,
    dateModifiedSec = media.dateModifiedSec,
    positionMs = positionMs ?: 0,
    lastPlayedAt = lastPlayedAt,
    isFavorite = isFavorite,
    completed = completed == true,
)

private fun FolderRow.toMediaFolder() = MediaFolder(
    volumeName = volumeName,
    relativePath = folderPath,
    name = folderName,
    itemCount = itemCount,
    videoCount = videoCount,
    audioCount = audioCount,
    totalSizeBytes = totalSize,
    lastModifiedSec = lastModifiedSec,
)
