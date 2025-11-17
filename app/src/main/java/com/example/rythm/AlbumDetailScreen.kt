package com.example.rythm

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: Long,
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var albumTitle by remember { mutableStateOf("Album") }
    var albumArtist by remember { mutableStateOf("Unknown") }
    var albumArt by remember { mutableStateOf<Uri?>(null) }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    // Load album info + songs
    LaunchedEffect(albumId) {
        val resolver = context.contentResolver

        // Album info
        resolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Audio.Albums.ALBUM,
                MediaStore.Audio.Albums.ARTIST
            ),
            "${MediaStore.Audio.Albums._ID}=?",
            arrayOf(albumId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                albumTitle = cursor.getString(0) ?: "Album"
                albumArtist = cursor.getString(1) ?: "Unknown"
            }
        }

        // Album Art
        albumArt = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )

        // Load songs inside this album
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE
        )

        val loaded = mutableListOf<Song>()

        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.ALBUM_ID}=?",
            arrayOf(albumId.toString()),
            "${MediaStore.Audio.Media.TRACK} ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol)
                val artist = c.getString(artistCol)
                val duration = c.getLong(durCol)
                val mime = c.getString(mimeCol)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                loaded.add(
                    Song(
                        id = id,
                        title = title,
                        artist = artist,
                        duration = duration,
                        contentUri = contentUri,
                        albumArtUri = albumArt,
                        fileType = mime
                    )
                )
            }
        }

        songs = loaded

        // Convert to MediaItems
        mediaItems = loaded.map { song ->
            MediaItem.Builder()
                .setUri(song.contentUri)
                .setMediaId(song.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(albumTitle)
                        .setArtworkUri(albumArt)
                        .build()
                )
                .build()
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text(albumTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            // Album Art
            albumArt?.let {
                AsyncImage(
                    model = it,
                    contentDescription = "Album Art",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(Icons.Default.MusicNote),
                    error = rememberVectorPainter(Icons.Default.MusicNote)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = albumArtist,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.height(8.dp))

            LazyColumn {
                items(songs) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val index = mediaItems.indexOfFirst { it.mediaId == song.id.toString() }
                                if (index != -1) {
                                    viewModel.onSongClick(mediaItems, index)
                                }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(song.title, fontWeight = FontWeight.Bold)
                            Text(song.artist, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}
