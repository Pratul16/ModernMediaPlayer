package com.pratul.mmplayer.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.pratul.mmplayer.ui.theme.Aurora
import com.pratul.mmplayer.ui.theme.GlassIconButton
import com.pratul.mmplayer.ui.theme.GlowButton
import com.pratul.mmplayer.ui.theme.GradientIconBadge
import com.pratul.mmplayer.ui.theme.GradientText
import com.pratul.mmplayer.ui.theme.NeonProgress
import com.pratul.mmplayer.ui.theme.glass
import com.pratul.mmplayer.ui.theme.neonBorder
import com.pratul.mmplayer.utils.formatDuration
import com.pratul.mmplayer.utils.resolutionLabel
import androidx.compose.ui.platform.LocalContext
import com.pratul.mmplayer.player.PlayerActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratul.mmplayer.R
import com.pratul.mmplayer.data.model.MediaFile
import com.pratul.mmplayer.ui.components.EmptyState
import com.pratul.mmplayer.ui.components.CreateFolderDialog
import com.pratul.mmplayer.ui.components.MediaThumbnailImage
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import com.pratul.mmplayer.ui.components.MediaActionsSheet
import com.pratul.mmplayer.ui.components.playMedia
import com.pratul.mmplayer.data.model.MediaFolder
import com.pratul.mmplayer.ui.components.FolderListItem
import com.pratul.mmplayer.ui.components.MediaCard
import com.pratul.mmplayer.ui.components.MediaListItem
import com.pratul.mmplayer.ui.components.PlaylistCard
import com.pratul.mmplayer.ui.components.SectionHeader
import com.pratul.mmplayer.ui.navigation.TopLevelDestination
import com.pratul.mmplayer.ui.modernMediaViewModel
import com.pratul.mmplayer.ui.permissions.MediaAccessState
import com.pratul.mmplayer.ui.permissions.rememberMediaAccessState
import com.pratul.mmplayer.utils.MediaAccess

@Composable
fun HomeScreen(
    onOpenFolder: (MediaFolder) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDestination: (TopLevelDestination) -> Unit,
    onShowMessage: (String) -> Unit,
    viewModel: HomeViewModel = modernMediaViewModel { HomeViewModel(it.mediaRepository, it.playlistRepository, it.mediaScanner) },
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val audio by viewModel.audio.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val mediaAccess = rememberMediaAccessState(onAccessChanged = { if (it != MediaAccess.NONE) viewModel.scan(onShowMessage) })

    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.VIDEOS) }
    var sheetMedia by remember { mutableStateOf<MediaFile?>(null) }
    val context = LocalContext.current
    var creatingFolder by remember { mutableStateOf(false) }
    val openFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) context.startActivity(PlayerActivity.intent(context, uri, context.contentResolver.getType(uri)))
    }
    val onPlay: (MediaFile) -> Unit = { context.playMedia(it) }
    val onScan: () -> Unit = { viewModel.scan { result -> onShowMessage(result) } }

    // The hero spotlights what to watch next: an unfinished video, else a new one.
    val hero = videos.continueWatching.firstOrNull() ?: videos.newVideos.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header") {
            HomeHeader(onOpenSearch = onOpenSearch, onOpenSettings = onOpenSettings, onRescan = onScan)
        }
        item(key = "search") { SearchPill(onClick = onOpenSearch) }
        if (mediaAccess.access != MediaAccess.FULL) {
            item(key = "permission") { PermissionCard(mediaAccess) }
        }
        if (selectedTab == HomeTab.VIDEOS && hero != null) {
            item(key = "hero") {
                HeroCard(
                    media = hero,
                    label = if (hero.positionMs > 0) "CONTINUE WATCHING" else "NEW ON YOUR PHONE",
                    onPlay = { onPlay(hero) },
                    onLongPress = { sheetMedia = hero },
                )
            }
        }
        item(key = "tabs") { HomeTabs(selected = selectedTab, onSelect = { selectedTab = it }) }
        item(key = "quick-actions") {
            QuickActions(
                onOpenFile = { openFileLauncher.launch(arrayOf("video/*", "audio/*")) },
                onScan = onScan,
                onOpenFolders = { onOpenDestination(TopLevelDestination.FOLDERS) },
                onFavorites = { onOpenDestination(TopLevelDestination.PLAYLISTS) },
                onPlaylists = { onOpenDestination(TopLevelDestination.PLAYLISTS) },
                onCreateFolder = { creatingFolder = true },
            )
        }
        when (selectedTab) {
            HomeTab.VIDEOS -> videoSections(
                state = videos,
                skip = hero,
                onPlay = onPlay,
                onLongPress = { sheetMedia = it },
                onSeeAll = { onOpenDestination(TopLevelDestination.VIDEOS) },
            )
            HomeTab.AUDIO -> audioSections(
                state = audio,
                onPlay = onPlay,
                onLongPress = { sheetMedia = it },
                onSeeAll = { onOpenDestination(TopLevelDestination.AUDIO) },
                onOpenPlaylists = { onOpenDestination(TopLevelDestination.PLAYLISTS) },
            )
            HomeTab.FOLDERS -> folderSections(
                state = folders,
                onBrowse = { onOpenDestination(TopLevelDestination.FOLDERS) },
                onOpenFolder = onOpenFolder,
            )
        }
    }

    sheetMedia?.let { media ->
        MediaActionsSheet(items = listOf(media), onDismiss = { sheetMedia = null })
    }
    if (creatingFolder) {
        CreateFolderDialog(
            onDismiss = { creatingFolder = false },
            onCreated = { folder ->
                creatingFolder = false
                onShowMessage("Created ${folder.name}. Use ⋮ on any file → Move to folder.")
            },
        )
    }
}

