package com.pratul.mmplayer.player.subtitles

import android.content.Context
import android.net.Uri
import com.pratul.mmplayer.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class SubtitleResult(
    val fileId: Int,
    val releaseName: String,
    val language: String,
    val downloadCount: Int,
)

object OpenSubtitlesClient {

    private const val BASE = "https://api.opensubtitles.com/api/v1"
    private const val HASH_CHUNK = 65536

    val isAvailable: Boolean get() = BuildConfig.OPENSUBTITLES_API_KEY.isNotBlank()

    fun search(query: String, hash: String?, languages: String): List<SubtitleResult> {
        val params = buildString {
            append("?query="); append(URLEncoder.encode(query, "UTF-8"))
            if (!hash.isNullOrBlank()) { append("&moviehash="); append(hash) }
            append("&languages="); append(URLEncoder.encode(languages, "UTF-8"))
        }
        val json = get("$BASE/subtitles$params")
        val data = json.jsonObject["data"]?.jsonArray ?: return emptyList()
        return data.mapNotNull { item ->
            val attrs = item.jsonObject["attributes"]?.jsonObject ?: return@mapNotNull null
            val release = attrs["release"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
            val lang = attrs["language"]?.jsonPrimitive?.contentOrNull ?: "?"
            val count = attrs["download_count"]?.jsonPrimitive?.intOrNull ?: 0
            val fileId = attrs["files"]?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("file_id")?.jsonPrimitive?.intOrNull
                ?: return@mapNotNull null
            SubtitleResult(fileId, release, lang, count)
        }
    }

    fun download(fileId: Int, cacheDir: File): File {
        val body = JsonObject(mapOf("file_id" to JsonPrimitive(fileId)))
        val json = post("$BASE/download", body.toString())
        val link = json.jsonObject["link"]?.jsonPrimitive?.contentOrNull
            ?: error("No download link in response")
        val dir = File(cacheDir, "subtitles").apply { mkdirs() }
        val dest = File(dir, "subtitle_$fileId.srt")
        val conn = URL(link).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.connect()
        try {
            conn.inputStream.use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
        } finally {
            conn.disconnect()
        }
        return dest
    }

    fun computeHash(context: Context, uri: Uri, fileSize: Long): String? {
        if (fileSize < HASH_CHUNK * 2) return null
        return runCatching {
            var hash = fileSize
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val chunk = ByteArray(HASH_CHUNK)
                var read = 0
                while (read < HASH_CHUNK) {
                    val n = stream.read(chunk, read, HASH_CHUNK - read)
                    if (n <= 0) return@runCatching null
                    read += n
                }
                val buf = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
                repeat(HASH_CHUNK / 8) { hash += buf.long }
            } ?: return null

            context.contentResolver.openInputStream(uri)?.use { stream ->
                val toSkip = fileSize - HASH_CHUNK
                var skipped = 0L
                while (skipped < toSkip) {
                    val n = stream.skip(toSkip - skipped)
                    if (n <= 0) break
                    skipped += n
                }
                if (skipped < toSkip) return@runCatching null
                val chunk = ByteArray(HASH_CHUNK)
                var read = 0
                while (read < HASH_CHUNK) {
                    val n = stream.read(chunk, read, HASH_CHUNK - read)
                    if (n <= 0) return@runCatching null
                    read += n
                }
                val buf = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN)
                repeat(HASH_CHUNK / 8) { hash += buf.long }
            } ?: return null

            String.format("%016x", hash)
        }.getOrNull()
    }

    private fun get(url: String): JsonElement {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Api-Key", BuildConfig.OPENSUBTITLES_API_KEY)
        conn.setRequestProperty("User-Agent", "ModernMediaPlayer v${BuildConfig.VERSION_NAME}")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            check(conn.responseCode in 200..299) {
                val body = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                parseErrorMessage(body, conn.responseCode)
            }
            return Json.parseToJsonElement(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    private fun post(url: String, body: String): JsonElement {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Api-Key", BuildConfig.OPENSUBTITLES_API_KEY)
        conn.setRequestProperty("User-Agent", "ModernMediaPlayer v${BuildConfig.VERSION_NAME}")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.doOutput = true
        conn.outputStream.write(body.toByteArray(Charsets.UTF_8))
        try {
            check(conn.responseCode in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                parseErrorMessage(errorBody, conn.responseCode)
            }
            return Json.parseToJsonElement(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    private fun parseErrorMessage(body: String, code: Int): String {
        val msg = runCatching {
            Json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return msg ?: when (code) {
            401 -> "Invalid API key"
            406 -> "Daily download limit reached"
            429 -> "Too many requests — please wait a moment"
            else -> "Server error ($code)"
        }
    }
}
