package com.pratul.mmplayer.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// Type-safe navigation routes. The video player runs in its own Activity (Phase 3) so it can own
// orientation, immersive mode and picture-in-picture without affecting the library UI.

@Serializable data object HomeRoute
@Serializable data object VideosRoute
@Serializable data object AudioRoute
@Serializable data object FoldersRoute
@Serializable data object PlaylistsRoute
@Serializable data object SearchRoute
@Serializable data object SettingsRoute
@Serializable data object HiddenFoldersRoute
@Serializable data class SettingsCategoryRoute(val id: String)

enum class TopLevelDestination(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(HomeRoute, "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    VIDEOS(VideosRoute, "Videos", Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary),
    AUDIO(AudioRoute, "Audio", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    FOLDERS(FoldersRoute, "Folders", Icons.Rounded.Folder, Icons.Outlined.Folder),
    PLAYLISTS(PlaylistsRoute, "Playlists", Icons.AutoMirrored.Rounded.QueueMusic, Icons.AutoMirrored.Outlined.QueueMusic),
}

@Serializable data class FolderDetailRoute(val volume: String, val path: String, val name: String)

fun com.pratul.mmplayer.data.model.MediaFolder.toRoute() = FolderDetailRoute(volumeName, relativePath, name)

@Serializable data class PlaylistDetailRoute(val id: Long, val name: String)
