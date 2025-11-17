// MainActivity.kt
package com.example.rythm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentUris
import android.content.ContentResolver
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import coil.compose.AsyncImage
import com.example.rythm.ui.theme.RythmTheme
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

// ---------- Navigation + models ----------
sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Library : Screen("library", "Library", Icons.Default.LibraryMusic)
    object Stats : Screen("stats", "Stats", Icons.Default.QueryStats)
    object Player : Screen("player", "Player", Icons.Default.MusicNote)
    object AlbumDetail : Screen("album_detail/{albumId}", "Album", Icons.Default.MusicNote) {
        fun createRoute(albumId: Long) = "album_detail/$albumId"
    }
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Search,
    Screen.Library
)

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val contentUri: Uri,
    val albumArtUri: Uri?,
    val fileType: String?
)

// Simple metadata type used when saving metadata from dialog.
data class SongMetadata(
    val id: Long,
    val title: String,
    val artist: String,
    val composer: String? = null,
    val album: String? = null
)

// ---------- Permission gate (Android 13+ compatible) ----------
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionGatedContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val permissionState = rememberPermissionState(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
    )

    if (permissionState.status.isGranted) {
        content()
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = { permissionState.launchPermissionRequest() }) {
                Text("Grant storage access")
            }
        }
    }
}

// ---------- Activity ----------
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
                    PermissionGatedContent {
                        MainApp(
                            navController = rememberNavController(),
                            onProfileClick = { /* TODO */ }
                        )
                    }
                }
            }
        }
    }
}

// ---------- Drawer (unchanged) ----------
@Composable
fun AppDrawerContent(
    navController: NavHostController,
    scope: CoroutineScope,
    drawerState: DrawerState
) {
    val context = LocalContext.current
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text("Rythm User", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
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
            NavigationDrawerItem(
                label = { Text("Recently Played") },
                icon = { Icon(Icons.Default.History, null) },
                selected = false,
                onClick = { scope.launch { drawerState.close() } }
            )
            NavigationDrawerItem(
                label = { Text("Rescan Library") },
                icon = { Icon(Icons.Default.Refresh, null) },
                selected = false,
                onClick = {
                    Toast.makeText(context, "Starting library rescan...", Toast.LENGTH_SHORT).show()
                    val musicDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(musicDir.absolutePath),
                        null
                    ) { _, _ -> }
                    scope.launch { drawerState.close() }
                }
            )
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

// ---------- Albums grid ----------
@Composable
fun AlbumGrid(
    viewModel: PlayerViewModel,
    onAlbumClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val contentResolver: ContentResolver = context.contentResolver
    var albumList by remember { mutableStateOf<List<Album>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val projection = arrayOf(
            android.provider.MediaStore.Audio.Albums._ID,
            android.provider.MediaStore.Audio.Albums.ALBUM,
            android.provider.MediaStore.Audio.Albums.ARTIST
        )
        val sortOrder = "${android.provider.MediaStore.Audio.Albums.ALBUM} ASC"
        val loaded = mutableListOf<Album>()
        contentResolver.query(
            android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Albums._ID)
            val titleCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Albums.ALBUM)
            val artistCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Albums.ARTIST)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol) ?: "Unknown"
                val artist = c.getString(artistCol) ?: "Unknown"
                val artworkUri: Uri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), id
                )
                loaded.add(Album(id, title, artist, artworkUri))
            }
        }
        albumList = loaded
        isLoading = false
    }

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albumList) { album ->
                AlbumGridItem(album = album) { onAlbumClick(album.id) }
            }
        }
    }
}

@Composable
fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = album.artworkUri,
            contentDescription = album.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            placeholder = rememberVectorPainter(Icons.Default.MusicNote),
            error = rememberVectorPainter(Icons.Default.MusicNote)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------- SongLoader ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongLoader(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel,
    onProfileClick: () -> Unit,
    onAlbumClick: (Long) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("Local") }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(onClick = onProfileClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.AccountCircle, contentDescription = "Profile", modifier = Modifier.fillMaxSize())
            }
            val selectedChipColors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                selectedLabelColor = MaterialTheme.colorScheme.background
            )
            FilterChip(
                selected = selectedFilter == "Local",
                onClick = { selectedFilter = "Local" },
                label = { Text("Local") },
                colors = selectedChipColors
            )
            FilterChip(
                selected = selectedFilter == "Drive",
                onClick = { selectedFilter = "Drive" },
                label = { Text("Drive") },
                colors = selectedChipColors
            )
            FilterChip(
                selected = selectedFilter == "Podcasts",
                onClick = { selectedFilter = "Podcasts" },
                label = { Text("Podcasts") },
                colors = selectedChipColors
            )
        }

        when (selectedFilter) {
            "Local" -> AlbumGrid(viewModel = viewModel, onAlbumClick = onAlbumClick)
            "Drive" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Drive feature coming soon!") }
            "Podcasts" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Podcasts feature coming soon!") }
        }
    }
}

