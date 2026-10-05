package com.pratul.mmplayer.ui.settings

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.preferences.core.Preferences
import com.pratul.mmplayer.data.settings.AppSettings
import com.pratul.mmplayer.data.settings.AspectRatioMode
import com.pratul.mmplayer.data.settings.DecoderMode
import com.pratul.mmplayer.data.settings.LibraryViewMode
import com.pratul.mmplayer.data.settings.OrientationMode
import com.pratul.mmplayer.data.settings.ResumeMode
import com.pratul.mmplayer.data.settings.SettingKeys
import com.pratul.mmplayer.data.settings.SortOrder
import com.pratul.mmplayer.data.settings.SubtitleColor
import com.pratul.mmplayer.data.settings.ThemeMode
import com.pratul.mmplayer.player.ui.label

data class Choice<T>(val value: T, val label: String)

enum class SettingAction { CLEAR_HISTORY }

/** One row on a settings page. [comingIn] marks an option whose feature ships in a later phase (shown disabled). */
sealed interface SettingItem {
    val title: String
    val summary: String?
    val comingIn: String?
    val enabledWhen: (AppSettings) -> Boolean
}

data class ToggleItem(
    override val title: String,
    override val summary: String?,
    val key: Preferences.Key<Boolean>,
    val value: (AppSettings) -> Boolean,
    override val comingIn: String? = null,
    override val enabledWhen: (AppSettings) -> Boolean = { true },
) : SettingItem

data class ChoiceItem<T>(
    override val title: String,
    val key: Preferences.Key<T>,
    val options: List<Choice<T>>,
    val value: (AppSettings) -> T,
    override val summary: String? = null,
    override val comingIn: String? = null,
    override val enabledWhen: (AppSettings) -> Boolean = { true },
) : SettingItem

data class SliderItem(
    override val title: String,
    val key: Preferences.Key<Int>,
    val range: IntRange,
    val step: Int,
    val value: (AppSettings) -> Int,
    val format: (Int) -> String,
    override val summary: String? = null,
    override val comingIn: String? = null,
    override val enabledWhen: (AppSettings) -> Boolean = { true },
) : SettingItem

data class ActionItem(
    override val title: String,
    override val summary: String?,
    val action: SettingAction,
    override val comingIn: String? = null,
    override val enabledWhen: (AppSettings) -> Boolean = { true },
) : SettingItem

enum class CategoryExtra { CODECS, ABOUT, THEMES, SECURITY }

data class SettingCategory(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val items: List<SettingItem>,
    val extra: CategoryExtra? = null,
)

private inline fun <reified E : Enum<E>> enumChoices(label: (E) -> String): List<Choice<String>> =
    enumValues<E>().map { Choice(it.name, label(it)) }

private val LANGUAGES = listOf(
    Choice("", "Device default"),
    Choice("en", "English"), Choice("hi", "Hindi"), Choice("ta", "Tamil"), Choice("te", "Telugu"),
    Choice("bn", "Bengali"), Choice("mr", "Marathi"), Choice("ml", "Malayalam"), Choice("kn", "Kannada"),
    Choice("gu", "Gujarati"), Choice("pa", "Punjabi"), Choice("ur", "Urdu"), Choice("es", "Spanish"),
    Choice("fr", "French"), Choice("de", "German"), Choice("it", "Italian"), Choice("pt", "Portuguese"),
    Choice("ru", "Russian"), Choice("ar", "Arabic"), Choice("ja", "Japanese"), Choice("ko", "Korean"),
    Choice("zh", "Chinese"),
)

private val ENCODINGS = listOf(
    Choice("", "Automatic"),
    Choice("UTF-8", "Unicode (UTF-8)"),
    Choice("windows-1252", "Western (Windows-1252)"),
    Choice("windows-1250", "Central European (Windows-1250)"),
    Choice("windows-1251", "Cyrillic (Windows-1251)"),
    Choice("windows-1253", "Greek (Windows-1253)"),
    Choice("windows-1254", "Turkish (Windows-1254)"),
    Choice("windows-1256", "Arabic (Windows-1256)"),
    Choice("ISO-8859-8", "Hebrew (ISO-8859-8)"),
    Choice("GBK", "Chinese Simplified (GBK)"),
    Choice("Big5", "Chinese Traditional (Big5)"),
    Choice("Shift_JIS", "Japanese (Shift_JIS)"),
    Choice("EUC-KR", "Korean (EUC-KR)"),
    Choice("TIS-620", "Thai (TIS-620)"),
)

