package com.pratul.mmplayer.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * User preferences, persisted with DataStore. Preferences are small key/value pairs read as a
 * whole, which DataStore handles better than a Room table (atomic writes, no schema migrations).
 * Enums are stored by name, so reordering enum constants never corrupts saved values.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toAppSettings() }
        .distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    suspend fun <E : Enum<E>> set(key: Preferences.Key<String>, value: E) = set(key, value.name)

    private fun Preferences.toAppSettings(): AppSettings {
        val d = AppSettings()
        val k = SettingKeys
        return AppSettings(
            themeMode = enumOr(k.THEME_MODE, d.themeMode),
            dynamicColor = this[k.DYNAMIC_COLOR] ?: d.dynamicColor,
            colorTheme = enumOr(k.COLOR_THEME, d.colorTheme),
            onboardingDone = this[k.ONBOARDING_DONE] ?: d.onboardingDone,
            decoderMode = enumOr(k.DECODER_MODE, d.decoderMode),
            vlcFallback = this[k.VLC_FALLBACK] ?: d.vlcFallback,
            alwaysUseVlc = this[k.ALWAYS_USE_VLC] ?: d.alwaysUseVlc,
            defaultPlaybackSpeed = this[k.PLAYBACK_SPEED] ?: d.defaultPlaybackSpeed,
            resumeMode = enumOr(k.RESUME_MODE, d.resumeMode),
            autoPlayNext = this[k.AUTO_PLAY_NEXT] ?: d.autoPlayNext,
            fastSeek = this[k.FAST_SEEK] ?: d.fastSeek,
            pauseOnHeadsetDisconnect = this[k.PAUSE_ON_HEADSET] ?: d.pauseOnHeadsetDisconnect,
            handleAudioFocus = this[k.AUDIO_FOCUS] ?: d.handleAudioFocus,
            keepScreenOn = this[k.KEEP_SCREEN_ON] ?: d.keepScreenOn,
            backgroundAudio = this[k.BACKGROUND_AUDIO] ?: d.backgroundAudio,
            pictureInPicture = this[k.PIP] ?: d.pictureInPicture,
            volumeBoost = this[k.VOLUME_BOOST] ?: d.volumeBoost,
            preferredAudioLanguage = this[k.AUDIO_LANGUAGE] ?: d.preferredAudioLanguage,
            defaultAspectRatio = enumOr(k.ASPECT_RATIO, d.defaultAspectRatio),
            orientation = enumOr(k.ORIENTATION, d.orientation),
            immersiveMode = this[k.IMMERSIVE] ?: d.immersiveMode,
            controllerTimeoutSeconds = this[k.CONTROLLER_TIMEOUT] ?: d.controllerTimeoutSeconds,
            showSeekButtons = this[k.SHOW_SEEK_BUTTONS] ?: d.showSeekButtons,
            rememberBrightness = this[k.REMEMBER_BRIGHTNESS] ?: d.rememberBrightness,
            lastBrightness = this[k.LAST_BRIGHTNESS] ?: d.lastBrightness,
            gesturesEnabled = this[k.GESTURES] ?: d.gesturesEnabled,
            seekGesture = this[k.SEEK_GESTURE] ?: d.seekGesture,
            volumeGesture = this[k.VOLUME_GESTURE] ?: d.volumeGesture,
            brightnessGesture = this[k.BRIGHTNESS_GESTURE] ?: d.brightnessGesture,
            doubleTapSeek = this[k.DOUBLE_TAP_SEEK] ?: d.doubleTapSeek,
            doubleTapPlayPause = this[k.DOUBLE_TAP_PLAY_PAUSE] ?: d.doubleTapPlayPause,
            pinchToZoom = this[k.PINCH_ZOOM] ?: d.pinchToZoom,
            longPressSpeedUp = this[k.LONG_PRESS_SPEED_UP] ?: d.longPressSpeedUp,
            doubleTapSeekSeconds = this[k.DOUBLE_TAP_SEEK_SECONDS] ?: d.doubleTapSeekSeconds,
            longPressSpeed = this[k.LONG_PRESS_SPEED] ?: d.longPressSpeed,
            subtitlesEnabledByDefault = this[k.SUBTITLES_ON] ?: d.subtitlesEnabledByDefault,
            autoLoadSubtitles = this[k.AUTO_LOAD_SUBTITLES] ?: d.autoLoadSubtitles,
            preferredSubtitleLanguage = this[k.SUBTITLE_LANGUAGE] ?: d.preferredSubtitleLanguage,
            subtitleSizeSp = this[k.SUBTITLE_SIZE] ?: d.subtitleSizeSp,
            subtitleColor = enumOr(k.SUBTITLE_COLOR, d.subtitleColor),
            subtitleBackground = this[k.SUBTITLE_BACKGROUND] ?: d.subtitleBackground,
            subtitleBold = this[k.SUBTITLE_BOLD] ?: d.subtitleBold,
            subtitleEncoding = this[k.SUBTITLE_ENCODING] ?: d.subtitleEncoding,
            sortOrder = enumOr(k.SORT_ORDER, d.sortOrder),
            folderSort = enumOr(k.FOLDER_SORT, d.folderSort),
            viewMode = enumOr(k.VIEW_MODE, d.viewMode),
            showHiddenFiles = this[k.SHOW_HIDDEN] ?: d.showHiddenFiles,
            showThumbnails = this[k.SHOW_THUMBNAILS] ?: d.showThumbnails,
            showFileExtensions = this[k.SHOW_EXTENSIONS] ?: d.showFileExtensions,
            saveHistory = this[k.SAVE_HISTORY] ?: d.saveHistory,
        )
    }

    private inline fun <reified T : Enum<T>> Preferences.enumOr(key: Preferences.Key<String>, default: T): T =
        this[key]?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
}

object SettingKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val COLOR_THEME = stringPreferencesKey("color_theme")
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

    val DECODER_MODE = stringPreferencesKey("decoder_mode_v2")
    val VLC_FALLBACK = booleanPreferencesKey("vlc_fallback")
    val ALWAYS_USE_VLC = booleanPreferencesKey("always_use_vlc")

    val PLAYBACK_SPEED = floatPreferencesKey("default_playback_speed")
    val RESUME_MODE = stringPreferencesKey("resume_mode")
    val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
    val FAST_SEEK = booleanPreferencesKey("fast_seek")
    val PAUSE_ON_HEADSET = booleanPreferencesKey("pause_on_headset_disconnect")
    val AUDIO_FOCUS = booleanPreferencesKey("handle_audio_focus")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val BACKGROUND_AUDIO = booleanPreferencesKey("background_audio")
    val PIP = booleanPreferencesKey("picture_in_picture")

    val VOLUME_BOOST = booleanPreferencesKey("volume_boost")
    val AUDIO_LANGUAGE = stringPreferencesKey("preferred_audio_language")

    val ASPECT_RATIO = stringPreferencesKey("default_aspect_ratio")
    val ORIENTATION = stringPreferencesKey("orientation_v2")
    val IMMERSIVE = booleanPreferencesKey("immersive_mode")
    val CONTROLLER_TIMEOUT = intPreferencesKey("controller_timeout_seconds")
    val SHOW_SEEK_BUTTONS = booleanPreferencesKey("show_seek_buttons")
    val REMEMBER_BRIGHTNESS = booleanPreferencesKey("remember_brightness")
    val LAST_BRIGHTNESS = floatPreferencesKey("last_brightness")

    val GESTURES = booleanPreferencesKey("gestures_enabled")
    val SEEK_GESTURE = booleanPreferencesKey("seek_gesture")
    val VOLUME_GESTURE = booleanPreferencesKey("volume_gesture")
    val BRIGHTNESS_GESTURE = booleanPreferencesKey("brightness_gesture")
    val DOUBLE_TAP_SEEK = booleanPreferencesKey("double_tap_seek")
    val DOUBLE_TAP_PLAY_PAUSE = booleanPreferencesKey("double_tap_play_pause")
    val PINCH_ZOOM = booleanPreferencesKey("pinch_zoom")
    val LONG_PRESS_SPEED_UP = booleanPreferencesKey("long_press_speed_up")
    val DOUBLE_TAP_SEEK_SECONDS = intPreferencesKey("double_tap_seek_seconds")
    val LONG_PRESS_SPEED = floatPreferencesKey("long_press_speed")

    val SUBTITLES_ON = booleanPreferencesKey("subtitles_enabled_by_default")
    val AUTO_LOAD_SUBTITLES = booleanPreferencesKey("auto_load_subtitles")
    val SUBTITLE_LANGUAGE = stringPreferencesKey("preferred_subtitle_language")
    val SUBTITLE_SIZE = intPreferencesKey("subtitle_size_sp")
    val SUBTITLE_COLOR = stringPreferencesKey("subtitle_color")
    val SUBTITLE_BACKGROUND = booleanPreferencesKey("subtitle_background")
    val SUBTITLE_BOLD = booleanPreferencesKey("subtitle_bold")
    val SUBTITLE_ENCODING = stringPreferencesKey("subtitle_encoding")

    val SORT_ORDER = stringPreferencesKey("sort_order")
    val FOLDER_SORT = stringPreferencesKey("folder_sort")
    val VIEW_MODE = stringPreferencesKey("view_mode")
    val SHOW_HIDDEN = booleanPreferencesKey("show_hidden_files")
    val SHOW_THUMBNAILS = booleanPreferencesKey("show_thumbnails")
    val SHOW_EXTENSIONS = booleanPreferencesKey("show_file_extensions")

    val SAVE_HISTORY = booleanPreferencesKey("save_history")
}
