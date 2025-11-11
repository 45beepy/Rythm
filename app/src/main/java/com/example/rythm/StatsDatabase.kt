package com.example.rythm

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// --- STEP 1: Add PlaybackHistory to the 'entities' list ---
@Database(entities = [SongStat::class, PlaybackHistory::class], version = 1)
abstract class StatsDatabase : RoomDatabase() {

    // --- STEP 2: Add the new DAO function ---
    abstract fun songStatDao(): SongStatDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao // <-- ADD THIS LINE

    companion object {
        @Volatile
        private var INSTANCE: StatsDatabase? = null

        fun getDatabase(context: Context): StatsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StatsDatabase::class.java,
                    "stats_database"
                ).build() // Note: In a real app, we'd handle migration
                INSTANCE = instance
                instance
            }
        }
    }
}