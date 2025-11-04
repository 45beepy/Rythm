package com.example.rythm

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SongStat::class], version = 1)
abstract class StatsDatabase : RoomDatabase() {

    // This tells Room what DAOs this database has
    abstract fun songStatDao(): SongStatDao

    // This is a "singleton" pattern. It makes sure only
    // ONE instance of this database ever exists.
    companion object {
        @Volatile
        private var INSTANCE: StatsDatabase? = null

        fun getDatabase(context: Context): StatsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StatsDatabase::class.java,
                    "stats_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}