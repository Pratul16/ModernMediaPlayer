@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package com.pratul.mmplayer.player.subtitles

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import com.pratul.mmplayer.player.session.QueueEntry
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** A subtitle file the user can pick: found next to the video or chosen manually. */
data class SubtitleSource(val uri: Uri, val name: String, val language: String?)

/** Parsed cues of one subtitle file, with a fast lookup by playback time. */
class SubtitleTrack(val source: SubtitleSource, cues: List<CuesWithTiming>) {
    private val starts: LongArray
    private val ends: LongArray
    private val texts: List<List<Cue>>

    init {
        val sorted = cues.sortedBy { it.startTimeUs }
        starts = LongArray(sorted.size) { sorted[it].startTimeUs }
        // Formats without durations (cue lasts until the next one) get the next start as their end.
        ends = LongArray(sorted.size) { i ->
            val c = sorted[i]
            if (c.durationUs != C.TIME_UNSET) c.endTimeUs else sorted.getOrNull(i + 1)?.startTimeUs ?: Long.MAX_VALUE
        }
        texts = sorted.map { it.cues }
    }

    val isEmpty: Boolean get() = starts.isEmpty()

    /** All cues showing at [timeUs]. */
    fun cuesAt(timeUs: Long): List<Cue> {
        if (starts.isEmpty() || timeUs < starts[0]) return emptyList()
        // Last cue starting at or before timeUs, then walk back over overlapping ones.
        var lo = 0
        var hi = starts.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= timeUs) lo = mid else hi = mid - 1
        }
        val result = ArrayList<Cue>(2)
        var i = lo
        while (i >= 0 && lo - i < MAX_OVERLAP_LOOKBACK) {
            if (timeUs < ends[i]) result.addAll(0, texts[i])
            i--
        }
        return result
    }

    private companion object {
        const val MAX_OVERLAP_LOOKBACK = 8
    }
}

object ExternalSubtitles {

    private val EXTENSIONS = setOf("srt", "vtt", "ass", "ssa")
    private val LANGUAGE_WORDS = mapOf(
        "english" to "en", "eng" to "en", "hindi" to "hi", "hin" to "hi", "tamil" to "ta", "telugu" to "te",
        "bengali" to "bn", "marathi" to "mr", "malayalam" to "ml", "kannada" to "kn", "spanish" to "es",
        "french" to "fr", "german" to "de", "italian" to "it", "portuguese" to "pt", "russian" to "ru",
        "arabic" to "ar", "japanese" to "ja", "korean" to "ko", "chinese" to "zh",
    )

    /**
     * Subtitle files next to [video]: same base name first ("Movie.srt", "Movie.en.srt"); if the
     * folder holds just this one video, any subtitle file in it.
     *
     * Android 12+ indexes subtitles in MediaStore and lets apps with video access read them; older
     * versions fall back to listing the folder directly.
     */
    fun find(context: Context, video: QueueEntry, videosInFolder: Int): List<SubtitleSource> {
        val all = queryMediaStore(context, video) ?: listFolder(video)
        val base = video.title.substringBeforeLast('.').lowercase(Locale.ROOT)
        val matching = all.filter { it.name.lowercase(Locale.ROOT).startsWith(base) }
        val chosen = if (matching.isNotEmpty() || videosInFolder > 1) matching else all
        return chosen.sortedBy { it.name.length }
    }

    private fun queryMediaStore(context: Context, video: QueueEntry): List<SubtitleSource>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val volume = video.volumeName ?: return null
        val folder = video.folderPath ?: return null
        val collection = MediaStore.Files.getContentUri(volume)
        val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME)
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_SUBTITLE.toString(), folder)
        return runCatching {
            context.contentResolver.query(collection, projection, selection, args, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val name = c.getString(1) ?: continue
                        if (name.substringAfterLast('.').lowercase(Locale.ROOT) !in EXTENSIONS) continue
                        add(SubtitleSource(ContentUris.withAppendedId(collection, c.getLong(0)), name, languageOf(name)))
                    }
                }
            }
        }.getOrNull()
    }

    private fun listFolder(video: QueueEntry): List<SubtitleSource> {
        val dir = video.path?.let { File(it).parentFile } ?: return emptyList()
        return runCatching {
            dir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in EXTENSIONS }
                ?.map { SubtitleSource(Uri.fromFile(it), it.name, languageOf(it.name)) }
                .orEmpty()
        }.getOrDefault(emptyList())
    }

    fun describe(context: Context, uri: Uri): SubtitleSource {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "Subtitles"
        return SubtitleSource(uri, name, languageOf(name))
    }

    /** "Movie.en.srt" -> "en", "Movie.Hindi.srt" -> "hi". */
    fun languageOf(fileName: String): String? {
        val parts = fileName.lowercase(Locale.ROOT).split('.', '_', '-', ' ')
        if (parts.size < 3) return null
        val tag = parts[parts.size - 2]
        LANGUAGE_WORDS[tag]?.let { return it }
        return tag.takeIf { it.length in 2..3 && it.all(Char::isLetter) && Locale.forLanguageTag(it).displayLanguage != it }
    }

    /**
     * Reads and parses a subtitle file. [encoding] blank = detect: UTF-8/UTF-16 when valid,
     * otherwise Windows-1252 (covers most Western subtitle files).
     */
    fun load(context: Context, source: SubtitleSource, encoding: String): SubtitleTrack? {
        val bytes = runCatching {
            context.contentResolver.openInputStream(source.uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        val text = decode(bytes, encoding)
        val mime = when (source.name.substringAfterLast('.').lowercase(Locale.ROOT)) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val format = Format.Builder().setSampleMimeType(mime).build()
        val factory = DefaultSubtitleParserFactory()
        if (!factory.supportsFormat(format)) return null
        val cues = ArrayList<CuesWithTiming>()
        return runCatching {
            factory.create(format).parse(text.toByteArray(Charsets.UTF_8), SubtitleParser.OutputOptions.allCues()) { cues.add(it) }
            SubtitleTrack(source, cues)
        }.getOrNull()?.takeUnless { it.isEmpty }
    }

    private fun decode(bytes: ByteArray, encoding: String): String {
        if (encoding.isNotBlank()) {
            return runCatching { String(bytes, Charset.forName(encoding)) }.getOrElse { String(bytes, Charsets.UTF_8) }
        }
        when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> return String(bytes, Charsets.UTF_16LE).drop(1)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> return String(bytes, Charsets.UTF_16BE).drop(1)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }
}
