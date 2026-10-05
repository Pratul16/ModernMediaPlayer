package com.pratul.mmplayer.data.model

/** Domain models shared by the UI and ViewModels. Database entities never reach the UI directly. */

enum class MediaType { VIDEO, AUDIO }

data class MediaFile(
    val id: Long,
    val uri: String,
    val path: String?,
    val displayName: String,
    val title: String,
    val type: MediaType,
    val mimeType: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val folderPath: String,
    val folderName: String,
    val artist: String?,
    val album: String?,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    // Per-user state joined from playback history and favorites.
    val positionMs: Long = 0,
    val lastPlayedAt: Long? = null,
    val isFavorite: Boolean = false,
    /** Played to the end at least once. */
    val completed: Boolean = false,
) {
    /** Watch progress in 0f..1f, or 0f when never played or the duration is unknown. */
    val progress: Float
        get() = if (durationMs > 0 && positionMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

data class MediaFolder(
    val volumeName: String,
    val relativePath: String,
    val name: String,
    val itemCount: Int,
    val videoCount: Int,
    val audioCount: Int,
    val totalSizeBytes: Long,
    val lastModifiedSec: Long,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val updatedAt: Long,
)
