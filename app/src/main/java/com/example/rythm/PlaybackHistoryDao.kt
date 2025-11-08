package com.example.rythm

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackHistoryDao {

    @Insert
    suspend fun addHistoryItem(item: PlaybackHistory)

    // Get all history, with the newest items first
    @Query("SELECT * FROM playback_history ORDER BY timestamp DESC")
    fun getFullHistory(): Flow<List<PlaybackHistory>>
}