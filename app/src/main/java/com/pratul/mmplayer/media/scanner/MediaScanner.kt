package com.pratul.mmplayer.media.scanner

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.pratul.mmplayer.data.database.dao.MediaDao
import com.pratul.mmplayer.data.database.entity.MediaEntity
import com.pratul.mmplayer.data.model.MediaType
import com.pratul.mmplayer.utils.MediaAccess
import com.pratul.mmplayer.utils.MediaPermissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ScanResult(val videos: Int, val audio: Int, val skipped: Boolean = false)

/**
 * Builds the local library from Android's MediaStore index instead of walking the file system:
 * MediaStore already knows every video and audio file on internal storage and SD cards, so a full
 * scan of thousands of files takes well under a second and never touches the files themselves.
 *
 * Scans are skipped when MediaStore reports no changes since the last one (Android 11+), and a
 * [ContentObserver] triggers a quick re-scan when files are added, removed or edited elsewhere.
 */
class MediaScanner(
    private val context: Context,
    private val mediaDao: MediaDao,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences("media_scanner", Context.MODE_PRIVATE)
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var observerRegistered = false
    private var pendingRescan: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // Changes arrive in bursts (e.g. a download or a camera recording); wait for quiet.
            pendingRescan?.cancel()
            pendingRescan = scope.launch {
                delay(RESCAN_DEBOUNCE_MS)
                scan(force = false)
            }
        }
    }

    /** Called at app start and whenever media access is granted. */
    fun start() {
        if (MediaPermissions.currentAccess(context) == MediaAccess.NONE) return
        registerObserver()
        scope.launch { scan(force = false) }
    }

    suspend fun scan(force: Boolean): ScanResult = mutex.withLock {
        if (MediaPermissions.currentAccess(context) == MediaAccess.NONE) return ScanResult(0, 0, skipped = true)
        registerObserver()
        withContext(Dispatchers.IO) {
            val volumes = MediaStore.getExternalVolumeNames(context).toList()
            val stamp = changeStamp(volumes)
            if (!force && stamp != null && stamp == prefs.getString(KEY_STAMP, null) && mediaDao.count() > 0) {
                return@withContext ScanResult(0, 0, skipped = true)
            }
            _isScanning.value = true
            try {
                val found = ArrayList<MediaEntity>(1024)
                for (volume in volumes) {
                    runCatching { found += query(volume, MediaType.VIDEO) }
                        .onFailure { Log.w(TAG, "Video scan failed on $volume", it) }
                    runCatching { found += query(volume, MediaType.AUDIO) }
                        .onFailure { Log.w(TAG, "Audio scan failed on $volume", it) }
                }
                // Upsert (never REPLACE) so history, favorites and playlists stay attached.
                found.chunked(CHUNK).forEach { mediaDao.upsertAll(it) }
                val seen = found.mapTo(HashSet(found.size)) { it.id }
                val removed = mediaDao.getAllIds().filterNot { it in seen }
                removed.chunked(CHUNK).forEach { mediaDao.deleteByIds(it) }
                stamp?.let { prefs.edit().putString(KEY_STAMP, it).apply() }
                ScanResult(
                    videos = found.count { it.mediaType == MediaType.VIDEO },
                    audio = found.count { it.mediaType == MediaType.AUDIO },
                )
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun query(volume: String, type: MediaType): List<MediaEntity> {
        val collection: Uri = when (type) {
            MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(volume)
            MediaType.AUDIO -> MediaStore.Audio.Media.getContentUri(volume)
        }
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.TITLE)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DURATION)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.RELATIVE_PATH)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            @Suppress("DEPRECATION") add(MediaStore.MediaColumns.DATA) // Read-only path, used to find subtitles.
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            if (type == MediaType.AUDIO) {
                add(MediaStore.Audio.AudioColumns.ARTIST)
                add(MediaStore.Audio.AudioColumns.ALBUM)
                add(MediaStore.Audio.AudioColumns.ALBUM_ID)
            }
        }.toTypedArray()

        val result = ArrayList<MediaEntity>()
        context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
            val col = projection.associateWith { c.getColumnIndexOrThrow(it) }
            while (c.moveToNext()) {
                val id = c.getLong(col.getValue(MediaStore.MediaColumns._ID))
                val name = c.str(col, MediaStore.MediaColumns.DISPLAY_NAME) ?: continue
                val relativePath = c.str(col, MediaStore.MediaColumns.RELATIVE_PATH).orEmpty()
                val folderName = c.str(col, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                    ?: relativePath.trimEnd('/').substringAfterLast('/').ifBlank { "Internal storage" }
                result += MediaEntity(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    path = c.str(col, @Suppress("DEPRECATION") MediaStore.MediaColumns.DATA),
                    displayName = name,
                    title = c.str(col, MediaStore.MediaColumns.TITLE)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.'),
                    mediaType = type,
                    mimeType = c.str(col, MediaStore.MediaColumns.MIME_TYPE),
                    durationMs = c.long(col, MediaStore.MediaColumns.DURATION),
                    sizeBytes = c.long(col, MediaStore.MediaColumns.SIZE),
                    width = c.long(col, MediaStore.MediaColumns.WIDTH).toInt(),
                    height = c.long(col, MediaStore.MediaColumns.HEIGHT).toInt(),
                    folderPath = relativePath,
                    folderName = folderName,
                    volumeName = volume,
                    artist = if (type == MediaType.AUDIO) c.str(col, MediaStore.Audio.AudioColumns.ARTIST)?.takeUnless { it == MediaStore.UNKNOWN_STRING } else null,
                    album = if (type == MediaType.AUDIO) c.str(col, MediaStore.Audio.AudioColumns.ALBUM) else null,
                    albumId = if (type == MediaType.AUDIO) c.long(col, MediaStore.Audio.AudioColumns.ALBUM_ID) else null,
                    dateAddedSec = c.long(col, MediaStore.MediaColumns.DATE_ADDED),
                    dateModifiedSec = c.long(col, MediaStore.MediaColumns.DATE_MODIFIED),
                )
            }
        }
        return result
    }

    /** MediaStore version + per-volume generation: changes whenever any media file changes (Android 11+). */
    private fun changeStamp(volumes: List<String>): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return buildString {
            append(MediaStore.getVersion(context))
            volumes.sorted().forEach { append('|').append(it).append(':').append(MediaStore.getGeneration(context, it)) }
        }
    }

    private fun registerObserver() {
        if (observerRegistered) return
        observerRegistered = true
        val resolver = context.contentResolver
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
    }

    private fun Cursor.str(columns: Map<String, Int>, name: String): String? =
        columns[name]?.let { if (isNull(it)) null else getString(it) }

    private fun Cursor.long(columns: Map<String, Int>, name: String): Long =
        columns[name]?.let { if (isNull(it)) 0L else getLong(it) } ?: 0L

    private companion object {
        const val TAG = "MediaScanner"
        const val KEY_STAMP = "last_change_stamp"
        const val CHUNK = 500
        const val RESCAN_DEBOUNCE_MS = 2_000L
    }
}
