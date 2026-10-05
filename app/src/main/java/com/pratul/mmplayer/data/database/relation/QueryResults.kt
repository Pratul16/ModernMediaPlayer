package com.pratul.mmplayer.data.database.relation

import androidx.room.Embedded
import com.pratul.mmplayer.data.database.entity.MediaEntity
import com.pratul.mmplayer.data.database.entity.PlaylistEntity

/** A media row joined with the user's history and favorite state. */
data class MediaWithState(
    @Embedded val media: MediaEntity,
    val positionMs: Long?,
    val lastPlayedAt: Long?,
    val isFavorite: Boolean,
    val completed: Boolean?,
)

/** One folder aggregated from media_files (MediaStore has no folder table of its own). */
data class FolderRow(
    val volumeName: String,
    val folderPath: String,
    val folderName: String,
    val itemCount: Int,
    val videoCount: Int,
    val audioCount: Int,
    val totalSize: Long,
    val lastModifiedSec: Long,
)

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    val itemCount: Int,
)

/** A playlist entry joined with its media and the user's state for it. */
data class PlaylistItemRow(
    val itemId: Long,
    val itemPosition: Int,
    @Embedded val media: MediaEntity,
    val positionMs: Long?,
    val lastPlayedAt: Long?,
    val isFavorite: Boolean,
    val completed: Boolean?,
)
