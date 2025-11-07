package com.example.rythm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.FilterChipDefaults

// Defines the screens in our app.
sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Library : Screen("library", "Library", Icons.Default.LibraryMusic)
    object Stats : Screen("stats", "Stats", Icons.Default.QueryStats)
    object Player : Screen("player", "Player", Icons.Default.MusicNote)
    // Settings screen is now in the drawer
}

// --- MODIFIED: "Settings" is removed from the bottom bar ---
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
            val isDarkTheme = ThemeState.isDarkTheme
            RythmTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // This is now the root composable
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
            // --- NEW: Set up the Drawer Navigation ---
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            // NavController is now at the top level
            val navController = rememberNavController()

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    AppDrawerContent(
                        navController = navController,
                        scope = scope,
                        drawerState = drawerState
                    )
                }
            ) {
                // MainApp is now the content *inside* the drawer
                MainApp(
                    navController = navController,
                    onProfileClick = {
                        scope.launch { drawerState.open() }
                    }
                )
            }
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

// --- NEW: The UI for the slide-out menu ---
@Composable
fun AppDrawerContent(
    navController: NavHostController,
    scope: CoroutineScope,
    drawerState: DrawerState
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            // 1. Profile Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = "Profile",
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Rythm User", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(24.dp))

            // 2. "Stats" Item
            NavigationDrawerItem(
                label = { Text("Stats") },
                icon = { Icon(Icons.Default.QueryStats, null) },
                selected = false,
                onClick = {
                    navController.navigate(Screen.Stats.route) {
                        popUpTo(navController.graph.startDestinationId)
                    }
                    scope.launch { drawerState.close() }
                }
            )

            // 3. "Recents" Item
            NavigationDrawerItem(
                label = { Text("Recently Played") },
                icon = { Icon(Icons.Default.History, null) },
                selected = false,
                onClick = {
                    // TODO: Implement Recents screen
                    scope.launch { drawerState.close() }
                }
            )

            // 4. "Dark Mode" Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Dark Mode", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = ThemeState.isDarkTheme,
                    onCheckedChange = { ThemeState.isDarkTheme = it }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongLoader(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel,
    onProfileClick: () -> Unit // Now accepts click
) {
    val context = LocalContext.current
    val contentResolver: ContentResolver = context.contentResolver

    var songList by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("Local") }

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

    LaunchedEffect(Unit) {
        // ... (Your song loading logic is unchanged) ...
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

    // UI Layer
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        Column(modifier = modifier) {

            // --- THIS IS THE MODIFIED TOP BAR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp) // Space between icon and chips
            ) {
                // 1. Profile Icon Button
                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier.size(32.dp) // Set size for the button
                ) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize() // Icon fills the button
                    )
                }

                // --- 2. Filter Chips with NEW Colors ---
                val selectedChipColors = FilterChipDefaults.filterChipColors(
                    // Selected background is white (onBackground in dark mode)
                    selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                    // Selected text is black (background in dark mode)
                    selectedLabelColor = MaterialTheme.colorScheme.background
                )

                FilterChip(
                    selected = selectedFilter == "Local",
                    onClick = { selectedFilter = "Local" },
                    label = { Text("Local") },
                    colors = selectedChipColors // <-- APPLY COLORS
                )
                FilterChip(
                    selected = selectedFilter == "Drive",
                    onClick = { selectedFilter = "Drive" },
                    label = { Text("Drive") },
                    colors = selectedChipColors // <-- APPLY COLORS
                )
                FilterChip(
                    selected = selectedFilter == "Podcasts",
                    onClick = { selectedFilter = "Podcasts" },
                    label = { Text("Podcasts") },
                    colors = selectedChipColors // <-- APPLY COLORS
                )
            }
            // --- END OF MODIFIED BAR ---

            // --- Show content based on filter ---
            when (selectedFilter) {
                "Local" -> {
                    SongList(
                        modifier = Modifier.fillMaxSize(),
                        songList = songList,
                        onSongClick = { song ->
                            val clickedSongIndex = mediaItemsList.indexOfFirst {
                                it.mediaId == song.id.toString()
                            }
                            if (clickedSongIndex != -1) {
                                viewModel.onSongClick(mediaItemsList, clickedSongIndex)
                            }
                        }
                    )
                }
                "Drive" -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Drive feature coming soon!")
                    }
                }
                "Podcasts" -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Podcasts feature coming soon!")
                    }
                }
            }
        }
    }
}


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
        Column(modifier = Modifier.weight(1f)) {
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
        Text(
            text = song.duration.formatTime(),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

fun Long.formatTime(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit
) {
    // --- 1. STATE ---
    val currentSong by viewModel.currentSong
    val isPlaying by viewModel.isPlaying
    val currentPosition by viewModel.currentPosition
    val songDuration by viewModel.songDuration

    val playPauseIcon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow

    var isDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf(currentPosition.toFloat()) }
    var lyricLines by remember { mutableStateOf(emptyList<LyricLine>()) }
    var currentLyricIndex by remember { mutableStateOf(-1) }
    var lyricStatus by remember { mutableStateOf("No lyrics loaded.") }
    var showLyrics by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val lyricListState = rememberLazyListState()

    // --- 3. POLLER (for Slider and Lyric sync) ---
    LaunchedEffect(isPlaying, isDragging, currentPosition) {
        if (isPlaying && !isDragging) {
            sliderPosition = currentPosition.toFloat()
            if (lyricLines.isNotEmpty()) {
                val newIndex = lyricLines.indexOfLast { it.timeInMs <= currentPosition }
                if (newIndex != currentLyricIndex) {
                    currentLyricIndex = newIndex
                    val viewportHeight = lyricListState.layoutInfo.viewportSize.height
                    val scrollOffset = -(viewportHeight / 2)
                    if (currentLyricIndex > 2) {
                        lyricListState.animateScrollToItem(currentLyricIndex, scrollOffset)
                    } else {
                        lyricListState.animateScrollToItem(0)
                    }
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
            showLyrics = false
            return@LaunchedEffect
        }
        val artist = currentSong?.artist.toString()
        val title = currentSong?.title.toString()
        if (artist.isBlank() || title.isBlank() || artist == "null" || title == "null") {
            lyricStatus = "Lyrics not available."
            lyricLines = emptyList()
            currentLyricIndex = -1
            showLyrics = false
            return@LaunchedEffect
        }
        lyricStatus = "Loading lyrics..."
        lyricLines = emptyList()
        currentLyricIndex = -1
        showLyrics = false
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

    // --- 5. THE UI (Spotify Layout) ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp) // Add padding for the nav bar
    ) {
        // --- NEW: Top Bar ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Default.KeyboardArrowDown, "Collapse")
            }
            Text(
                "PLAYING FROM YOUR LIBRARY", // TODO: Make this dynamic later
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { /* TODO: Options */ }) {
                Icon(Icons.Default.MoreVert, "Options")
            }
        }

        // Main Expanded Content (Art, Title, Lyrics)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!showLyrics) {
                AsyncImage(
                    model = currentSong?.artworkUri,
                    contentDescription = "Large Album Art",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    placeholder = rememberVectorPainter(Icons.Default.MusicNote),
                    error = rememberVectorPainter(Icons.Default.MusicNote)
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            // --- NEW: Song Info Row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                }
                IconButton(onClick = { /* TODO: Favorite Logic */ }) {
                    Icon(
                        imageVector = Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            TextButton(onClick = { showLyrics = !showLyrics }) {
                Text(if (showLyrics) "Hide Lyrics" else "Show Lyrics")
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (showLyrics) {
                LazyColumn(
                    state = lyricListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
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
                                fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // --- Bottom Controls (Slider & Buttons) ---
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Slider(
                value = if (isDragging) sliderPosition else currentPosition.toFloat(),
                onValueChange = { newValue ->
                    isDragging = true
                    sliderPosition = newValue
                },
                onValueChangeFinished = {
                    isDragging = false
                    viewModel.seekTo(sliderPosition.toLong())
                },
                valueRange = 0f..songDuration.toFloat().coerceAtLeast(1f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp), // Padding for the text
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(currentPosition.formatTime(), style = MaterialTheme.typography.bodySmall)
                Text(songDuration.formatTime(), style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            IconButton(onClick = { /* TODO: Shuffle */ }) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = { viewModel.skipPrevious() }) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Skip Previous",
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { viewModel.playPause() }) {
                Icon(
                    imageVector = playPauseIcon,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(56.dp)
                )
            }
            IconButton(onClick = { viewModel.skipNext() }) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Skip Next",
                    modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { /* TODO: Repeat */ }) {
                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* TODO: Devices */ }) {
                Icon(Icons.Outlined.Devices, "Devices")
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { /* TODO: Share */ }) {
                Icon(Icons.Default.Share, "Share")
            }
            IconButton(onClick = { /* TODO: Queue */ }) {
                Icon(Icons.Outlined.QueueMusic, "Queue")
            }
        }
    }
}

// Mini Player Bar
@Composable
fun MiniPlayerBar(
    viewModel: PlayerViewModel,
    onClick: () -> Unit
) {
    val currentSong by viewModel.currentSong
    val isPlaying by viewModel.isPlaying
    val playPauseIcon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
        IconButton(onClick = { viewModel.playPause() }) {
            Icon(
                imageVector = playPauseIcon,
                contentDescription = "Play/Pause",
                modifier = Modifier.size(32.dp)
            )
        }
        IconButton(onClick = { viewModel.skipNext() }) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Skip Next",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// --- The App's Navigation ---
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainApp(
    navController: NavHostController,
    onProfileClick: () -> Unit // <-- NEW
) {
    val context = LocalContext.current
    val playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModelFactory(context.applicationContext as Application)
    )
    val currentSong by playerViewModel.currentSong
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            Column {
                // Only show the mini player if a song is loaded
                // AND we are NOT on the full player screen.
                if (currentSong != null && currentRoute != Screen.Player.route) {
                    MiniPlayerBar(
                        viewModel = playerViewModel,
                        onClick = {
                            navController.navigate(Screen.Player.route)
                        }
                    )
                }
                NavigationBar {
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
        }
    ) { innerPadding ->
        AppNavigation(
            navController = navController,
            viewModel = playerViewModel,
            onProfileClick = onProfileClick, // <-- PASS CLICK
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModel: PlayerViewModel,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Library.route,
        modifier = modifier
    ) {
        composable(Screen.Library.route) {
            SongLoader(
                modifier = Modifier,
                viewModel = viewModel,
                onProfileClick = onProfileClick
            )
        }
        composable(Screen.Stats.route) {
            StatsScreen()
        }
        composable(Screen.Player.route) {
            PlayerScreen(
                viewModel = viewModel,
                onCollapse = {
                    navController.popBackStack()
                }
            )
        }
    }
}