package com.pratul.mmplayer.data.repository

import com.pratul.mmplayer.data.database.dao.PlaylistDao
import com.pratul.mmplayer.data.database.entity.PlaylistEntity
import com.pratul.mmplayer.data.database.entity.PlaylistItemEntity
import com.pratul.mmplayer.data.database.relation.MediaWithState
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.data.model.PlaylistSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One row of a playlist: [itemId] identifies the entry (the same file may appear in several playlists). */
data class PlaylistEntry(val itemId: Long, val media: MediaFile)

sealed interface CreatePlaylistResult {
    data class Created(val id: Long) : CreatePlaylistResult
    data object EmptyName : CreatePlaylistResult
    data object Duplicate : CreatePlaylistResult
}

class PlaylistRepository(private val playlistDao: PlaylistDao) {

    fun observePlaylists(): Flow<List<PlaylistSummary>> =
        playlistDao.observePlaylists().map { rows ->
            rows.map {
                PlaylistSummary(
                    id = it.playlist.id,
                    name = it.playlist.name,
                    itemCount = it.itemCount,
                    updatedAt = it.playlist.updatedAt,
                )
            }
        }

    fun observeEntries(playlistId: Long): Flow<List<PlaylistEntry>> =
        playlistDao.observeItems(playlistId).map { rows ->
            rows.map { row ->
                PlaylistEntry(
                    itemId = row.itemId,
                    media = MediaWithState(row.media, row.positionMs, row.lastPlayedAt, row.isFavorite, row.completed).toMediaFile(),
                )
            }
        }

    suspend fun create(name: String): CreatePlaylistResult {
        val clean = name.trim()
        if (clean.isEmpty()) return CreatePlaylistResult.EmptyName
        if (playlistDao.countByName(clean) > 0) return CreatePlaylistResult.Duplicate
        val now = System.currentTimeMillis()
        return CreatePlaylistResult.Created(playlistDao.insert(PlaylistEntity(name = clean, createdAt = now, updatedAt = now)))
    }

    suspend fun rename(id: Long, name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || playlistDao.countByName(clean) > 0) return false
        playlistDao.rename(id, clean, System.currentTimeMillis())
        return true
    }

    suspend fun delete(id: Long) = playlistDao.delete(id)

    /** Appends files to the end of a playlist, skipping ones already in it. Returns how many were added. */
    suspend fun add(playlistId: Long, mediaIds: List<Long>): Int {
        val existing = playlistDao.mediaIds(playlistId).toSet()
        val fresh = mediaIds.distinct().filterNot { it in existing }
        if (fresh.isEmpty()) return 0
        val start = playlistDao.lastPosition(playlistId) + 1
        val now = System.currentTimeMillis()
        playlistDao.insertItems(fresh.mapIndexed { i, id -> PlaylistItemEntity(playlistId = playlistId, mediaId = id, position = start + i, addedAt = now) })
        playlistDao.touch(playlistId, now)
        return fresh.size
    }

    suspend fun remove(playlistId: Long, itemId: Long) {
        playlistDao.deleteItem(itemId)
        playlistDao.touch(playlistId, System.currentTimeMillis())
    }

    /** Writes a new order (e.g. after moving an entry up or down). */
    suspend fun reorder(playlistId: Long, orderedItemIds: List<Long>) {
        orderedItemIds.forEachIndexed { index, itemId -> playlistDao.setPosition(itemId, index) }
        playlistDao.touch(playlistId, System.currentTimeMillis())
    }
}
