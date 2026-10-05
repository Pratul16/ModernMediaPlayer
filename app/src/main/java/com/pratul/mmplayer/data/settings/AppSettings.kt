package com.pratul.mmplayer.data.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Accent colours and background glows; each works in dark and light mode. */
enum class ColorTheme(val label: String) { AURORA("Aurora"), GLASS("Glass"), SUNSET("Sunset"), OCEAN("Ocean"), EMERALD("Emerald") }

enum class LibraryViewMode { LIST, GRID }

enum class FolderSort { NAME_ASC, NAME_DESC, MOST_ITEMS, LARGEST, RECENT }

enum class SortOrder { NAME_ASC, NAME_DESC, DATE_ADDED, DATE_MODIFIED, SIZE, DURATION, RECENTLY_PLAYED }

enum class AspectRatioMode { FIT, FILL, CROP, RATIO_16_9, RATIO_4_3, ORIGINAL }

/** SYSTEM follows the phone's auto-rotate setting; FOLLOW_VIDEO picks landscape/portrait from the video. */
enum class OrientationMode { FOLLOW_VIDEO, AUTO, LANDSCAPE, PORTRAIT, SYSTEM }

/**
 * HARDWARE: platform (MediaCodec) decoders only.
 * HARDWARE_PLUS: platform decoders first, FFmpeg software decoders for anything the phone lacks.
 * SOFTWARE: FFmpeg first (useful when a hardware decoder is buggy), platform decoders as backup.
 */
enum class DecoderMode { HARDWARE, HARDWARE_PLUS, SOFTWARE }

enum class ResumeMode { ASK, ALWAYS, NEVER }

enum class SubtitleColor(val argb: Int) {
    WHITE(0xFFFFFFFF.toInt()),
    YELLOW(0xFFFFEB3B.toInt()),
    CYAN(0xFF4DD0E1.toInt()),
    GREEN(0xFF81C784.toInt()),
}

data class AppSettings(
    // Appearance
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val colorTheme: ColorTheme = ColorTheme.AURORA,
    // First run
    val onboardingDone: Boolean = false,

    // Decoders & engine
    val decoderMode: DecoderMode = DecoderMode.HARDWARE_PLUS,
    val vlcFallback: Boolean = true,
    val alwaysUseVlc: Boolean = false,

    // Playback
    val defaultPlaybackSpeed: Float = 1f,
    val resumeMode: ResumeMode = ResumeMode.ASK,
    val autoPlayNext: Boolean = true,
    val fastSeek: Boolean = true,
    val pauseOnHeadsetDisconnect: Boolean = true,
    val handleAudioFocus: Boolean = true,
    val keepScreenOn: Boolean = true,
    val backgroundAudio: Boolean = false,
    val pictureInPicture: Boolean = true,

    // Audio
    val volumeBoost: Boolean = false,
    val preferredAudioLanguage: String = "",

    // Display
    val defaultAspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val orientation: OrientationMode = OrientationMode.FOLLOW_VIDEO,
    val immersiveMode: Boolean = true,
    val controllerTimeoutSeconds: Int = 3,
    val showSeekButtons: Boolean = true,
    val rememberBrightness: Boolean = false,
    /** Last brightness set by gesture, 0f..1f; negative means "use system brightness". */
    val lastBrightness: Float = -1f,

    // Gestures
    val gesturesEnabled: Boolean = true,
    val seekGesture: Boolean = true,
    val volumeGesture: Boolean = true,
    val brightnessGesture: Boolean = true,
    val doubleTapSeek: Boolean = true,
    val doubleTapPlayPause: Boolean = true,
    val pinchToZoom: Boolean = true,
    val longPressSpeedUp: Boolean = true,
    val doubleTapSeekSeconds: Int = 10,
    val longPressSpeed: Float = 2f,

    // Subtitles
    val subtitlesEnabledByDefault: Boolean = true,
    val autoLoadSubtitles: Boolean = true,
    val preferredSubtitleLanguage: String = "",
    val subtitleSizeSp: Int = 20,
    val subtitleColor: SubtitleColor = SubtitleColor.WHITE,
    val subtitleBackground: Boolean = false,
    val subtitleBold: Boolean = false,
    /** Charset name for subtitle files, blank = detect automatically. */
    val subtitleEncoding: String = "",

    // Library
    val sortOrder: SortOrder = SortOrder.NAME_ASC,
    val folderSort: FolderSort = FolderSort.NAME_ASC,
    val viewMode: LibraryViewMode = LibraryViewMode.LIST,
    val showHiddenFiles: Boolean = false,
    val showThumbnails: Boolean = true,
    val showFileExtensions: Boolean = true,

    // Privacy
    val saveHistory: Boolean = true,
)