// ---------- Utilities ----------
fun Long.formatTime(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

// ---------- EditSongDialog ----------
@Composable
fun EditSongDialog(
    currentSongMeta: MediaMetadata?,
    initialLrc: String?,
    onSave: (title: String, artist: String, composer: String?, lrcText: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultTitle = currentSongMeta?.title?.toString() ?: ""
    val defaultArtist = currentSongMeta?.artist?.toString() ?: ""
    val defaultComposer = currentSongMeta?.extras?.getString("composer") ?: ""

    var title by remember { mutableStateOf(defaultTitle) }
    var artist by remember { mutableStateOf(defaultArtist) }
    var composer by remember { mutableStateOf(defaultComposer) }
    var lrcText by remember { mutableStateOf(initialLrc ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit song details") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Song title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = composer,
                    onValueChange = { composer = it },
                    label = { Text("Composer (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Paste synced (LRC) or unsynced lyrics below:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = lrcText,
                    onValueChange = { lrcText = it },
                    label = { Text("Lyrics (LRC / plain)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    maxLines = 20,
                    singleLine = false
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "If you paste LRC (timestamps like [00:12.34]) the app will store them as synced lyrics.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(2.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) {
                    onDismiss()
                    return@TextButton
                }
                onSave(title.trim(), artist.trim(), composer.trim().ifBlank { null }, lrcText.ifBlank { null })
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ---------- PlayerScreen / MiniPlayerBar / App + Nav ----------
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit
) {
    val currentSong by viewModel.currentSong
    val isPlaying by viewModel.isPlaying
    val currentPosition by viewModel.currentPosition
    val songDuration by viewModel.songDuration

    var isDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf(currentPosition.toFloat()) }
    var lyricLines by remember { mutableStateOf(emptyList<LyricLine>()) }
    var currentLyricIndex by remember { mutableStateOf(-1) }
    var lyricStatus by remember { mutableStateOf("No lyrics loaded.") }
    var showLyrics by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val lyricListState = rememberLazyListState()

    var showEditDialog by remember { mutableStateOf(false) }
    var initialLrc by remember { mutableStateOf<String?>(null) }

    // If your PlayerViewModel exposes LRC text StateFlow, collect it here (uncomment & adapt if exists)
    // LaunchedEffect(Unit) { viewModel.currentLrcText.collect { initialLrc = it } }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 20) {
                        onCollapse()
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCollapse) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse") }
            Text(
                text = currentSong?.albumTitle.toString().uppercase(Locale.getDefault()),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { showEditDialog = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Options") }
        }

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
                        .fillMaxWidth(0.8f)
                        .padding(vertical = 16.dp),
                    placeholder = rememberVectorPainter(Icons.Default.MusicNote),
                    error = rememberVectorPainter(Icons.Default.MusicNote)
                )
                Spacer(Modifier.height(32.dp))
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
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
                val fileType = currentSong?.extras?.getString("fileType") ?: ""
                Text(
                    text = fileType.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            TextButton(onClick = { showLyrics = !showLyrics }) {
                Text(if (showLyrics) "Hide Lyrics" else "Show Lyrics")
            }
            Spacer(Modifier.height(16.dp))

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
                        items(count = lyricLines.size) { index ->
                            val line = lyricLines[index]
                            val isCurrentLine = (index == currentLyricIndex)
                            val color =
                                if (isCurrentLine) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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

        Column(Modifier.padding(horizontal = 16.dp)) {
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
                    .padding(horizontal = 16.dp),
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
                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { viewModel.skipPrevious() }) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Skip Previous", modifier = Modifier.size(40.dp))
            }

            // Play / Pause with pop animation
            val isPlayingLocal by viewModel.isPlaying
            val coroutineScope = rememberCoroutineScope()

            var tapScale by remember { mutableStateOf(1f) }
            val tapScaleAnimated by animateFloatAsState(
                targetValue = tapScale,
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            )

            IconButton(
                onClick = {
                    coroutineScope.launch {
                        tapScale = 0.9f
                        delay(60)
                        tapScale = 1.12f
                        delay(90)
                        tapScale = 1f
                    }
                    viewModel.playPause()
                },
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        scaleX = tapScaleAnimated
                        scaleY = tapScaleAnimated
                    }
            ) {
                Crossfade(targetState = isPlayingLocal) { playing ->
                    if (playing) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause",
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            IconButton(onClick = { viewModel.skipNext() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Skip Next", modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { /* TODO: Repeat */ }) {
                Icon(Icons.Default.Repeat, contentDescription = "Repeat", modifier = Modifier.size(28.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { /* TODO: Devices */ }) { Icon(Icons.Outlined.Devices, contentDescription = "Devices") }
            IconButton(onClick = { /* TODO: Share */ }) { Icon(Icons.Default.Share, contentDescription = "Share") }
            IconButton(onClick = { showLyrics = !showLyrics }) {
                Icon(
                    imageVector = Icons.Default.TextSnippet,
                    contentDescription = if (showLyrics) "Hide Lyrics" else "Show Lyrics",
                    tint = if (showLyrics) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // show edit dialog when triggered
    if (showEditDialog) {
        EditSongDialog(
            currentSongMeta = currentSong,
            initialLrc = initialLrc,
            onSave = { newTitle, newArtist, newComposer, lrc ->
                // get a reliable id if available from controller's currentMediaItem
                val idFromController = viewModel.mediaController
                    ?.currentMediaItem
                    ?.mediaId
                    ?.toLongOrNull()

                val id = idFromController ?: System.currentTimeMillis()

                val metadata = SongMetadata(
                    id = id,
                    title = newTitle,
                    artist = newArtist,
                    composer = newComposer,
                    album = currentSong?.albumTitle?.toString()
                )

                // Save metadata & lyrics using your ViewModel method (signature must match)
                try {
                    viewModel.saveSongMetadataAndLyrics(id, metadata, lrc)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // If you want the UI to update instantly, add a helper in PlayerViewModel
                // like: fun updateCurrentMetadata(meta: MediaMetadata) { _currentSong.value = meta }
                // and call it here (uncomment if implemented).
                //
                // Example:
                // viewModel.updateCurrentMetadata(MediaMetadata.Builder().setTitle(newTitle).setArtist(newArtist).build())

                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }
}

@Composable
fun MiniPlayerBar(
    viewModel: PlayerViewModel,
    onClick: () -> Unit
) {
    val currentSong by viewModel.currentSong
    val isPlaying by viewModel.isPlaying

    val artScale by animateFloatAsState(
        targetValue = if (currentSong != null) 1f else 0.9f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    val buttonBaseScaleTarget = if (isPlaying) 1.05f else 1f
    val buttonScale by animateFloatAsState(
        targetValue = buttonBaseScaleTarget,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = currentSong?.artworkUri,
            contentDescription = currentSong?.title?.toString() ?: "No song",
            modifier = Modifier
                .size((40.dp * artScale).coerceAtLeast(32.dp))
                .clip(CircleShape),
            placeholder = rememberVectorPainter(Icons.Default.MusicNote),
            error = rememberVectorPainter(Icons.Default.MusicNote)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentSong?.title?.toString() ?: "Unknown",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentSong?.artist?.toString() ?: "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = { viewModel.playPause() },
            modifier = Modifier.size((40.dp * buttonScale).coerceAtLeast(32.dp))
        ) {
            Crossfade(targetState = isPlaying) { playing ->
                if (playing) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause",
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        IconButton(onClick = { viewModel.skipNext() }) {
            Icon(Icons.Default.SkipNext, contentDescription = "Skip Next")
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainApp(
    navController: NavHostController,
    onProfileClick: () -> Unit
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
                val showMini = currentSong != null && currentRoute != Screen.Player.route

                AnimatedVisibility(
                    visible = showMini,
                    enter = slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight / 2 },
                        animationSpec = tween(durationMillis = 300)
                    ) + fadeIn(animationSpec = tween(200)),
                    exit = slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight / 2 },
                        animationSpec = tween(durationMillis = 250)
                    ) + fadeOut(animationSpec = tween(180))
                ) {
                    MiniPlayerBar(
                        viewModel = playerViewModel,
                        onClick = { navController.navigate(Screen.Player.route) }
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
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
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
            onProfileClick = onProfileClick,
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
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) { HomeScreen(onProfileClick) }
        composable(Screen.Search.route) { SearchScreen() }
        composable(Screen.Library.route) {
            SongLoader(
                modifier = Modifier,
                viewModel = viewModel,
                onProfileClick = onProfileClick,
                onAlbumClick = { albumId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumId))
                }
            )
        }
        composable(Screen.Stats.route) { StatsScreen() }
        composable(Screen.Player.route) {
            PlayerScreen(
                viewModel = viewModel,
                onCollapse = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(navArgument("albumId") { type = NavType.LongType })
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("albumId")
            if (albumId == null) {
                navController.popBackStack()
            } else {
                AlbumDetailScreen(
                    albumId = albumId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

// ---------- Simple screens ----------
@Composable
fun HomeScreen(onProfileClick: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onProfileClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.AccountCircle, contentDescription = "Profile", modifier = Modifier.fillMaxSize())
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Home Screen (Discovery) - Coming Soon!", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun SearchScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Search Screen - Coming Soon!", style = MaterialTheme.typography.titleLarge)
    }
}
