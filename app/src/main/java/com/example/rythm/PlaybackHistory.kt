package com.example.rythm

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val songId: Long, // The MediaStore ID
    val title: String,
    val artist: String,
    val timestamp: Long // The time this was played
)