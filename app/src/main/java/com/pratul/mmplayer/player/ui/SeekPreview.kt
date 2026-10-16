package com.pratul.mmplayer.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Frames for the seek preview, read straight from the video file. Frames are taken at the nearest
 * keyframe (fast) in one-second steps and cached, so dragging back and forth stays smooth.
 * Formats Android can't decode (some AVI/WMV) simply show no preview.
 */
class SeekThumbnails(private val context: Context, private val uri: Uri) {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val cache = LruCache<Long, ImageBitmap>(CACHE_FRAMES)
    private var retriever: MediaMetadataRetriever? = null
    private var broken = false

    suspend fun frameAt(positionMs: Long): ImageBitmap? {
        val second = positionMs / 1000
        cache.get(second)?.let { return it }
        if (broken) return null
        return withContext(dispatcher) {
            cache.get(second) ?: runCatching {
                val r = retriever ?: MediaMetadataRetriever().also {
                    it.setDataSource(context, uri)
                    retriever = it
                }
                val frame: Bitmap? = r.getScaledFrameAtTime(
                    second * 1_000_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    WIDTH_PX,
                    HEIGHT_PX,
                )
                frame?.asImageBitmap()?.also { cache.put(second, it) }
            }.onFailure { broken = true }.getOrNull()
        }
    }

    fun release() {
        runCatching { retriever?.release() }
        retriever = null
        cache.evictAll()
    }

    private companion object {
        const val WIDTH_PX = 320
        const val HEIGHT_PX = 180
        const val CACHE_FRAMES = 40
    }
}

/** One [SeekThumbnails] per video, released when the video changes or the screen closes. */
@Composable
fun rememberSeekThumbnails(uri: Uri?, isVideo: Boolean): SeekThumbnails? {
    val context = LocalContext.current
    val thumbnails = remember(uri, isVideo) { if (uri != null && isVideo) SeekThumbnails(context.applicationContext, uri) else null }
    DisposableEffect(thumbnails) { onDispose { thumbnails?.release() } }
    return thumbnails
}

/** The frame at [positionMs], or nothing while it loads or if the file has no usable frames. */
@Composable
fun SeekPreviewFrame(thumbnails: SeekThumbnails, positionMs: Long, modifier: Modifier = Modifier, width: Dp = 160.dp) {
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val second = positionMs / 1000
    LaunchedEffect(second) {
        delay(40) // Skip frames while the finger moves fast.
        thumbnails.frameAt(positionMs)?.let { frame = it }
    }
    val image = frame ?: return
    Box(
        modifier
            .size(width, width * 9 / 16)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(1.5.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
    ) {
        Image(image, contentDescription = "Preview", contentScale = ContentScale.Crop, modifier = Modifier.size(width, width * 9 / 16))
    }
}
