package com.example.rythm

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.rythm.ui.theme.RythmTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Defines the screens in our app.
sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Library : Screen("library", "Library", Icons.Default.LibraryMusic)
    object Stats : Screen("stats", "Stats", Icons.Default.QueryStats)
}

// A helper list of all our screens
val bottomNavItems = listOf(
    Screen.Library,
    Screen.Stats
)

// This is our data model.
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val contentUri: Uri,
    val albumArtUri: Uri?
)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

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
            MainApp()
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
fun SongLoader(modifier: Modifier = Modifier) {
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

    // Convert List<Song> to List<MediaItem>
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
                            .setArtworkUri(song.albumArtUri)
                            .build()
                    )
                    .build()
            }
        }
    }

    // Load Songs from MediaStore
    LaunchedEffect(Unit) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
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
            val albumIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (c.moveToNext()) {
                val id = c.getLong(idColumn)
                val title = c.getString(titleColumn)
                val artist = c.getString(artistColumn)
                val duration = c.getLong(durationColumn)
                val albumId = c.getLong(albumIdColumn)

                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                // This is the special URI for finding album art.
                val albumArtUri: Uri? = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )

                loadedSongs.add(Song(id, title, artist, duration, contentUri, albumArtUri))
            }
        }
        songList = loadedSongs
        isLoading = false
    }

    // UI Layer (Displaying List and Player)
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        val scope = rememberCoroutineScope()
        val scaffoldState = rememberBottomSheetScaffoldState()

        BottomSheetScaffold(
            modifier = modifier,
            scaffoldState = scaffoldState,
            sheetPeekHeight = 80.dp,
            sheetContent = {
                PlayerScreen(
                    mediaController = mediaController,
                    onCollapse = {
                        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                    }
                )
            }
        ) { innerPadding ->
            SongList(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding), // This padding is from the BottomSheet
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
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = song.title,
            modifier = Modifier.size(40.dp),
            placeholder = rememberVectorPainter(Icons.Default.MusicNote),
            error = rememberVectorPainter(Icons.Default.MusicNote)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
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
        Spacer(modifier = Modifier.weight(1f))
        val durationMinutes = (song.duration / 1000) / 60
        val durationSeconds = (song.duration / 1000) % 60
        val durationString = String.format(Locale.getDefault(), "%d:%02d", durationMinutes, durationSeconds)
        Text(
            text = durationString,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// This is the "Smart" Player Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    mediaController: MediaController?,
    onCollapse: () -> Unit
) {
    // --- 1. STATE ---
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
    var lyricLines by remember { mutableStateOf(emptyList<LyricLine>()) }
    var currentLyricIndex by remember { mutableStateOf(-1) }
    var lyricStatus by remember { mutableStateOf("No lyrics loaded.") }
    val coroutineScope = rememberCoroutineScope()

    // --- NEW: State for our LazyColumn ---
    val lyricListState = rememberLazyListState()

    // --- 2. LISTENER ---
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
        currentSong = mediaController.mediaMetadata
        isPlaying = mediaController.isPlaying
        songDuration = mediaController.duration
        onDispose {
            mediaController.removeListener(listener)
        }
    }

    // --- 3. POLLER (with auto-scroll) ---
    LaunchedEffect(isPlaying, isDragging) {
        if (isPlaying) {
            coroutineScope.launch {
                while (true) {
                    if (!isDragging) {
                        val newPosition = mediaController?.currentPosition ?: 0L
                        currentPosition = newPosition
                        sliderPosition = newPosition.toFloat()

                        if (lyricLines.isNotEmpty()) {
                            val newIndex = lyricLines.indexOfLast { it.timeInMs <= newPosition }
                            if (newIndex != currentLyricIndex) {
                                currentLyricIndex = newIndex

                                // --- THIS IS THE AUTO-SCROLL ---
                                // We scroll to 2 lines *before* the current one
                                // to keep the active line centered.
                                val scrollToIndex = (newIndex - 2).coerceAtLeast(0)
                                lyricListState.animateScrollToItem(scrollToIndex)
                                // --- END OF AUTO-SCROLL ---
                            }
                        }
                    }
                    delay(1000L)
                }
            }
        }
    }

    // --- 4. LYRICS FETCHER ---
    LaunchedEffect(currentSong) {
        if (currentSong == null) {
            lyricStatus = ""
            lyricLines = emptyList()
            currentLyricIndex = -1
            return@LaunchedEffect
        }
        val artist = currentSong?.artist.toString()
        val title = currentSong?.title.toString()
        if (artist.isBlank() || title.isBlank() || artist == "null" || title == "null") {
            lyricStatus = "Lyrics not available."
            lyricLines = emptyList()
            currentLyricIndex = -1
            return@LaunchedEffect
        }
        lyricStatus = "Loading lyrics..."
        lyricLines = emptyList()
        currentLyricIndex = -1
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = RetrofitInstance.api.getLyrics(artist = artist, track = title)
                val lrcText = response.syncedLyrics
                if (lrcText == null) {
                    lyricStatus = "No lyrics found."
                } else {
                    val lines = LyricParser.parse(lrcText)
                    if (lines.isEmpty()) {
                        lyricStatus = "Unsynced lyrics (not supported)."
                    } else {
                        lyricLines = lines
                        lyricStatus = ""
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                lyricStatus = "Error: Could not load lyrics."
            }
        }
    }

    // --- 5. THE NEW UI ---
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // --- Mini-Player / Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable { onCollapse() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = currentSong?.artworkUri,
                contentDescription = currentSong?.title.toString(),
                modifier = Modifier.size(40.dp),
                placeholder = rememberVectorPainter(Icons.Default.MusicNote),
                error = rememberVectorPainter(Icons.Default.MusicNote)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
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
            Spacer(modifier = Modifier.weight(1f))
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

        // --- Main Expanded Content (Art, Title, Lyrics) ---
        // This Column now fills the space and *doesn't* scroll
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // 1. Large Album Art
            AsyncImage(
                model = currentSong?.artworkUri,
                contentDescription = "Large Album Art",
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(vertical = 16.dp),
                placeholder = rememberVectorPainter(Icons.Default.MusicNote),
                error = rememberVectorPainter(Icons.Default.MusicNote)
            )
            Spacer(modifier = Modifier.height(32.dp))

            // 2. Large Title & Artist
            Text(
                text = currentSong?.title.toString(),
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentSong?.artist.toString(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(32.dp))

            // 3. Lyrics (This now fills the space and scrolls)
            LazyColumn(
                state = lyricListState, // <-- Attach the state
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // <-- This makes the lyrics fill the space
                horizontalAlignment = Alignment.CenterHorizontally // Center the text
            ) {
                if (lyricLines.isEmpty()) {
                    item {
                        Text(
                            text = lyricStatus,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    items(lyricLines.size) { index ->
                        val line = lyricLines[index]
                        val isCurrentLine = (index == currentLyricIndex)
                        val color = if (isCurrentLine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = color,
                            fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp)) // Padding before slider
        }

        // --- Bottom Controls (Slider & Buttons) ---
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            IconButton(
                onClick = { mediaController?.seekToPreviousMediaItem() }
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Skip Previous",
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(
                onClick = { if (isPlaying) mediaController?.pause() else mediaController?.play() }
            ) {
                Icon(
                    imageVector = playPauseIcon,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(56.dp)
                )
            }
            IconButton(
                onClick = { mediaController?.seekToNextMediaItem() }
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

// --- The App's Navigation ---
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainApp() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) {
        AppNavigation(
            navController = navController,
            modifier = Modifier.padding(it)
        )
    }
}

@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Library.route,
        modifier = modifier
    ) {
        composable(Screen.Library.route) {
            SongLoader(modifier = modifier)
        }
        composable(Screen.Stats.route) {
            StatsScreen()
        }
    }
}