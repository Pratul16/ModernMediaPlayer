package com.pratul.mmplayer.media.vault

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.data.model.MediaType
import com.pratul.mmplayer.media.files.FileTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@Serializable
data class HiddenItem(
    val fileName: String,
    val displayName: String,
    val type: MediaType,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val durationMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class HiddenFolder(
    val id: String,
    val name: String,
    /** Where the files came from, e.g. "Movies/Holiday/"; they go back there when unhidden. */
    val originalPath: String,
    val hiddenAt: Long,
    val items: List<HiddenItem>,
    /** The originals' content URIs, used to finish or undo a half-done hide after a restart. */
    val originalUris: List<String> = emptyList(),
    /** False while hiding is half-done (copied, originals not yet deleted). */
    val complete: Boolean = true,
)

/**
 * Hidden folders. Hiding moves a folder's files into this app's private storage, which no other
 * app, gallery, file manager or computer connection can see or open; Android's media index forgets
 * them too. They are listed and played only inside this app. Unhiding puts them back.
 *
 * Note: Android deletes an app's private storage when the app is uninstalled.
 */
class Vault(private val context: Context) {

    private val root = File(context.filesDir, "vault").apply { mkdirs() }
    private val indexFile = File(root, "index.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private val _folders = MutableStateFlow(load())
    val folders: StateFlow<List<HiddenFolder>> = _folders.asStateFlow()

    private val _task = MutableStateFlow<FileTask?>(null)
    val task: StateFlow<FileTask?> = _task.asStateFlow()

    init {
        // A hide interrupted by the app being closed: if any original is still there, undo the
        // copy (nothing was lost); if the originals are gone, the copies are the only ones left,
        // so keep them and finish the hide.
        val partial = _folders.value.filterNot { it.complete }
        if (partial.isNotEmpty()) {
            val kept = _folders.value.mapNotNull { folder ->
                when {
                    folder.complete -> folder
                    folder.originalUris.any(::stillExists) -> {
                        File(root, folder.id).deleteRecursively()
                        null
                    }
                    else -> folder.copy(complete = true)
                }
            }
            save(kept)
        }
    }

    private fun stillExists(uri: String): Boolean = runCatching {
        context.contentResolver.query(Uri.parse(uri), arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    fun fileOf(folder: HiddenFolder, item: HiddenItem): File = File(File(root, folder.id), item.fileName)

    /** Bytes free in private storage minus what [items] need; negative means not enough room. */
    fun spaceAfter(items: List<MediaFile>): Long = root.usableSpace - items.sumOf { it.sizeBytes } - SPACE_MARGIN

    /**
     * Step 1 of hiding: copies [items] into private storage. Returns the pending folder, or null
     * if copying failed (nothing is left behind). The originals are untouched until
     * [completeHide]; call [rollback] if they couldn't be deleted.
     */
    suspend fun copyIn(name: String, originalPath: String, items: List<MediaFile>): HiddenFolder? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val dir = File(root, id).apply { mkdirs() }
            val total = items.sumOf { it.sizeBytes.coerceAtLeast(1) }.toFloat()
            var done = 0L
            val label = "Hiding $name"
            _task.value = FileTask(label, 0f)
            try {
                val hidden = items.mapIndexed { index, media ->
                    // One sub-folder per file keeps the real name (players show it) without clashes.
                    val fileName = "$index/${media.displayName.replace('/', '_')}"
                    val input = context.contentResolver.openInputStream(Uri.parse(media.uri)) ?: error("Can't read ${media.displayName}")
                    input.use { stream ->
                        File(dir, fileName).apply { parentFile?.mkdirs() }.outputStream().use { out ->
                            copy(stream, out) { n ->
                                done += n
                                _task.value = FileTask(label, (done / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                    HiddenItem(fileName, media.displayName, media.type, media.mimeType, media.sizeBytes, media.durationMs, media.width, media.height)
                }
                val folder = HiddenFolder(id, name, originalPath, System.currentTimeMillis(), hidden, items.map { it.uri }, complete = false)
                save(_folders.value + folder)
                folder
            } catch (e: Exception) {
                dir.deleteRecursively()
                null
            } finally {
                _task.value = null
            }
        }
    }

    /** Step 2 of hiding, after the originals were deleted. */
    suspend fun completeHide(folder: HiddenFolder) = mutex.withLock {
        save(_folders.value.map { if (it.id == folder.id) it.copy(complete = true) else it })
    }

    /** Undo [copyIn] when the originals couldn't be removed. */
    suspend fun rollback(folder: HiddenFolder) = mutex.withLock {
        withContext(Dispatchers.IO) { File(root, folder.id).deleteRecursively() }
        save(_folders.value.filterNot { it.id == folder.id })
    }

    /**
     * Puts a hidden folder back into shared storage (its original place when Android allows it,
     * otherwise Movies/<name> or Music/<name>). Returns how many files were restored; the folder
     * stays hidden with whatever couldn't be restored.
     */
    suspend fun unhide(folder: HiddenFolder): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            val total = folder.items.sumOf { it.sizeBytes.coerceAtLeast(1) }.toFloat()
            var done = 0L
            val label = "Unhiding ${folder.name}"
            _task.value = FileTask(label, 0f)
            val left = mutableListOf<HiddenItem>()
            try {
                for (item in folder.items) {
                    val source = fileOf(folder, item)
                    val restored = restoreOne(item, source, folder) { n ->
                        done += n
                        _task.value = FileTask(label, (done / total).coerceIn(0f, 1f))
                    }
                    if (restored) source.delete() else left += item
                }
            } finally {
                _task.value = null
            }
            if (left.isEmpty()) {
                File(root, folder.id).deleteRecursively()
                save(_folders.value.filterNot { it.id == folder.id })
            } else {
                save(_folders.value.map { if (it.id == folder.id) it.copy(items = left) else it })
            }
            folder.items.size - left.size
        }
    }

    /** Permanently deletes a hidden folder. */
    suspend fun delete(folder: HiddenFolder) = mutex.withLock {
        withContext(Dispatchers.IO) { File(root, folder.id).deleteRecursively() }
        save(_folders.value.filterNot { it.id == folder.id })
    }

    private fun restoreOne(item: HiddenItem, source: File, folder: HiddenFolder, onBytes: (Int) -> Unit): Boolean {
        if (!source.exists()) return true // Already gone; nothing to restore.
        val resolver = context.contentResolver
        val collection = when (item.type) {
            MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            MediaType.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val fallback = (if (item.type == MediaType.VIDEO) "Movies/" else "Music/") + folder.name.replace('/', '_') + "/"
        for (path in listOf(folder.originalPath, fallback).distinct()) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
                put(MediaStore.MediaColumns.RELATIVE_PATH, path)
                item.mimeType?.let { put(MediaStore.MediaColumns.MIME_TYPE, it) }
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val target = runCatching { resolver.insert(collection, values) }.getOrNull() ?: continue
            val ok = runCatching {
                source.inputStream().use { input -> resolver.openOutputStream(target, "w")!!.use { copy(input, it, onBytes) } }
                resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }.isSuccess
            if (ok) return true
            runCatching { resolver.delete(target, null, null) }
        }
        return false
    }

    private fun copy(input: InputStream, output: OutputStream, onBytes: (Int) -> Unit) {
        val buffer = ByteArray(256 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            onBytes(read)
        }
    }

    private fun load(): List<HiddenFolder> =
        runCatching { json.decodeFromString<List<HiddenFolder>>(indexFile.readText()) }.getOrDefault(emptyList())

    private fun save(list: List<HiddenFolder>) {
        _folders.value = list
        // Write-then-rename so a crash never leaves a half-written index.
        val tmp = File(root, "index.json.tmp")
        tmp.writeText(json.encodeToString(list))
        tmp.renameTo(indexFile)
    }

    private companion object {
        const val SPACE_MARGIN = 200L * 1024 * 1024
    }
}
