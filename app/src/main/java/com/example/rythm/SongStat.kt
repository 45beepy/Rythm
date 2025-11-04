package com.example.rythm

import androidx.room.Entity
import androidx.room.PrimaryKey

// This data class defines a table named "song_stats"
@Entity(tableName = "song_stats")
data class SongStat(
    // We'll use the song's MediaStore ID as the Primary Key
    @PrimaryKey val id: Long,

    val title: String, // Storing title/artist is good for a "stats" screen
    val artist: String,
    val playCount: Int,
    val totalPlayTimeMs: Long // We'll store this in milliseconds
)