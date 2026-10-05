package com.pratul.mmplayer.media.files

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Where a new folder can live. Android only lets media apps write into these shared folders. */
enum class FolderBase(val directory: String, val label: String, val allowsVideo: Boolean, val allowsAudio: Boolean) {
    MOVIES(Environment.DIRECTORY_MOVIES, "Movies", allowsVideo = true, allowsAudio = false),
    MUSIC(Environment.DIRECTORY_MUSIC, "Music", allowsVideo = false, allowsAudio = true),
    DOWNLOAD(Environment.DIRECTORY_DOWNLOADS, "Download", allowsVideo = true, allowsAudio = true),
}

/** Files waiting to be pasted into a folder: copied (kept) or cut (moved on paste). */
data class FileClip(val items: List<MediaFile>, val cut: Boolean)

/** A long-running copy, shown as a progress bar. */
data class FileTask(val label: String, val fraction: Float)

/** A folder created in the app, remembered so it is listed even while still empty. */
data class CreatedFolder(val relativePath: String, val name: String)

/**
 * Folder creation and moving files between folders, using the APIs Android allows a media app:
 * - folders are created inside the shared Movies / Music / Download directories;
 * - on Android 11+ moving a file means asking the system for write access (one consent dialog
 *   for all selected files), then changing the file's MediaStore RELATIVE_PATH;
 * - on Android 10 the legacy file API is used (WRITE_EXTERNAL_STORAGE with legacy storage).
 */
class FileOperations(private val context: Context) {

    private val prefs = context.getSharedPreferences("created_folders", Context.MODE_PRIVATE)
    private val _created = MutableStateFlow(load())
    val createdFolders: StateFlow<List<CreatedFolder>> = _created.asStateFlow()

    private val _clip = MutableStateFlow<FileClip?>(null)
    val clipboard: StateFlow<FileClip?> = _clip.asStateFlow()

    private val _task = MutableStateFlow<FileTask?>(null)
    val task: StateFlow<FileTask?> = _task.asStateFlow()

    fun setClip(clip: FileClip?) {
        _clip.value = clip
    }

    /** Null when [name] is a valid new folder name, otherwise the reason it is not. */
    fun validateFolderName(name: String, base: FolderBase): String? {
        val clean = name.trim()
        return when {
            clean.isEmpty() -> "Enter a folder name"
            clean.length > 100 -> "Name is too long"
            clean == "." || clean == ".." -> "That name is reserved"
            clean.startsWith(".") -> "Names starting with a dot are hidden"
            clean.any { it in INVALID_CHARS || it.isISOControl() } -> "A folder name can't contain \\ / : * ? \" < > |"
            File(baseDir(base), clean).exists() -> "A folder with this name already exists"
            else -> null
        }
    }

    suspend fun createFolder(base: FolderBase, name: String): Result<CreatedFolder> = withContext(Dispatchers.IO) {
        val clean = name.trim()
        validateFolderName(clean, base)?.let { return@withContext Result.failure(IllegalArgumentException(it)) }
        val dir = File(baseDir(base), clean)
        if (!dir.mkdirs() && !dir.isDirectory) {
            return@withContext Result.failure(IllegalStateException("Android didn't allow creating this folder"))
        }
        val folder = CreatedFolder(relativePath = "${base.directory}/$clean/", name = clean)
        remember(folder)
        Result.success(folder)
    }

