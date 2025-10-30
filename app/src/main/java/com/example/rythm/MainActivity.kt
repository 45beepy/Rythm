package com.example.rythm // Make sure this matches your package name!
import java.util.Locale
import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.example.rythm.ui.theme.RythmTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import android.content.ComponentName
import com.google.common.util.concurrent.ListenableFuture
import androidx.core.content.ContextCompat

// This is our data model.
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val contentUri: Uri
)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class) // Required for the Accompanist library
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen() // Installs a simple splash screen

        setContent {
            RythmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionGatedContent()
                }
            }
        }
    }
}

// This composable manages the permission request.
@SuppressLint("InlinedApi")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionGatedContent() {
    val audioPermissionState = rememberPermissionState(
        permission = Manifest.permission.READ_MEDIA_AUDIO
    )

    LaunchedEffect(Unit) {
        audioPermissionState.launchPermissionRequest()
    }

    when {
        audioPermissionState.status.isGranted -> {
            // Permission is granted! Call SongLoader.
            SongLoader()
        }

        audioPermissionState.status.shouldShowRationale -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { audioPermissionState.launchPermissionRequest() }) {
                    Text("We need permission to load music. Click to try again.")
                }
            }
        }

        !audioPermissionState.status.isGranted && !audioPermissionState.status.shouldShowRationale -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Waiting for permission...")
            }
        }
    }
}


// --- THIS IS THE FUNCTION YOU WERE LOOKING FOR ---
// This is the composable we show *after* permission is granted.
// Its job is to find all the audio files and then display them.
@Composable
fun SongLoader() {
    val context = LocalContext.current
    val contentResolver: ContentResolver = context.contentResolver

    var songList by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // --- NEW CODE: MediaController Setup ---
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // This is the "token" that identifies our PlaybackService.
    val sessionToken = remember {
        SessionToken(context, ComponentName(context, PlaybackService::class.java))
    }

    // 'DisposableEffect' is a special composable for code that needs
    // to be "cleaned up" when the UI goes away.
    // We use it to connect/disconnect from the service.
    DisposableEffect(lifecycleOwner, sessionToken) {
        val controllerFuture: ListenableFuture<MediaController> =
            MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener(
            {
                // This block runs when the controller is ready.
                mediaController = controllerFuture.get()
            },
            ContextCompat.getMainExecutor(context)
        )

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                controllerFuture.addListener({ mediaController = controllerFuture.get() }, ContextCompat.getMainExecutor(context))
            }
            if (event == Lifecycle.Event.ON_STOP) {
                MediaController.releaseFuture(controllerFuture)
                mediaController = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // 'onDispose' is the "cleanup" block. It runs when the
        // composable is removed from the screen.
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            MediaController.releaseFuture(controllerFuture)
            mediaController = null
        }
    }
    // --- END OF NEW CODE ---


    // This LaunchedEffect for loading songs is THE SAME AS BEFORE.
    LaunchedEffect(Unit) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
        val loadedSongs = mutableListOf<Song>()

        val cursor = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )

        cursor?.use { c ->
            val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST) // <-- Make sure you fixed the bug!
            val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val title = c.getString(titleColumn)
                val artist = c.getString(artistColumn) // <-- Make sure this uses artistColumn
                val duration = c.getLong(durationColumn)

                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                loadedSongs.add(Song(id, title, artist, duration, contentUri))
            }
        }

        songList = loadedSongs
        isLoading = false
    }

    // This UI part is slightly different.
    // We now pass the 'mediaController' down to the 'SongList'.
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        // --- UPDATED CODE ---
        // Pass the controller and the song list to our UI.
        SongList(
            songList = songList,
            onSongClick = { song ->
                // This is the "play" command!
                mediaController?.let { controller ->
                    // 1. Create a MediaItem that the player can understand
                    val mediaItem = MediaItem.fromUri(song.contentUri)
                    // 2. Set the item and command it to play
                    controller.setMediaItem(mediaItem)
                    controller.prepare()
                    controller.play()
                }
            }
        )
        // --- END OF UPDATED CODE ---
    }
}

// This composable just holds the LazyColumn.
@Composable
fun SongList(
    songList: List<Song>,
    onSongClick: (Song) -> Unit // <-- NEW: Accept a function to call
) {
    LazyColumn {
        items(songList) { song ->
            SongListItem(
                song = song,
                onClick = { onSongClick(song) } // <-- NEW: Pass the song to the click handler
            )
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit // <-- NEW: Accept a simple click handler
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick) // <-- NEW: Use the passed-in click handler
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ... the rest of your SongListItem (Icons, Text, etc.) is THE SAME ...
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = "Song Icon",
            modifier = Modifier.size(40.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        val durationMinutes = (song.duration / 1000) / 60
        val durationSeconds = (song.duration / 1000) % 60
        val durationString = String.format(Locale.getDefault(), "%d:%02d", durationMinutes, durationSeconds)

        Text(
            text = durationString,
            style = MaterialTheme.typography.bodySmall
        )
    }
}