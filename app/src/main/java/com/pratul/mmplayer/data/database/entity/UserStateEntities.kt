package com.pratul.mmplayer.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Resume position and per-file playback preferences. One row per media file that has been played. */
@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("lastPlayedAt")],
)
data class PlaybackHistoryEntity(
    @PrimaryKey val mediaId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayedAt: Long,
    /** Set when playback reached the end (or close to it); the resume position is then cleared. */
    val completed: Boolean = false,
    val playCount: Int = 1,
    /** Remembered audio track (BCP-47 language or track id) for multi-track videos. */
    val audioTrack: String? = null,
    /** Remembered external subtitle file uri. */
    val subtitleUri: String? = null,
    val subtitleDelayMs: Long = 0,
    val playbackSpeed: Float? = null,
    /** Embedded subtitle choice: a language/label, or "off". */
    val subtitleTrack: String? = null,
    val aspectRatio: String? = null,
    @ColumnInfo(defaultValue = "1") val zoom: Float = 1f,
)

@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FavoriteEntity(
    @PrimaryKey val mediaId: Long,
    val addedAt: Long,
)

@Entity(
    tableName = "playlists",
    indices = [Index("name", unique = true)],
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId", "position"), Index("mediaId")],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val mediaId: Long,
    val position: Int,
    val addedAt: Long,
)
