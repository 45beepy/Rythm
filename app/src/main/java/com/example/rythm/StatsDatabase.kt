package com.example.rythm

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// 1. ADDED PlaybackHistory::class and BUMPED version to 2
@Database(entities = [SongStat::class, PlaybackHistory::class], version = 2)
abstract class StatsDatabase : RoomDatabase() {

    abstract fun songStatDao(): SongStatDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao // 2. ADDED the new DAO

    companion object {
        @Volatile
        private var INSTANCE: StatsDatabase? = null

        fun getDatabase(context: Context): StatsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StatsDatabase::class.java,
                    "stats_database"
                )
                    // 3. ADDED this. Wipes the DB on schema change.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}