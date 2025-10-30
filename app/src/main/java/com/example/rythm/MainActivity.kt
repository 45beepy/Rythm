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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.media3.common.Player
import androidx.compose.material3.IconButton
import androidx.media3.session.MediaSession.Callback

// These are for our listener
import androidx.media3.common.MediaMetadata
import androidx.compose.runtime.derivedStateOf

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
        Column(modifier = Modifier.fillMaxSize()) {
            // The SongList gets a 'weight' of 1f. This is a "nook and corner"
            // that means "fill all available space" (pushing the bar to the bottom).
            SongList(
                modifier = Modifier.weight(1f), // <-- NEW
                songList = songList,
                onSongClick = { song ->
                    mediaController?.let { controller ->
                        val mediaItem = MediaItem.fromUri(song.contentUri)
                        controller.setMediaItem(mediaItem)
                        controller.prepare()
                        controller.play()
                    }
                }
            )

            // Our new composable for the mini-player.
            NowPlayingBar(mediaController = mediaController)
                }
            }

        // --- END OF UPDATED CODE ---
    }


// This composable just holds the LazyColumn.
@Composable
fun SongList(
    modifier: Modifier = Modifier, // <-- NEW
    songList: List<Song>,
    onSongClick: (Song) -> Unit // <-- NEW: Accept a function to call
) {
    LazyColumn(modifier = modifier) {
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
@Composable
fun NowPlayingBar(
    mediaController: MediaController?
) {
    // 1. --- OUR UI's STATE ---
    // We use 'remember' to hold the state. When these change,
    // the UI will automatically "recompose" (update).
    var currentSong: MediaMetadata? by remember { mutableStateOf(null) }
    var isPlaying: Boolean by remember { mutableStateOf(false) }

    // We use 'derivedStateOf' for the icon. This is a smart way
    // to automatically pick the right icon based on the 'isPlaying' state.
    val playPauseIcon by remember {
        derivedStateOf {
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
        }
    }

    // 2. --- THE "LISTENER" ---
    // 'DisposableEffect' is the key. It "listens" as long as the
    // 'mediaController' is on the screen.
    DisposableEffect(mediaController) {
        // If there's no controller, do nothing.
        if (mediaController == null) {
            // Set to default state when controller is null
            currentSong = null
            isPlaying = false
            return@DisposableEffect onDispose {}
        }

        // Create our "ear" (the listener).
        val listener = object : Player.Listener {
            // This is called when the song changes
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                super.onMediaMetadataChanged(mediaMetadata)
                currentSong = mediaMetadata // Update our state
            }

            // This is called when the player plays or pauses
            override fun onIsPlayingChanged(playing: Boolean) {
                super.onIsPlayingChanged(playing)
                isPlaying = playing // Update our state
            }
        }

        // Attach our "ear" to the controller.
        mediaController?.addListener(listener)

        // Also, update the state to the *current* values right now
        currentSong = mediaController?.mediaMetadata
        isPlaying = mediaController?.isPlaying == true

        // 'onDispose' is the cleanup. When the UI goes away,
        // we remove our "ear" to prevent memory leaks.
        onDispose {
            mediaController?.removeListener(listener)
        }
    }


    // 3. --- THE UI ---
    // If there's no song, show nothing (an empty box).
    if (currentSong == null) {
        Box(modifier = Modifier.fillMaxWidth()) {}
    } else {
        // If there *is* a song, build the bar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clickable {
                    // TODO: In a future lesson, click this row
                    // to open the full player screen.
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Song Icon
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Song Icon",
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Title and Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentSong?.title.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentSong?.artist.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play/Pause Button
            IconButton(onClick = {
                if (isPlaying) {
                    mediaController?.pause()
                } else {
                    mediaController?.play()
                }
            }) {
                Icon(
                    imageVector = playPauseIcon,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Skip Button
            IconButton(onClick = {
                mediaController?.seekToNextMediaItem()
            }) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Skip Next",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}