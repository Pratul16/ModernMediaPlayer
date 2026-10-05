package com.pratul.mmplayer.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class MediaAccess {
    /** Can read all videos and audio. */
    FULL,

    /** Android 14+ "selected videos only", or only one of video/audio granted on Android 13+. */
    PARTIAL,
    NONE,
}

/**
 * Read-permission strategy by Android version:
 * - 10–12 (API 29–32): READ_EXTERNAL_STORAGE.
 * - 13 (API 33): READ_MEDIA_VIDEO + READ_MEDIA_AUDIO.
 * - 14+ (API 34+): as 13, plus READ_MEDIA_VISUAL_USER_SELECTED so a "select videos" grant
 *   is reported to the app as partial access instead of falling back to compatibility mode.
 *
 * Write access is not a permission on Android 11+: renames, moves and deletes of files the app
 * did not create go through MediaStore.createWriteRequest/createDeleteRequest, which show a
 * system consent dialog per operation (Phase 6).
 */
object MediaPermissions {

    val requestPermissions: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun currentAccess(context: Context): MediaAccess {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) MediaAccess.FULL else MediaAccess.NONE
        }
        val video = granted(Manifest.permission.READ_MEDIA_VIDEO)
        val audio = granted(Manifest.permission.READ_MEDIA_AUDIO)
        val selectedVisual = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        return when {
            video && audio -> MediaAccess.FULL
            video || audio || selectedVisual -> MediaAccess.PARTIAL
            else -> MediaAccess.NONE
        }
    }
}
