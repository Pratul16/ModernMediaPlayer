package com.pratul.mmplayer.di

import android.content.Context
import android.util.Log
import com.pratul.mmplayer.data.database.ModernMediaDatabase
import com.pratul.mmplayer.data.repository.MediaRepository
import com.pratul.mmplayer.data.repository.PlaylistRepository
import com.pratul.mmplayer.data.settings.SettingsRepository
import com.pratul.mmplayer.media.scanner.MediaScanner
import com.pratul.mmplayer.player.service.PlaybackHolder
import com.pratul.mmplayer.media.files.FileOperations
import com.pratul.mmplayer.security.AppLock
import com.pratul.mmplayer.media.vault.Vault
import com.pratul.mmplayer.billing.TipJar
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container, created once by [com.pratul.mmplayer.ModernMediaApp].
 * Lazily builds singletons so app start stays fast.
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    /**
     * Lives as long as the process; for work that must outlive any one screen (e.g. scanning).
     * A failure in one background job is logged instead of closing the whole app.
     */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> Log.e("ModernMediaPlayer", "Background task failed", e) },
    )

    val database: ModernMediaDatabase by lazy { ModernMediaDatabase.create(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val mediaRepository: MediaRepository by lazy {
        MediaRepository(database.mediaDao(), database.playbackHistoryDao(), database.favoriteDao())
    }

    val playlistRepository: PlaylistRepository by lazy { PlaylistRepository(database.playlistDao()) }

    val mediaScanner: MediaScanner by lazy { MediaScanner(appContext, database.mediaDao(), appScope) }

    val playbackHolder: PlaybackHolder by lazy { PlaybackHolder(appContext) }

    val fileOperations: FileOperations by lazy { FileOperations(appContext) }

    val appLock: AppLock = AppLock(appContext)

    val vault: Vault by lazy { Vault(appContext) }

    /** "Buy me a coffee" tips through Google Play (About & developer page). */
    val tipJar: TipJar by lazy { TipJar(appContext) }
}