/** Greeting header: logo, time-of-day greeting, gradient app name and two glass actions. */
@Composable
private fun HomeHeader(onOpenSearch: () -> Unit, onOpenSettings: () -> Unit, onRescan: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val greeting = remember {
        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Late-night session"
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_mm_logo),
            contentDescription = null,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GradientText("Modern Media", style = MaterialTheme.typography.headlineSmall)
        }
        Box {
            GlassIconButton(Icons.Rounded.Tune, contentDescription = "More options", onClick = { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rescan media") },
                    leadingIcon = { Icon(Icons.Rounded.Sync, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onRescan()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onOpenSettings()
                    },
                )
            }
        }
    }
}

/** Search entry point styled as a glass pill; opens the search screen. */
@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .glass(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClickLabel = "Search", onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(
            "Search videos, music, folders…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Large spotlight card with the thumbnail, progress and a glowing play button. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCard(media: MediaFile, label: String, onPlay: () -> Unit, onLongPress: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(24.dp, shape, ambientColor = Aurora.accent, spotColor = Aurora.accent)
            .clip(shape)
            .neonBorder(shape, 1.dp)
            .combinedClickable(onClick = onPlay, onLongClick = onLongPress, onLongClickLabel = "More options")
            .aspectRatio(16f / 10f),
    ) {
        MediaThumbnailImage(
            media = media,
            showDuration = false,
            showProgress = false,
            cornerRadius = 0.dp,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .glass(RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = media.displayName.substringBeforeLast('.'),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (media.positionMs > 0 && media.durationMs > 0) {
                        "${formatDuration(media.durationMs - media.positionMs)} left · ${media.folderName}"
                    } else {
                        listOfNotNull(formatDuration(media.durationMs), resolutionLabel(media.width, media.height), media.folderName).joinToString(" · ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                )
                if (media.progress > 0f) {
                    Spacer(Modifier.height(10.dp))
                    NeonProgress(media.progress, height = 4.dp)
                }
            }
            Spacer(Modifier.width(16.dp))
            GlassIconButton(
                icon = Icons.Rounded.PlayArrow,
                contentDescription = "Play ${media.displayName}",
                onClick = onPlay,
                size = 60.dp,
                highlighted = true,
                modifier = Modifier.shadow(20.dp, CircleShape, ambientColor = Aurora.accentEnd, spotColor = Aurora.accentEnd),
            )
        }
    }
}

/** Videos / Audio / Folders switch: glass track with a sliding neon capsule. */
@Composable
private fun HomeTabs(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val tabs = listOf(
        Triple(HomeTab.VIDEOS, "Videos", Icons.Rounded.VideoLibrary),
        Triple(HomeTab.AUDIO, "Music", Icons.Rounded.GraphicEq),
        Triple(HomeTab.FOLDERS, "Folders", Icons.Rounded.Folder),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .glass(RoundedCornerShape(50))
            .padding(5.dp),
    ) {
        tabs.forEach { (tab, label, icon) ->
            val isSelected = tab == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(50))
                    .then(if (isSelected) Modifier.background(Aurora.gradient) else Modifier)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) }),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = color, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PermissionCard(state: MediaAccessState) {
    val partial = state.access == MediaAccess.PARTIAL
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .glass(shape, strong = true)
            .neonBorder(shape, 1.dp)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GradientIconBadge(Icons.Rounded.PhotoLibrary, size = 48.dp)
        Text(
            text = if (partial) "Unlock your full library" else "Bring your media in",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = if (partial) {
                "Modern Media Player can see only some of your media. Allow all videos and music to see everything."
            } else {
                "Allow access so Modern Media Player can find the videos and music on this phone. Nothing is uploaded — everything stays on your device."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        if (state.permanentlyDenied) {
            GlowButton("Open settings", onClick = state::openAppSettings, icon = Icons.Rounded.Settings)
        } else {
            GlowButton(if (partial) "Manage access" else "Allow access", onClick = state::request, icon = Icons.Rounded.LockOpen)
        }
    }
}

/** Glass tiles with gradient icon badges. */
@Composable
private fun QuickActions(
    onOpenFile: () -> Unit,
    onScan: () -> Unit,
    onOpenFolders: () -> Unit,
    onFavorites: () -> Unit,
    onPlaylists: () -> Unit,
    onCreateFolder: () -> Unit,
) {
    val actions = listOf(
        QuickAction("Open file", Icons.Rounded.FileOpen, onOpenFile),
        QuickAction("Scan", Icons.Rounded.Radar, onScan),
        QuickAction("Folders", Icons.Rounded.FolderOpen, onOpenFolders),
        QuickAction("Favorites", Icons.Rounded.Favorite, onFavorites),
        QuickAction("Playlists", Icons.AutoMirrored.Rounded.QueueMusic, onPlaylists),
        QuickAction("New folder", Icons.Rounded.CreateNewFolder, onCreateFolder),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(actions, key = { it.label }) { action ->
            Column(
                modifier = Modifier
                    .width(92.dp)
                    .glass(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button, onClick = action.onClick)
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GradientIconBadge(action.icon, size = 40.dp, shape = RoundedCornerShape(13.dp))
                Text(action.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

private data class QuickAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

private fun LazyListScope.mediaShelf(
    key: String,
    title: String,
    items: List<MediaFile>,
    onPlay: (MediaFile) -> Unit,
    onLongPress: (MediaFile) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "$key-header") { SectionHeader(title) }
    item(key = "$key-row") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items, key = { it.id }) { media ->
                MediaCard(media = media, onClick = { onPlay(media) }, onLongClick = { onLongPress(media) }, onMore = { onLongPress(media) })
            }
        }
    }
}

private fun LazyListScope.videoSections(
    state: VideoSectionState,
    skip: MediaFile?,
    onPlay: (MediaFile) -> Unit,
    onLongPress: (MediaFile) -> Unit,
    onSeeAll: () -> Unit,
) {
    if (!state.isLoading && state.totalCount == 0) {
        item(key = "videos-empty") {
            EmptyState(
                icon = Icons.Rounded.VideoLibrary,
                title = "No videos yet",
                message = "Videos on this device will appear here after media access is granted and the library is scanned.",
            )
        }
        return
    }
    mediaShelf("continue", "Continue watching", state.continueWatching.filterNot { it.id == skip?.id }, onPlay, onLongPress)
    if (state.series.isNotEmpty()) {
        item(key = "series-header") { SectionHeader("Your series") }
        item(key = "series-row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.series, key = { it.name }) { series ->
                    SeriesCard(series = series, onClick = { onPlay(series.next) }, onLongClick = { onLongPress(series.next) })
                }
            }
        }
    }
    mediaShelf("recent-videos", "Recently played", state.recentlyPlayed, onPlay, onLongPress)
    mediaShelf("fav-videos", "Favorites", state.favorites, onPlay, onLongPress)
    mediaShelf("downloads", "Downloads", state.downloads, onPlay, onLongPress)
    mediaShelf("new-videos", "New on your phone", state.newVideos.filterNot { it.id == skip?.id }, onPlay, onLongPress)
    if (state.recentlyAdded.isNotEmpty()) {
        item(key = "all-videos-header") {
            SectionHeader("All videos · ${state.totalCount}", actionLabel = "See all", onAction = onSeeAll)
        }
        items(state.recentlyAdded, key = { "video-${it.id}" }) { media ->
            MediaListItem(media = media, onClick = { onPlay(media) }, onLongClick = { onLongPress(media) }, onMore = { onLongPress(media) })
        }
    }
}

private fun LazyListScope.audioSections(
    state: AudioSectionState,
    onPlay: (MediaFile) -> Unit,
    onLongPress: (MediaFile) -> Unit,
    onSeeAll: () -> Unit,
    onOpenPlaylists: () -> Unit,
) {
    if (state.playlists.isNotEmpty()) {
        item(key = "playlists-header") {
            SectionHeader("Playlists", actionLabel = "See all", onAction = onOpenPlaylists)
        }
        item(key = "playlists-row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.playlists, key = { it.id }) { playlist ->
                    PlaylistCard(playlist = playlist, onClick = onOpenPlaylists)
                }
            }
        }
    }
    if (!state.isLoading && state.totalCount == 0) {
        item(key = "audio-empty") {
            EmptyState(
                icon = Icons.Rounded.LibraryMusic,
                title = "No music yet",
                message = "Songs and other audio files will appear here after the library is scanned.",
            )
        }
        return
    }
    mediaShelf("recent-audio", "Recently played", state.recentlyPlayed, onPlay, onLongPress)
    mediaShelf("fav-audio", "Favorites", state.favorites, onPlay, onLongPress)
    if (state.recentlyAdded.isNotEmpty()) {
        item(key = "all-audio-header") {
            SectionHeader("All audio · ${state.totalCount}", actionLabel = "See all", onAction = onSeeAll)
        }
        items(state.recentlyAdded, key = { "audio-${it.id}" }) { media ->
            MediaListItem(media = media, onClick = { onPlay(media) }, onLongClick = { onLongPress(media) }, onMore = { onLongPress(media) })
        }
    }
}

