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
import androidx.compose.material3.Slider
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.common.MediaMetadata
import androidx.compose.runtime.derivedStateOf
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongLoader() {
    val context = LocalContext.current
    val contentResolver: ContentResolver = context.contentResolver

    var songList by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // --- MediaController Setup ---
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    val sessionToken = remember {
        SessionToken(context, ComponentName(context, PlaybackService::class.java))
    }

    DisposableEffect(lifecycleOwner, sessionToken) {
        val controllerFuture: ListenableFuture<MediaController> =
            MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener(
            {
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

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            MediaController.releaseFuture(controllerFuture)
            mediaController = null
        }
    }

    // --- (LESSON 6) CONVERT List<Song> to List<MediaItem> ---
    // This includes metadata so the player can read the title/artist.
    val mediaItemsList by remember {
        derivedStateOf {
            songList.map { song ->
                MediaItem.Builder()
                    .setUri(song.contentUri)
                    .setMediaId(song.id.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .build()
                    )
                    .build()
            }
        }
    }

    // --- Load Songs from MediaStore ---
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
            val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val title = c.getString(titleColumn)
                val artist = c.getString(artistColumn)
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

    // --- UI Layer (Displaying List and Player) ---
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        // --- NEW BOTTOM SHEET LAYOUT ---
        val scope = rememberCoroutineScope()
        val scaffoldState = rememberBottomSheetScaffoldState()

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 80.dp, // This is the height of our collapsed mini-player
            sheetContent = {
                // This is the new, full "Now Playing" screen.
                // We pass it the controller and the scaffoldState
                // so it can expand/collapse itself.
                PlayerScreen(
                    mediaController = mediaController,
                    onCollapse = {
                        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                    }
                )
            }
        ) { innerPadding ->
            // This is the main content of the app (our song list)
            SongList(
                // Use the innerPadding to avoid the list going "under" the system bars
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                songList = songList,
                onSongClick = { song ->
                    mediaController?.let { controller ->
                        val clickedSongIndex = mediaItemsList.indexOfFirst {
                            it.mediaId == song.id.toString()
                        }
                        if (clickedSongIndex == -1) return@let

                        controller.setMediaItems(mediaItemsList, clickedSongIndex, 0L)
                        controller.prepare()
                        controller.play()
                    }
                }
            )
        }
        // --- END OF NEW BOTTOM SHEET LAYOUT ---
    }
}


// This composable just holds the LazyColumn.
@Composable
fun SongList(
    modifier: Modifier = Modifier,
    songList: List<Song>,
    onSongClick: (Song) -> Unit
) {
    LazyColumn(modifier = modifier) {
        items(songList) { song ->
            SongListItem(
                song = song,
                onClick = { onSongClick(song) }
            )
        }
    }
}

// This composable is one row in the list.
@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

// --- (LESSON 5 & 7) The "Smart" Now Playing Bar ---
// This OptIn is needed for the BottomSheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    mediaController: MediaController?,
    onCollapse: () -> Unit // Function to collapse the sheet
) {
    // --- 1. STATE ---
    // All the state from our old NowPlayingBar
    var currentSong: MediaMetadata? by remember { mutableStateOf(null) }
    var isPlaying: Boolean by remember { mutableStateOf(false) }
    val playPauseIcon by remember {
        derivedStateOf {
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow
        }
    }
    var currentPosition by remember { mutableStateOf(0L) }
    var songDuration by remember { mutableStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    // --- 2. LISTENER ---
    // The same listener from before
    DisposableEffect(mediaController) {
        if (mediaController == null) {
            currentSong = null
            isPlaying = false
            return@DisposableEffect onDispose {}
        }
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                super.onMediaMetadataChanged(mediaMetadata)
                currentSong = mediaMetadata
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                super.onIsPlayingChanged(playing)
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                if (playbackState == Player.STATE_READY) {
                    songDuration = mediaController.duration
                }
            }
        }
        mediaController.addListener(listener)

        // Update to current state
        currentSong = mediaController.mediaMetadata
        isPlaying = mediaController.isPlaying
        songDuration = mediaController.duration

        onDispose {
            mediaController.removeListener(listener)
        }
    }

    // --- 3. POLLER ---
    // The same poller from before
    LaunchedEffect(isPlaying, isDragging) { // <-- Updated key
        if (isPlaying) {
            coroutineScope.launch {
                while (true) {
                    if (!isDragging) {
                        currentPosition = mediaController?.currentPosition ?: 0L
                        sliderPosition = currentPosition.toFloat()
                    }
                    delay(1000L) // Poll every second
                }
            }
        }
    }

    // --- 4. THE UI ---
    // This Column is the *entire* bottom sheet,
    // from the mini-player to the full-screen UI.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Fill the max height a sheet can have, not the whole screen
            .padding(bottom = 80.dp) // Add padding to avoid the slider
        // getting cut off at the bottom
    ) {

        // --- THIS IS OUR OLD "NowPlayingBar" ---
        // It's now the "header" of the bottom sheet
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable { onCollapse() }, // Click to collapse!
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Song Icon",
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
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
            IconButton(onClick = {
                if (isPlaying) mediaController?.pause() else mediaController?.play()
            }) {
                Icon(
                    imageVector = playPauseIcon,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = { mediaController?.seekToNextMediaItem() }) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Skip Next",
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // --- THIS IS THE "FULL SCREEN" UI ---
        // It will only be visible when the sheet is expanded.

        // The Slider
        Slider(
            value = if (isDragging) sliderPosition else currentPosition.toFloat(),
            onValueChange = { newValue ->
                isDragging = true
                sliderPosition = newValue
            },
            onValueChangeFinished = {
                isDragging = false
                mediaController?.seekTo(sliderPosition.toLong())
            },
            valueRange = 0f..songDuration.toFloat().coerceAtLeast(1f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Main Controls (Play, Skip, etc.)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Skip Previous Button (NEW)
            IconButton(
                onClick = { mediaController?.seekToPreviousMediaItem() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Skip Previous",
                    modifier = Modifier.size(40.dp)
                )
            }

            // Play/Pause Button
            IconButton(
                onClick = { if (isPlaying) mediaController?.pause() else mediaController?.play() },
                modifier = Modifier.weight(1F)
            ) {
                Icon(
                    imageVector = playPauseIcon,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(56.dp) // Make it bigger
                )
            }

            // Skip Next Button
            IconButton(
                onClick = { mediaController?.seekToNextMediaItem() },
                modifier = Modifier.weight(1F)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Skip Next",
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}