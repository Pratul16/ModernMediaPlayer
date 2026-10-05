package com.pratul.mmplayer.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pratul.mmplayer.data.database.dao.FavoriteDao
import com.pratul.mmplayer.data.database.dao.MediaDao
import com.pratul.mmplayer.data.database.dao.PlaybackHistoryDao
import com.pratul.mmplayer.data.database.dao.PlaylistDao
import com.pratul.mmplayer.data.database.entity.FavoriteEntity
import com.pratul.mmplayer.data.database.entity.MediaEntity
import com.pratul.mmplayer.data.database.entity.PlaybackHistoryEntity
import com.pratul.mmplayer.data.database.entity.PlaylistEntity
import com.pratul.mmplayer.data.database.entity.PlaylistItemEntity

@Database(
    entities = [
        MediaEntity::class,
        PlaybackHistoryEntity::class,
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ModernMediaDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        fun create(context: Context): ModernMediaDatabase =
            Room.databaseBuilder(context, ModernMediaDatabase::class.java, "modern_media_player.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: per-video memory of subtitle choice, aspect ratio and zoom. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playback_history ADD COLUMN subtitleTrack TEXT")
                db.execSQL("ALTER TABLE playback_history ADD COLUMN aspectRatio TEXT")
                db.execSQL("ALTER TABLE playback_history ADD COLUMN zoom REAL NOT NULL DEFAULT 1")
            }
        }
    }
}
