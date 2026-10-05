package com.pratul.mmplayer.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pratul.mmplayer.data.database.entity.MediaEntity
import com.pratul.mmplayer.data.database.relation.FolderRow
import com.pratul.mmplayer.data.database.relation.MediaWithState
import kotlinx.coroutines.flow.Flow

private const val MEDIA_WITH_STATE = """
    SELECT m.*, h.positionMs AS positionMs, h.lastPlayedAt AS lastPlayedAt,
           (f.mediaId IS NOT NULL) AS isFavorite, h.completed AS completed
    FROM media_files m
    LEFT JOIN playback_history h ON h.mediaId = m.id
    LEFT JOIN favorites f ON f.mediaId = m.id
"""

@Dao
interface MediaDao {

    @Query("$MEDIA_WITH_STATE WHERE m.mediaType = :type ORDER BY m.displayName COLLATE NOCASE")
    fun observeByType(type: String): Flow<List<MediaWithState>>

    @Query("$MEDIA_WITH_STATE WHERE m.mediaType = :type ORDER BY m.dateAddedSec DESC LIMIT :limit")
    fun observeRecentlyAdded(type: String, limit: Int): Flow<List<MediaWithState>>

    @Query(
        "$MEDIA_WITH_STATE WHERE m.mediaType = :type AND h.lastPlayedAt IS NOT NULL " +
            "ORDER BY h.lastPlayedAt DESC LIMIT :limit",
    )
    fun observeRecentlyPlayed(type: String, limit: Int): Flow<List<MediaWithState>>

    @Query(
        "$MEDIA_WITH_STATE WHERE m.mediaType = 'VIDEO' AND h.completed = 0 AND h.positionMs > 0 " +
            "ORDER BY h.lastPlayedAt DESC LIMIT :limit",
    )
    fun observeContinueWatching(limit: Int): Flow<List<MediaWithState>>

    @Query(
        "$MEDIA_WITH_STATE WHERE m.mediaType = :type AND f.mediaId IS NOT NULL " +
            "ORDER BY f.addedAt DESC LIMIT :limit",
    )
    fun observeFavorites(type: String, limit: Int): Flow<List<MediaWithState>>

    /** Media whose folder starts with [folderPrefix], e.g. "Download/" for the Downloads shelf. */
    @Query(
        "$MEDIA_WITH_STATE WHERE m.mediaType = :type AND m.folderPath LIKE :folderPrefix || '%' " +
            "ORDER BY m.dateAddedSec DESC LIMIT :limit",
    )
    fun observeInFolderPrefix(type: String, folderPrefix: String, limit: Int): Flow<List<MediaWithState>>

    @Query(
        """
        SELECT volumeName, folderPath, folderName,
               COUNT(*) AS itemCount,
               SUM(CASE WHEN mediaType = 'VIDEO' THEN 1 ELSE 0 END) AS videoCount,
               SUM(CASE WHEN mediaType = 'AUDIO' THEN 1 ELSE 0 END) AS audioCount,
               SUM(sizeBytes) AS totalSize,
               MAX(dateModifiedSec) AS lastModifiedSec
        FROM media_files
        GROUP BY volumeName, folderPath
        ORDER BY folderName COLLATE NOCASE
        """,
    )
    fun observeFolders(): Flow<List<FolderRow>>

    @Query("SELECT COUNT(*) FROM media_files WHERE mediaType = :type")
    fun observeCount(type: String): Flow<Int>

    @Query(
        "$MEDIA_WITH_STATE WHERE m.volumeName = :volume AND m.folderPath = :folderPath " +
            "ORDER BY m.mediaType DESC, m.displayName COLLATE NOCASE",
    )
    fun observeInFolder(volume: String, folderPath: String): Flow<List<MediaWithState>>

    // --- Player support ---

    @Query("SELECT * FROM media_files WHERE id = :id")
    suspend fun getById(id: Long): MediaEntity?

    /** Matches a file opened from another app (no MediaStore id in its uri) to the library. */
    @Query("SELECT * FROM media_files WHERE displayName = :name AND (:size <= 0 OR sizeBytes = :size) LIMIT 1")
    suspend fun findByNameAndSize(name: String, size: Long): MediaEntity?

    @Query("SELECT * FROM media_files WHERE volumeName = :volume AND folderPath = :folderPath AND mediaType = :type")
    suspend fun getInFolder(volume: String, folderPath: String, type: String): List<MediaEntity>

    // --- Scanner support ---

    @Query("SELECT COUNT(*) FROM media_files")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(items: List<MediaEntity>)

    @Query("SELECT id FROM media_files")
    suspend fun getAllIds(): List<Long>

    @Query("DELETE FROM media_files WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
