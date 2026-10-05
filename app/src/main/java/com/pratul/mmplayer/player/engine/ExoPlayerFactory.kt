@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package com.pratul.mmplayer.player.engine

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.pratul.mmplayer.data.settings.AppSettings
import com.pratul.mmplayer.data.settings.DecoderMode

/**
 * Builds the primary engine. Media3 handles MP4/M4V/MOV/3GP, MKV/WebM, AVI, FLV, MPEG-TS/PS, Ogg,
 * FLAC, WAV, AMR, MP3 and AAC containers itself. The bundled FFmpeg extension adds software decoders
 * (AC-3, E-AC-3, DTS, TrueHD, ALAC, ... and experimental video) that DefaultRenderersFactory loads
 * automatically whenever the extension renderer mode is not OFF.
 */
object ExoPlayerFactory {

    fun create(context: Context, settings: AppSettings): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(
                when (settings.decoderMode) {
                    DecoderMode.HARDWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                    DecoderMode.HARDWARE_PLUS -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    DecoderMode.SOFTWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                },
            )
            // If the first decoder fails to initialise, try the next one instead of failing playback.
            .setEnableDecoderFallback(true)

        val extractorsFactory = DefaultExtractorsFactory()
            // Allows seeking in files without a seek index (raw MP3/AAC/AMR, some AVI/TS rips).
            .setConstantBitrateSeekingEnabled(true)
            .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
            // Picks up H.264 streams in transport streams that lack proper access-unit delimiters.
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage(settings.preferredAudioLanguage.ifBlank { null })
                    .setPreferredTextLanguage(settings.preferredSubtitleLanguage.ifBlank { null })
                    .setSelectUndeterminedTextLanguage(true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !settings.subtitlesEnabledByDefault)
                    // Prefer a track the device can actually decode over a "better" one it cannot.
                    .setExceedRendererCapabilitiesIfNecessary(true),
            )
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val seekMs = settings.doubleTapSeekSeconds * 1000L
        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context, extractorsFactory))
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, settings.handleAudioFocus)
            .setHandleAudioBecomingNoisy(settings.pauseOnHeadsetDisconnect)
            .setSeekBackIncrementMs(seekMs)
            .setSeekForwardIncrementMs(seekMs)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .apply {
                setSeekParameters(if (settings.fastSeek) SeekParameters.CLOSEST_SYNC else SeekParameters.EXACT)
                playbackParameters = PlaybackParameters(settings.defaultPlaybackSpeed)
            }
    }
}