private val SPEEDS = (1..12).map { it * 0.25f }.map { Choice(it, "${it}×") }

private const val PHASE_2 = "a later update"
private const val PHASE_4 = "Phase 4"
private const val PHASE_5 = "Phase 5"
private const val PHASE_7 = "Phase 7"

object SettingsCatalog {

    val categories: List<SettingCategory> = listOf(
        SettingCategory(
            id = "decoder",
            title = "Decoders & codecs",
            description = "Hardware, FFmpeg software decoding and the VLC engine",
            icon = Icons.Rounded.Memory,
            extra = CategoryExtra.CODECS,
            items = listOf(
                ChoiceItem(
                    title = "Decoder",
                    key = SettingKeys.DECODER_MODE,
                    options = listOf(
                        Choice(DecoderMode.HARDWARE.name, "HW — device decoders only"),
                        Choice(DecoderMode.HARDWARE_PLUS.name, "HW+ — device first, FFmpeg for the rest (recommended)"),
                        Choice(DecoderMode.SOFTWARE.name, "SW — FFmpeg first"),
                    ),
                    value = { it.decoderMode.name },
                ),
                ToggleItem(
                    "Fall back to the VLC engine",
                    "Automatically switch to VLC for files the main engine can't play (WMV, RMVB, DivX, …)",
                    SettingKeys.VLC_FALLBACK, { it.vlcFallback },
                ),
                ToggleItem(
                    "Always use the VLC engine",
                    "Use VLC for every file instead of only as a fallback",
                    SettingKeys.ALWAYS_USE_VLC, { it.alwaysUseVlc },
                ),
            ),
        ),
        SettingCategory(
            id = "playback",
            title = "Playback",
            description = "Speed, resume, seeking, interruptions",
            icon = Icons.Rounded.PlayCircle,
            items = listOf(
                ChoiceItem("Default playback speed", SettingKeys.PLAYBACK_SPEED, SPEEDS, { it.defaultPlaybackSpeed }),
                ChoiceItem(
                    "Resume playback", SettingKeys.RESUME_MODE,
                    enumChoices<ResumeMode> {
                        when (it) {
                            ResumeMode.ASK -> "Ask every time"
                            ResumeMode.ALWAYS -> "Always resume"
                            ResumeMode.NEVER -> "Always start over"
                        }
                    },
                    { it.resumeMode.name },
                ),
                ToggleItem("Auto-play next", "Play the next video in the folder or playlist", SettingKeys.AUTO_PLAY_NEXT, { it.autoPlayNext }),
                ToggleItem("Fast seeking", "Jump to the nearest keyframe for instant seeks (less precise)", SettingKeys.FAST_SEEK, { it.fastSeek }),
                ToggleItem("Pause when headphones disconnect", null, SettingKeys.PAUSE_ON_HEADSET, { it.pauseOnHeadsetDisconnect }),
                ToggleItem("Pause for calls and other audio", "Respect audio focus from other apps", SettingKeys.AUDIO_FOCUS, { it.handleAudioFocus }),
                ToggleItem("Keep screen on", "While a video is playing", SettingKeys.KEEP_SCREEN_ON, { it.keepScreenOn }),
                ToggleItem("Picture-in-picture", "Keep watching in a floating window when you leave the app", SettingKeys.PIP, { it.pictureInPicture }),
                ToggleItem("Play audio in background", "Keep a video's sound playing when you leave the player", SettingKeys.BACKGROUND_AUDIO, { it.backgroundAudio }),
            ),
        ),
        SettingCategory(
            id = "audio",
            title = "Audio",
            description = "Volume boost and preferred language",
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            items = listOf(
                ToggleItem("Volume boost", "Let the volume gesture go up to 200%", SettingKeys.VOLUME_BOOST, { it.volumeBoost }),
                ChoiceItem("Preferred audio language", SettingKeys.AUDIO_LANGUAGE, LANGUAGES, { it.preferredAudioLanguage }),
            ),
        ),
        SettingCategory(
            id = "display",
            title = "Display",
            description = "Aspect ratio, rotation, full screen, controls",
            icon = Icons.Rounded.Fullscreen,
            items = listOf(
                ChoiceItem("Default aspect ratio", SettingKeys.ASPECT_RATIO, enumChoices<AspectRatioMode> { it.label }, { it.defaultAspectRatio.name }),
                ChoiceItem("Screen rotation", SettingKeys.ORIENTATION, enumChoices<OrientationMode> { it.label }, { it.orientation.name }),
                ToggleItem("Full screen", "Hide the status and navigation bars while playing", SettingKeys.IMMERSIVE, { it.immersiveMode }),
                SliderItem("Hide controls after", SettingKeys.CONTROLLER_TIMEOUT, 2..10, 1, { it.controllerTimeoutSeconds }, { "$it s" }),
                ToggleItem("Show rewind and forward buttons", null, SettingKeys.SHOW_SEEK_BUTTONS, { it.showSeekButtons }),
                ToggleItem("Remember brightness", "Reuse the brightness you last set with the gesture", SettingKeys.REMEMBER_BRIGHTNESS, { it.rememberBrightness }),
            ),
        ),
        SettingCategory(
            id = "gestures",
            title = "Gestures",
            description = "Swipe, double-tap, pinch and long-press controls",
            icon = Icons.Rounded.TouchApp,
            items = buildList {
                add(ToggleItem("Enable gestures", "Turn all player gestures on or off", SettingKeys.GESTURES, { it.gesturesEnabled }))
                val whenOn: (AppSettings) -> Boolean = { it.gesturesEnabled }
                add(ToggleItem("Swipe left/right to seek", null, SettingKeys.SEEK_GESTURE, { it.seekGesture }, enabledWhen = whenOn))
                add(ToggleItem("Swipe on right side for volume", null, SettingKeys.VOLUME_GESTURE, { it.volumeGesture }, enabledWhen = whenOn))
                add(ToggleItem("Swipe on left side for brightness", null, SettingKeys.BRIGHTNESS_GESTURE, { it.brightnessGesture }, enabledWhen = whenOn))
                add(ToggleItem("Double-tap sides to seek", null, SettingKeys.DOUBLE_TAP_SEEK, { it.doubleTapSeek }, enabledWhen = whenOn))
                add(ToggleItem("Double-tap center to play/pause", null, SettingKeys.DOUBLE_TAP_PLAY_PAUSE, { it.doubleTapPlayPause }, enabledWhen = whenOn))
                add(ToggleItem("Pinch to zoom", null, SettingKeys.PINCH_ZOOM, { it.pinchToZoom }, enabledWhen = whenOn))
                add(ToggleItem("Long-press for fast playback", null, SettingKeys.LONG_PRESS_SPEED_UP, { it.longPressSpeedUp }, enabledWhen = whenOn))
                add(SliderItem("Double-tap seek amount", SettingKeys.DOUBLE_TAP_SEEK_SECONDS, 5..60, 5, { it.doubleTapSeekSeconds }, { "$it s" }))
                add(
                    ChoiceItem(
                        "Long-press speed", SettingKeys.LONG_PRESS_SPEED,
                        (5..12).map { it * 0.25f }.map { Choice(it, "${it}×") }, { it.longPressSpeed },
                        enabledWhen = { it.gesturesEnabled && it.longPressSpeedUp },
                    ),
                )
            },
        ),
        SettingCategory(
            id = "subtitles",
            title = "Subtitles",
            description = "Language, size, color and style",
            icon = Icons.Rounded.ClosedCaption,
            items = listOf(
                ToggleItem("Show subtitles by default", null, SettingKeys.SUBTITLES_ON, { it.subtitlesEnabledByDefault }),
                ToggleItem("Load subtitle files automatically", "Use Movie.srt next to Movie.mkv", SettingKeys.AUTO_LOAD_SUBTITLES, { it.autoLoadSubtitles }),
                ChoiceItem("Preferred subtitle language", SettingKeys.SUBTITLE_LANGUAGE, LANGUAGES, { it.preferredSubtitleLanguage }),
                SliderItem("Text size", SettingKeys.SUBTITLE_SIZE, 12..40, 2, { it.subtitleSizeSp }, { "$it sp" }),
                ChoiceItem(
                    "Text color", SettingKeys.SUBTITLE_COLOR,
                    enumChoices<SubtitleColor> { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                    { it.subtitleColor.name },
                ),
                ToggleItem("Background box", "Draw a dark box behind the text instead of an outline", SettingKeys.SUBTITLE_BACKGROUND, { it.subtitleBackground }),
                ToggleItem("Bold text", null, SettingKeys.SUBTITLE_BOLD, { it.subtitleBold }),
                ChoiceItem("Subtitle file encoding", SettingKeys.SUBTITLE_ENCODING, ENCODINGS, { it.subtitleEncoding }),
            ),
        ),
        SettingCategory(
            id = "library",
            title = "Library",
            description = "Sorting, layout and visible files",
            icon = Icons.Rounded.VideoLibrary,
            items = listOf(
                ChoiceItem(
                    "Default sorting", SettingKeys.SORT_ORDER,
                    enumChoices<SortOrder> {
                        when (it) {
                            SortOrder.NAME_ASC -> "Name A–Z"
                            SortOrder.NAME_DESC -> "Name Z–A"
                            SortOrder.DATE_ADDED -> "Date added"
                            SortOrder.DATE_MODIFIED -> "Date modified"
                            SortOrder.SIZE -> "Size"
                            SortOrder.DURATION -> "Duration"
                            SortOrder.RECENTLY_PLAYED -> "Recently played"
                        }
                    },
                    { it.sortOrder.name },
                ),
                ChoiceItem("Layout", SettingKeys.VIEW_MODE, enumChoices<LibraryViewMode> { if (it == LibraryViewMode.LIST) "List" else "Grid" }, { it.viewMode.name }),
                ToggleItem("Show thumbnails", null, SettingKeys.SHOW_THUMBNAILS, { it.showThumbnails }),
                ToggleItem("Show file extensions", null, SettingKeys.SHOW_EXTENSIONS, { it.showFileExtensions }),
                ToggleItem("Show hidden files and folders", "Files and folders starting with a dot", SettingKeys.SHOW_HIDDEN, { it.showHiddenFiles }, comingIn = PHASE_2),
            ),
        ),
        SettingCategory(
            id = "appearance",
            title = "Appearance",
            description = "Color themes, dark and light mode",
            icon = Icons.Rounded.Palette,
            extra = CategoryExtra.THEMES,
            items = buildList {
                add(
                    ChoiceItem(
                        "Theme", SettingKeys.THEME_MODE,
                        enumChoices<ThemeMode> {
                            when (it) {
                                ThemeMode.SYSTEM -> "System default"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            }
                        },
                        { it.themeMode.name },
                    ),
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(ToggleItem("Dynamic colors", "Match colors to your wallpaper", SettingKeys.DYNAMIC_COLOR, { it.dynamicColor }))
                }
            },
        ),
        SettingCategory(
            id = "privacy",
            title = "Privacy & security",
            description = "App lock, watch history",
            icon = Icons.Rounded.PrivacyTip,
            items = listOf(
                ToggleItem("Save watch history", "Needed for Continue watching and Recently played", SettingKeys.SAVE_HISTORY, { it.saveHistory }),
                ActionItem("Clear watch history", "Remove all resume positions and recently played items", SettingAction.CLEAR_HISTORY),
            ),
            extra = CategoryExtra.SECURITY,
        ),
        SettingCategory(
            id = "about",
            title = "About & developer",
            description = "Version, contact, feedback and licenses",
            icon = Icons.Rounded.Info,
            items = emptyList(),
            extra = CategoryExtra.ABOUT,
        ),
    )

    fun byId(id: String): SettingCategory? = categories.firstOrNull { it.id == id }
}
