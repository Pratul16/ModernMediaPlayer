@file:androidx.annotation.OptIn(markerClass = [UnstableApi::class])

package com.pratul.mmplayer.player.service

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.pratul.mmplayer.ModernMediaApp
import com.pratul.mmplayer.data.database.entity.MediaEntity
import com.pratul.mmplayer.player.PlayerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground media service. Publishes the current player through a MediaLibrarySession, which gives
 * the media notification (title, previous / play-pause / next), lock-screen controls, Bluetooth and
 * headset buttons, Android Auto browsing and "Now playing" in quick settings — and keeps audio
 * playing after the player screen is closed or the phone is locked.
 */
class PlaybackService : MediaLibraryService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaLibrarySession? = null
    private val holder get() = (application as ModernMediaApp).container.playbackHolder
    private val container get() = (application as ModernMediaApp).container

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            holder.nowPlaying.collect { nowPlaying ->
                if (nowPlaying == null) {
                    shutDown()
                    return@collect
                }
                val current = session
                if (current == null) {
                    session = MediaLibrarySession.Builder(this@PlaybackService, nowPlaying.player, libraryCallback)
                        .setSessionActivity(openPlayer(nowPlaying))
                        .build()
                        .also { addSession(it) }
                } else {
                    if (current.player !== nowPlaying.player) current.player = nowPlaying.player
                    current.setSessionActivity(openPlayer(nowPlaying))
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            holder.stop()
            shutDown()
        }
    }

    private fun shutDown() {
        session?.let { removeSession(it); it.release() }
        session = null
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun openPlayer(nowPlaying: NowPlaying): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            PlayerActivity.intent(this, nowPlaying.uri, nowPlaying.mimeType),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    // --- Browse tree for Android Auto --------------------------------------------------------

    private val libraryCallback = object : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = browsable(ROOT, "Modern Media Player", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            scope.launch(Dispatchers.IO) {
                val items = browseChildren(parentId, page, pageSize)
                future.set(LibraryResult.ofItemList(items, params))
            }
            return future
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_NOT_SUPPORTED))
    }

    private suspend fun browseChildren(parentId: String, page: Int, pageSize: Int): List<MediaItem> {
        val db = container.database
        val offset = page * pageSize
        return when (parentId) {
            ROOT -> listOf(
                browsable(RECENT, "Recently Played", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                browsable(FAVORITES, "Favorites", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                browsable(PLAYLISTS, "Playlists", MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS),
                browsable(MUSIC, "Music", MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS),
                browsable(FOLDERS, "Folders", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED),
            )
            RECENT -> db.mediaDao().getRecentAudio(pageSize).drop(offset).map { it.toMediaItem() }
            FAVORITES -> db.mediaDao().getFavoriteAudio(pageSize).drop(offset).map { it.toMediaItem() }
            MUSIC -> db.mediaDao().getAudio(pageSize).drop(offset).map { it.toMediaItem() }
            PLAYLISTS -> db.playlistDao().getAll().drop(offset).take(pageSize).map { pl ->
                browsable("$PLAYLIST_PREFIX${pl.id}", pl.name, MediaMetadata.MEDIA_TYPE_PLAYLIST)
            }
            FOLDERS -> {
                val folders = db.mediaDao().getInFolder("external_primary", "%", "AUDIO")
                    .groupBy { it.folderName }
                    .keys.sorted()
                    .drop(offset).take(pageSize)
                folders.map { name -> browsable("$FOLDER_PREFIX$name", name, MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS) }
            }
            else -> when {
                parentId.startsWith(PLAYLIST_PREFIX) -> {
                    val playlistId = parentId.removePrefix(PLAYLIST_PREFIX).toLongOrNull() ?: return emptyList()
                    db.playlistDao().getItemMedia(playlistId).drop(offset).take(pageSize).map { it.toMediaItem() }
                }
                parentId.startsWith(FOLDER_PREFIX) -> {
                    val folderName = parentId.removePrefix(FOLDER_PREFIX)
                    db.mediaDao().getAudio(500).filter { it.folderName == folderName }
                        .drop(offset).take(pageSize).map { it.toMediaItem() }
                }
                else -> emptyList()
            }
        }
    }

    private fun browsable(id: String, title: String, mediaType: Int): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .build(),
            )
            .build()

    private fun MediaEntity.toMediaItem(): MediaItem {
        val artUri = if (mediaType.name == "AUDIO" && albumId != null) {
            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
        } else null
        return MediaItem.Builder()
            .setMediaId(uri)
            .setUri(Uri.parse(uri))
            .setMimeType(mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title.ifBlank { displayName })
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .apply { if (artUri != null) setArtworkUri(artUri) }
                    .build(),
            )
            .build()
    }

    companion object {
        private const val ROOT = "root"
        private const val RECENT = "recent"
        private const val FAVORITES = "favorites"
        private const val PLAYLISTS = "playlists"
        private const val MUSIC = "music"
        private const val FOLDERS = "folders"
        private const val PLAYLIST_PREFIX = "playlist:"
        private const val FOLDER_PREFIX = "folder:"
    }
}
