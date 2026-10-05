package com.pratul.mmplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pratul.mmplayer.ModernMediaApp
import com.pratul.mmplayer.ui.components.MiniPlayer
import com.pratul.mmplayer.ui.components.FileTaskBanner
import com.pratul.mmplayer.ui.components.LocalSystemConsent
import com.pratul.mmplayer.ui.components.rememberSystemConsent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pratul.mmplayer.ui.theme.Aurora
import com.pratul.mmplayer.ui.theme.AuroraBackground
import com.pratul.mmplayer.ui.theme.glass
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pratul.mmplayer.ui.components.PlaceholderScreen
import com.pratul.mmplayer.ui.home.HomeScreen
import com.pratul.mmplayer.ui.navigation.PlaylistDetailRoute
import com.pratul.mmplayer.ui.playlists.PlaylistDetailScreen
import com.pratul.mmplayer.ui.playlists.PlaylistsScreen
import com.pratul.mmplayer.ui.search.SearchScreen
import com.pratul.mmplayer.data.model.MediaType
import com.pratul.mmplayer.ui.folders.FolderDetailScreen
import com.pratul.mmplayer.ui.folders.FoldersScreen
import com.pratul.mmplayer.ui.library.LibraryScreen
import com.pratul.mmplayer.ui.navigation.FolderDetailRoute
import com.pratul.mmplayer.ui.navigation.toRoute
import com.pratul.mmplayer.ui.navigation.AudioRoute
import com.pratul.mmplayer.ui.navigation.FoldersRoute
import com.pratul.mmplayer.ui.navigation.HomeRoute
import com.pratul.mmplayer.ui.navigation.PlaylistsRoute
import com.pratul.mmplayer.ui.navigation.SearchRoute
import com.pratul.mmplayer.ui.navigation.SettingsCategoryRoute
import com.pratul.mmplayer.ui.navigation.SettingsRoute
import com.pratul.mmplayer.ui.navigation.HiddenFoldersRoute
import com.pratul.mmplayer.ui.vault.HiddenFoldersScreen
import com.pratul.mmplayer.ui.navigation.TopLevelDestination
import com.pratul.mmplayer.ui.navigation.VideosRoute
import com.pratul.mmplayer.ui.settings.SettingsCategoryScreen
import com.pratul.mmplayer.ui.settings.SettingsScreen
import androidx.navigation.toRoute
import kotlinx.coroutines.launch

@Composable
fun ModernMediaAppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { currentDestination.isInHierarchy(it) }
    val playbackHolder = (LocalContext.current.applicationContext as ModernMediaApp).container.playbackHolder
    val nowPlaying by playbackHolder.nowPlaying.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    CompositionLocalProvider(LocalSystemConsent provides rememberSystemConsent()) {
    AuroraBackground(Modifier.fillMaxSize()) {
        Scaffold(
            // Each screen draws its own top bar and handles the status bar inset itself.
            contentWindowInsets = WindowInsets(0),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Column(if (showBottomBar) Modifier else Modifier.navigationBarsPadding()) {
                    FileTaskBanner()
                    // Playing (or paused) media stays one tap away, including audio that kept
                    // going in the background after the player closed.
                    AnimatedVisibility(
                        visible = nowPlaying != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        nowPlaying?.let { MiniPlayer(nowPlaying = it, onClose = playbackHolder::stop, modifier = Modifier.padding(top = 6.dp)) }
                    }
                    AnimatedVisibility(
                        visible = showBottomBar,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        FloatingNavBar(
                            isSelected = { currentDestination.isInHierarchy(it) },
                            onSelect = navController::navigateToTopLevel,
                        )
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
                enterTransition = { fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f) },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.98f) },
            ) {
            composable<HomeRoute> {
                HomeScreen(
                    onOpenFolder = { navController.navigate(it.toRoute()) },
                    onOpenSearch = { navController.navigate(SearchRoute) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onOpenDestination = navController::navigateToTopLevel,
                    onShowMessage = showMessage,
                )
            }
            composable<VideosRoute> { LibraryScreen(type = MediaType.VIDEO) }
            composable<AudioRoute> { LibraryScreen(type = MediaType.AUDIO) }
            composable<FoldersRoute> {
                FoldersScreen(
                    onOpenFolder = { navController.navigate(it.toRoute()) },
                    onOpenHidden = { navController.navigate(HiddenFoldersRoute) },
                )
            }
            composable<FolderDetailRoute> { entry ->
                val route = entry.toRoute<FolderDetailRoute>()
                FolderDetailScreen(
                    volume = route.volume,
                    path = route.path,
                    name = route.name,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<HiddenFoldersRoute> { HiddenFoldersScreen(onBack = { navController.popBackStack() }) }
            composable<PlaylistsRoute> {
                PlaylistsScreen(onOpen = { id, name -> navController.navigate(PlaylistDetailRoute(id, name)) })
            }
            composable<PlaylistDetailRoute> { entry ->
                val route = entry.toRoute<PlaylistDetailRoute>()
                PlaylistDetailScreen(playlistId = route.id, name = route.name, onBack = { navController.popBackStack() })
            }
            composable<SearchRoute> {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenFolder = { navController.navigate(it.toRoute()) },
                    onOpenPlaylists = { navController.navigateToTopLevel(TopLevelDestination.PLAYLISTS) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenCategory = { navController.navigate(SettingsCategoryRoute(it)) },
                )
            }
            composable<SettingsCategoryRoute> { entry ->
                SettingsCategoryScreen(
                    categoryId = entry.toRoute<SettingsCategoryRoute>().id,
                    onBack = { navController.popBackStack() },
                    onShowMessage = showMessage,
                )
            }
        }
    }
    }
    }
}

private fun NavDestination?.isInHierarchy(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(destination.route::class) } == true

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Floating glass navigation pill. The selected destination grows into a neon gradient capsule
 * with its label; the others stay as quiet icons.
 */
@Composable
private fun FloatingNavBar(
    isSelected: (TopLevelDestination) -> Boolean,
    onSelect: (TopLevelDestination) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .glass(RoundedCornerShape(32.dp), strong = true)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopLevelDestination.entries.forEach { destination ->
                val selected = isSelected(destination)
                Row(
                    modifier = Modifier
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .then(if (selected) Modifier.background(Aurora.gradient) else Modifier)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(destination) })
                        .semantics { contentDescription = destination.label }
                        .animateContentSize(tween(220))
                        .padding(horizontal = if (selected) 18.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                        tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Text(destination.label, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}
