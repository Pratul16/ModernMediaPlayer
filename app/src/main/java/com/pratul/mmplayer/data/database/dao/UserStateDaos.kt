package com.pratul.mmplayer.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.pratul.mmplayer.data.database.entity.FavoriteEntity
import com.pratul.mmplayer.data.database.entity.PlaybackHistoryEntity
import com.pratul.mmplayer.data.database.entity.PlaylistEntity
import com.pratul.mmplayer.data.database.entity.PlaylistItemEntity
import com.pratul.mmplayer.data.database.relation.PlaylistItemRow
import com.pratul.mmplayer.data.database.relation.PlaylistWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId")
    suspend fun get(mediaId: Long): PlaybackHistoryEntity?

    @Upsert
    suspend fun upsert(entry: PlaybackHistoryEntity)

    @Query("DELETE FROM playback_history WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: Long)

    @Query("DELETE FROM playback_history")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :mediaId)")
    suspend fun isFavorite(mediaId: Long): Boolean
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.*, COUNT(i.id) AS itemCount
        FROM playlists p LEFT JOIN playlist_items i ON i.playlistId = p.id
        GROUP BY p.id
        ORDER BY p.updatedAt DESC
        """,
    )
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, name: String, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun lastPosition(playlistId: Long): Int

    @Query(
        """
        SELECT i.id AS itemId, i.position AS itemPosition, m.*, h.positionMs AS positionMs,
               h.lastPlayedAt AS lastPlayedAt, (f.mediaId IS NOT NULL) AS isFavorite, h.completed AS completed
        FROM playlist_items i
        JOIN media_files m ON m.id = i.mediaId
        LEFT JOIN playback_history h ON h.mediaId = m.id
        LEFT JOIN favorites f ON f.mediaId = m.id
        WHERE i.playlistId = :playlistId
        ORDER BY i.position
        """,
    )
    fun observeItems(playlistId: Long): Flow<List<PlaylistItemRow>>

    @Query("SELECT mediaId FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun mediaIds(playlistId: Long): List<Long>

    @Query("DELETE FROM playlist_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)

    @Query("UPDATE playlist_items SET position = :position WHERE id = :itemId")
    suspend fun setPosition(itemId: Long, position: Int)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM playlists WHERE name = :name COLLATE NOCASE")
    suspend fun countByName(name: String): Int
}