private fun LazyListScope.folderSections(
    state: FolderSectionState,
    onBrowse: () -> Unit,
    onOpenFolder: (MediaFolder) -> Unit,
) {
    if (!state.isLoading && state.folders.isEmpty()) {
        item(key = "folders-empty") {
            EmptyState(
                icon = Icons.Rounded.Folder,
                title = "No media folders yet",
                message = "Folders that contain videos or audio will be listed here.",
                actionLabel = "Browse storage",
                onAction = onBrowse,
            )
        }
        return
    }
    item(key = "folders-header") {
        SectionHeader("Folders · ${state.folders.size}", actionLabel = "Browse", onAction = onBrowse)
    }
    items(state.folders.take(FOLDER_PREVIEW_LIMIT), key = { "${it.volumeName}/${it.relativePath}" }) { folder ->
        FolderListItem(folder = folder, onClick = { onOpenFolder(folder) })
    }
    if (state.folders.size > FOLDER_PREVIEW_LIMIT) {
        item(key = "folders-more") {
            OutlinedButton(
                onClick = onBrowse,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("Browse all folders") }
        }
    }
}

private const val FOLDER_PREVIEW_LIMIT = 10


/** A detected show: the next episode's thumbnail, with the show name and which episode is next. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesCard(series: SeriesSummary, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(196.dp)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "More options")
            .padding(4.dp),
    ) {
        MediaThumbnailImage(
            media = series.next,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
        Spacer(Modifier.height(8.dp))
        Text(series.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = "${if (series.next.positionMs > 0) "Continue" else "Next"}: ${series.nextLabel} · ${series.episodeCount} episodes",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
