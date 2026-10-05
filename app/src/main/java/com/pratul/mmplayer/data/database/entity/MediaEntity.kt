package com.pratul.mmplayer.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pratul.mmplayer.data.model.MediaType

/**
 * Local index of a MediaStore video or audio row.
 *
 * [id] is the MediaStore `_ID`, which is stable across rescans, so history, favorites and playlist
 * items survive a rescan. The scanner must write with `@Upsert` (never REPLACE): REPLACE deletes the
 * row first and would cascade-delete everything that references it.
 *
 * Thumbnails are not stored here; they come from MediaStore's own thumbnail cache through Coil
 * (see `media/thumbnail`), keyed by uri + [dateModifiedSec].
 */
@Entity(
    tableName = "media_files",
    indices = [
        Index("mediaType"),
        Index("volumeName", "folderPath"),
        Index("displayName"),
        Index("dateAddedSec"),
    ],
)
data class MediaEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    /** Absolute path when MediaStore reports one; used for subtitle discovery next to the file. */
    val path: String?,
    val displayName: String,
    val title: String,
    val mediaType: MediaType,
    val mimeType: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    /** MediaStore RELATIVE_PATH, e.g. "Movies/Trailers/". */
    val folderPath: String,
    val folderName: String,
    /** MediaStore volume, e.g. "external_primary" or an SD card id. */
    val volumeName: String,
    val artist: String?,
    val album: String?,
    val albumId: Long?,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
)
