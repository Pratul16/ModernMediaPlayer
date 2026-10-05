@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package com.pratul.mmplayer.player.engine

import android.media.MediaCodecList
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary

enum class CodecPath(val label: String) {
    HARDWARE("Device decoder"),
    FFMPEG("FFmpeg (software)"),
    VLC("VLC engine"),
}

data class CodecEntry(val name: String, val path: CodecPath)

data class CodecReport(
    val ffmpegVersion: String?,
    val video: List<CodecEntry>,
    val audio: List<CodecEntry>,
    val containers: List<String>,
)

/** Works out, for common formats, which engine on this device will decode them. Call off the main thread. */
object CodecSupport {

    private val videoFormats = listOf(
        "H.264 / AVC" to MimeTypes.VIDEO_H264,
        "H.265 / HEVC" to MimeTypes.VIDEO_H265,
        "AV1" to MimeTypes.VIDEO_AV1,
        "VP9" to MimeTypes.VIDEO_VP9,
        "VP8" to MimeTypes.VIDEO_VP8,
        "MPEG-4 Part 2 (DivX, Xvid)" to MimeTypes.VIDEO_MP4V,
        "MPEG-2" to MimeTypes.VIDEO_MPEG2,
        "H.263" to MimeTypes.VIDEO_H263,
        "Dolby Vision" to MimeTypes.VIDEO_DOLBY_VISION,
        "VC-1 / WMV" to "video/wvc1",
        "RealVideo" to "video/x-pn-realvideo",
    )

    private val audioFormats = listOf(
        "AAC" to MimeTypes.AUDIO_AAC,
        "MP3" to MimeTypes.AUDIO_MPEG,
        "Opus" to MimeTypes.AUDIO_OPUS,
        "Vorbis" to MimeTypes.AUDIO_VORBIS,
        "FLAC" to MimeTypes.AUDIO_FLAC,
        "ALAC" to MimeTypes.AUDIO_ALAC,
        "WAV / PCM" to MimeTypes.AUDIO_RAW,
        "AMR" to MimeTypes.AUDIO_AMR_NB,
        "Dolby Digital (AC-3)" to MimeTypes.AUDIO_AC3,
        "Dolby Digital Plus (E-AC-3)" to MimeTypes.AUDIO_E_AC3,
        "DTS" to MimeTypes.AUDIO_DTS,
        "Dolby TrueHD" to MimeTypes.AUDIO_TRUEHD,
        "MPEG Layer II" to MimeTypes.AUDIO_MPEG_L2,
        "WMA" to "audio/x-ms-wma",
    )

    val containers = listOf(
        "MP4 · M4V · MOV · 3GP" to "Media3",
        "MKV · WebM" to "Media3",
        "AVI · FLV · MPEG-TS · MPEG-PS" to "Media3",
        "MP3 · AAC · M4A · FLAC · WAV · OGG · OPUS · AMR" to "Media3",
        "WMV · WMA · ASF · RMVB · VOB · DivX" to "VLC",
    )

    fun report(): CodecReport {
        val deviceTypes = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filterNot { it.isEncoder }
            .flatMap { info -> info.supportedTypes.map { it.lowercase() } }
            .toSet()
        val ffmpeg = runCatching { FfmpegLibrary.isAvailable() }.getOrDefault(false)

        fun pathFor(mime: String): CodecPath = when {
            mime.lowercase() in deviceTypes -> CodecPath.HARDWARE
            ffmpeg && runCatching { FfmpegLibrary.supportsFormat(mime) }.getOrDefault(false) -> CodecPath.FFMPEG
            else -> CodecPath.VLC
        }

        return CodecReport(
            ffmpegVersion = if (ffmpeg) runCatching { FfmpegLibrary.getVersion() }.getOrNull() ?: "available" else null,
            video = videoFormats.map { (name, mime) -> CodecEntry(name, pathFor(mime)) },
            audio = audioFormats.map { (name, mime) -> CodecEntry(name, pathFor(mime)) },
            containers = containers.map { (formats, engine) -> "$formats — $engine" },
        )
    }
}
