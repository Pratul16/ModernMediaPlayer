package com.pratul.mmplayer.player.session

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.pratul.mmplayer.data.database.dao.MediaDao
import com.pratul.mmplayer.data.database.entity.MediaEntity
import com.pratul.mmplayer.utils.SmartNames
import java.io.File

/** One playable item. [mediaId] is set when the file is in the library (needed for history). */
data class QueueEntry(
    val uri: Uri,
    val title: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val mediaId: Long?,
    val path: String?,
    val volumeName: String?,
    val folderPath: String?,
) {
    val isAudio: Boolean get() = mimeType?.startsWith("audio/") == true
}

data class PlaybackQueue(val entries: List<QueueEntry>, val startIndex: Int)

/**
 * Turns "play this file" into a queue: the file plus the other videos (or songs) in the same
 * folder, ordered as episodes ("S01E02" before "S01E10"), so Next and auto-play follow the series.
 * Files opened from other apps are matched to the library when possible.
 */
class QueueBuilder(private val context: Context, private val mediaDao: MediaDao) {

    /** A given list of library files (playlist, favorites, shuffled order), starting at [startIndex]. */
    suspend fun fromIds(ids: List<Long>, startIndex: Int): PlaybackQueue? {
        val entries = ids.mapNotNull { mediaDao.getById(it)?.toEntry() }
        if (entries.isEmpty()) return null
        return PlaybackQueue(entries, startIndex.coerceIn(0, entries.lastIndex))
    }

    suspend fun build(uri: Uri, mimeType: String?): PlaybackQueue {
        val media = findInLibrary(uri)
        if (media == null) {
            val (name, size) = describe(uri)
            return PlaybackQueue(listOf(QueueEntry(uri, name, mimeType, size, null, uri.path, null, null)), 0)
        }
        val siblings = mediaDao.getInFolder(media.volumeName, media.folderPath, media.mediaType.name)
            .sortedWith(compareBy(SmartNames.naturalOrder) { it.displayName })
            .ifEmpty { listOf(media) }
        val entries = siblings.map { it.toEntry() }
        val start = siblings.indexOfFirst { it.id == media.id }.coerceAtLeast(0)
        return PlaybackQueue(entries, start)
    }

    private suspend fun findInLibrary(uri: Uri): MediaEntity? {
        if (uri.authority == "media") {
            runCatching { ContentUris.parseId(uri) }.getOrNull()?.let { id ->
                mediaDao.getById(id)?.let { return it }
            }
        }
        val (name, size) = describe(uri)
        return mediaDao.findByNameAndSize(name, size ?: 0)
    }

    private fun describe(uri: Uri): Pair<String, Long?> {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { c ->
                        if (c.moveToFirst()) {
                            return (c.getString(0) ?: fallbackName(uri)) to (if (c.isNull(1)) null else c.getLong(1))
                        }
                    }
            }
            return fallbackName(uri) to null
        }
        val file = uri.path?.let(::File)
        return (file?.name ?: fallbackName(uri)) to file?.length()?.takeIf { it > 0 }
    }

    private fun fallbackName(uri: Uri) = uri.lastPathSegment ?: "Video"

    private fun MediaEntity.toEntry() = QueueEntry(
        uri = Uri.parse(uri),
        title = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        mediaId = id,
        path = path,
        volumeName = volumeName,
        folderPath = folderPath,
    )
}
