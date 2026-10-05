package com.pratul.mmplayer.media.thumbnail

import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse

/** Coil model for a MediaStore thumbnail. [dateModifiedSec] invalidates the cache when the file changes. */
data class MediaThumbnail(val uri: Uri, val dateModifiedSec: Long)

/**
 * Loads thumbnails through [android.content.ContentResolver.loadThumbnail]. MediaStore generates and
 * caches these on disk itself, so a video frame is decoded at most once per file across app restarts,
 * and only small bitmaps sized to the view are produced. Coil's memory cache covers scrolling.
 * Works for video frames and embedded audio album art alike.
 */
class MediaThumbnailFetcher(
    private val data: MediaThumbnail,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val width = options.size.width.pxOrElse { DEFAULT_SIZE_PX }
        val height = options.size.height.pxOrElse { DEFAULT_SIZE_PX }
        val bitmap = options.context.contentResolver.loadThumbnail(data.uri, Size(width, height), null)
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<MediaThumbnail> {
        override fun create(data: MediaThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbnailFetcher(data, options)
    }

    private companion object {
        const val DEFAULT_SIZE_PX = 384
    }
}

class MediaThumbnailKeyer : Keyer<MediaThumbnail> {
    override fun key(data: MediaThumbnail, options: Options): String = "${data.uri}#${data.dateModifiedSec}"
}
