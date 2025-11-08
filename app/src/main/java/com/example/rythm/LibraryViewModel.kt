package com.example.rythm

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    // --- Album State ---
    private val _albums = mutableStateOf<List<Album>>(emptyList())
    val albums: State<List<Album>> = _albums

    // We can add state for artists, genres, etc. here later

    init {
        loadAlbums()
    }

    // This function queries the MediaStore for all albums
    private fun loadAlbums() {
        viewModelScope.launch(Dispatchers.IO) {
            val albumList = mutableListOf<Album>()

            val contentResolver = getApplication<Application>().contentResolver
            val cursor = contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                Album.projection,
                null,
                null,
                "${MediaStore.Audio.Albums.ALBUM} ASC"
            )

            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
                val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
                val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)

                while (c.moveToNext()) {
                    val id = c.getLong(idColumn)
                    val title = c.getString(titleColumn)
                    val artist = c.getString(artistColumn)

                    // Get the artwork URI
                    val artworkUri: Uri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        id
                    )

                    albumList.add(
                        Album(
                            id = id,
                            title = title,
                            artist = artist,
                            artworkUri = artworkUri
                        )
                    )
                }
            }
            _albums.value = albumList
        }
    }
}