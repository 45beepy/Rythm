package com.example.rythm

import android.net.Uri
import android.provider.MediaStore

// A simple data class to hold info about one Album
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artworkUri: Uri?
) {
    companion object {
        // This is the "blueprint" for querying albums
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST
        )
    }
}