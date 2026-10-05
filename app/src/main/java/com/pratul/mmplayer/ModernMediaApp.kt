package com.pratul.mmplayer

import android.app.Application
import android.content.pm.ApplicationInfo
import com.pratul.mmplayer.utils.MainThreadWatchdog
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.pratul.mmplayer.di.AppContainer
import com.pratul.mmplayer.media.thumbnail.MediaThumbnailFetcher
import com.pratul.mmplayer.media.thumbnail.MediaThumbnailKeyer

class ModernMediaApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appLock.attach(this)
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) MainThreadWatchdog.start()
        // Refresh the library in the background (no-op without media permission or when nothing changed).
        container.mediaScanner.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(MediaThumbnailKeyer())
                add(MediaThumbnailFetcher.Factory())
            }
            .crossfade(true)
            .build()
}