    /**
     * Android 11+: the consent dialog to launch before [move] for files this app did not create.
     * Null on Android 10 (no dialog needed).
     */
    fun writeRequest(uris: List<Uri>): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createWriteRequest(context.contentResolver, uris).intentSender
        } else {
            null
        }

    /** Moves files into [relativePath] (e.g. "Movies/Holiday/"). Returns how many were moved. */
    suspend fun move(uris: List<Uri>, relativePath: String): Int = withContext(Dispatchers.IO) {
        var moved = 0
        for (uri in uris) {
            val ok = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val values = ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) }
                    context.contentResolver.update(uri, values, null, null) > 0
                } else {
                    moveLegacy(uri, relativePath)
                }
            }.getOrDefault(false)
            if (ok) moved++
        }
        // Once something is in it, MediaStore lists the folder by itself.
        if (moved > 0) forget(relativePath)
        moved
    }

    /**
     * Android 11+: the system dialog that deletes the files itself once the user agrees.
     * Null on Android 10, where [delete] removes them directly.
     */
    fun deleteRequest(uris: List<Uri>): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        } else {
            null
        }

    /** Android 10 delete (legacy storage). Returns how many files were removed. */
    suspend fun delete(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        uris.count { uri ->
            runCatching {
                val path = dataPath(uri)
                val removedFile = path == null || File(path).let { !it.exists() || it.delete() }
                context.contentResolver.delete(uri, null, null)
                removedFile
            }.getOrDefault(false)
        }
    }

    /** Null when [name] is a valid file name (without extension), otherwise the reason it is not. */
    fun validateFileName(name: String): String? {
        val clean = name.trim()
        return when {
            clean.isEmpty() -> "Enter a name"
            clean.length > 120 -> "Name is too long"
            clean.startsWith(".") -> "Names starting with a dot are hidden"
            clean.any { it in INVALID_CHARS || it.isISOControl() } -> "A name can't contain \\ / : * ? \" < > |"
            else -> null
        }
    }

    /** Renames a file, keeping it in its folder. Needs [writeRequest] consent first on Android 11+. */
    suspend fun rename(uri: Uri, newDisplayName: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newDisplayName) }
                context.contentResolver.update(uri, values, null, null) > 0
            } else {
                val source = File(dataPath(uri) ?: return@runCatching false)
                val target = File(source.parentFile, newDisplayName)
                if (target.exists() || !source.renameTo(target)) return@runCatching false
                MediaScannerConnection.scanFile(context, arrayOf(source.absolutePath, target.absolutePath), null, null)
                true
            }
        }.getOrDefault(false)
    }

    /**
     * Copies files into [relativePath] as new media entries (no consent needed: the copies belong
     * to this app). Same-name files get a " (1)" suffix from Android. Reports progress through
     * [task] and returns how many were copied.
     */
    suspend fun copy(items: List<MediaFile>, relativePath: String): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val totalBytes = items.sumOf { it.sizeBytes.coerceAtLeast(1) }.toFloat()
        var doneBytes = 0L
        var copied = 0
        val label = if (items.size == 1) "Copying ${items.first().displayName}" else "Copying ${items.size} files"
        _task.value = FileTask(label, 0f)
        try {
            for (item in items) {
                val collection = when (item.type) {
                    MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    MediaType.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    mimeFor(item)?.let { put(MediaStore.MediaColumns.MIME_TYPE, it) }
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val target = runCatching { resolver.insert(collection, values) }.getOrNull()
                if (target == null) {
                    doneBytes += item.sizeBytes
                    continue
                }
                val ok = runCatching {
                    resolver.openInputStream(Uri.parse(item.uri))!!.use { input ->
                        resolver.openOutputStream(target, "w")!!.use { output ->
                            val buffer = ByteArray(BUFFER)
                            var sinceUpdate = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                doneBytes += read
                                sinceUpdate += read
                                if (sinceUpdate >= PROGRESS_STEP) {
                                    sinceUpdate = 0
                                    _task.value = FileTask(label, (doneBytes / totalBytes).coerceIn(0f, 1f))
                                }
                            }
                        }
                    }
                    resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                }.isSuccess
                if (ok) copied++ else runCatching { resolver.delete(target, null, null) }
            }
        } finally {
            _task.value = null
        }
        if (copied > 0) forget(relativePath)
        copied
    }

    private fun mimeFor(item: MediaFile): String? {
        val prefix = if (item.type == MediaType.VIDEO) "video/" else "audio/"
        item.mimeType?.takeIf { it.startsWith(prefix) }?.let { return it }
        val ext = item.displayName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.takeIf { it.startsWith(prefix) }
    }

    @Suppress("DEPRECATION")
    private fun dataPath(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

    private fun moveLegacy(uri: Uri, relativePath: String): Boolean {
        val path = dataPath(uri) ?: return false
        val source = File(path)
        val target = File(Environment.getExternalStorageDirectory(), relativePath).apply { mkdirs() }.resolve(source.name)
        if (target.exists() || !source.renameTo(target)) return false
        MediaScannerConnection.scanFile(context, arrayOf(source.absolutePath, target.absolutePath), null, null)
        return true
    }

    @Suppress("DEPRECATION")
    private fun baseDir(base: FolderBase): File = Environment.getExternalStoragePublicDirectory(base.directory)

    private fun remember(folder: CreatedFolder) {
        val updated = (_created.value.filterNot { it.relativePath == folder.relativePath } + folder)
        save(updated)
    }

    private fun forget(relativePath: String) {
        save(_created.value.filterNot { it.relativePath == relativePath })
    }

    private fun save(list: List<CreatedFolder>) {
        _created.value = list
        prefs.edit().putStringSet(KEY, list.map { "${it.relativePath}|${it.name}" }.toSet()).apply()
    }

    private fun load(): List<CreatedFolder> =
        prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNull { entry ->
            val path = entry.substringBefore('|')
            val name = entry.substringAfter('|', "")
            // Drop folders deleted outside the app.
            @Suppress("DEPRECATION")
            val exists = File(Environment.getExternalStorageDirectory(), path).isDirectory
            if (path.isBlank() || name.isBlank() || !exists) null else CreatedFolder(path, name)
        }.sortedBy { it.name.lowercase() }

    private companion object {
        const val KEY = "folders"
        const val BUFFER = 256 * 1024
        const val PROGRESS_STEP = 2L * 1024 * 1024
        const val INVALID_CHARS = "\\/:*?\"<>|"
    }
}
