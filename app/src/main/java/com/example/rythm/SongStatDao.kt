package com.example.rythm

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SongStatDao {

    // "Upsert" is a smart command:
    // If a song with this ID already exists, UPDATE it.
    // If not, INSERT it.
    @Upsert
    suspend fun upsertStat(stat: SongStat)

    // This query gets a single song's stats by its ID
    @Query("SELECT * FROM song_stats WHERE id = :id")
    suspend fun getStatById(id: Long): SongStat?

    // This gets ALL stats, ordered by play count
    // 'Flow' means it's "live": if the data changes,
    // our UI will get the update automatically.
    @Query("SELECT * FROM song_stats ORDER BY playCount DESC")
    fun getAllStats(): Flow<List<SongStat>>
}