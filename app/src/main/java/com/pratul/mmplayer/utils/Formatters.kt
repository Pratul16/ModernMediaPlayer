package com.pratul.mmplayer.utils

import android.text.format.DateUtils
import java.util.Locale

/** 77,852,000 ms -> "1:17:32"; 95,000 ms -> "1:35". */
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

/** Decimal units, matching how Android's Files app reports sizes. */
fun formatFileSize(bytes: Long): String {
    if (bytes < 1000) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1000.0
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) {
        value /= 1000
        unit++
    }
    val pattern = if (value < 10) "%.1f %s" else "%.0f %s"
    return String.format(Locale.getDefault(), pattern, value, units[unit])
}

/** Short quality label from the frame size, e.g. "1080p" or "4K"; null when unknown. */
fun resolutionLabel(width: Int, height: Int): String? {
    val shortSide = minOf(width, height)
    if (shortSide <= 0) return null
    return when {
        shortSide >= 2160 -> "4K"
        shortSide >= 1440 -> "1440p"
        shortSide >= 1080 -> "1080p"
        shortSide >= 720 -> "720p"
        shortSide >= 480 -> "480p"
        else -> "${shortSide}p"
    }
}

/** "5 minutes ago", "Yesterday", ... in the user's locale. */
fun formatRelativeTime(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String =
    DateUtils.getRelativeTimeSpanString(epochMs, nowMs, DateUtils.MINUTE_IN_MILLIS).toString()
