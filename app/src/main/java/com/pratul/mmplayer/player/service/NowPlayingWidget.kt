package com.pratul.mmplayer.player.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pratul.mmplayer.ModernMediaApp
import com.pratul.mmplayer.R
import com.pratul.mmplayer.player.PlayerActivity

class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val holder = (context.applicationContext as ModernMediaApp).container.playbackHolder
        val np = holder.nowPlaying.value
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, np)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val holder = (context.applicationContext as ModernMediaApp).container.playbackHolder
        when (intent.action) {
            ACTION_PREV -> holder.nowPlaying.value?.player?.let {
                if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0)
            }
            ACTION_TOGGLE -> holder.nowPlaying.value?.player?.let {
                it.playWhenReady = !it.playWhenReady
            }
            ACTION_NEXT -> holder.nowPlaying.value?.player?.let {
                if (it.hasNextMediaItem()) it.seekToNextMediaItem()
            }
        }
        if (intent.action in setOf(ACTION_PREV, ACTION_TOGGLE, ACTION_NEXT)) {
            refresh(context)
        }
    }

    companion object {
        private const val ACTION_PREV = "com.pratul.mmplayer.WIDGET_PREV"
        private const val ACTION_TOGGLE = "com.pratul.mmplayer.WIDGET_TOGGLE"
        private const val ACTION_NEXT = "com.pratul.mmplayer.WIDGET_NEXT"

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
            if (ids.isEmpty()) return
            val holder = (context.applicationContext as ModernMediaApp).container.playbackHolder
            val np = holder.nowPlaying.value
            val views = buildViews(context, np)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun buildViews(context: Context, np: NowPlaying?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
            views.setTextViewText(R.id.widget_title, np?.title ?: "Not playing")
            val playing = np?.player?.playWhenReady == true
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            )
            val openIntent = if (np != null) {
                PendingIntent.getActivity(
                    context, 0,
                    PlayerActivity.intent(context, np.uri, np.mimeType),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            } else {
                PendingIntent.getActivity(
                    context, 0,
                    context.packageManager.getLaunchIntentForPackage(context.packageName),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            views.setOnClickPendingIntent(R.id.widget_title, openIntent)

            views.setOnClickPendingIntent(R.id.widget_prev, actionIntent(context, ACTION_PREV, 1))
            views.setOnClickPendingIntent(R.id.widget_play_pause, actionIntent(context, ACTION_TOGGLE, 2))
            views.setOnClickPendingIntent(R.id.widget_next, actionIntent(context, ACTION_NEXT, 3))
            return views
        }

        private fun actionIntent(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, code,
                Intent(context, NowPlayingWidget::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}
