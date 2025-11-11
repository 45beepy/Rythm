package com.example.rythm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip // <-- needed for Modifier.clip(...)
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
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import android.content.ContentUris
import android.content.ContentResolver
import android.media.MediaScannerConnection
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ModalDrawerSheet
import coil.compose.AsyncImage
import com.example.rythm.ui.theme.RythmTheme
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope as KCoroutineScope

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

// ---------- Drawer ----------
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
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST
        )
        val sortOrder = "${MediaStore.Audio.Albums.ALBUM} ASC"
        val loaded = mutableListOf<Album>()
        contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection, null, null, sortOrder
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol)
                val artist = c.getString(artistCol)
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
            // Use the grid 'items' extension explicitly imported above.
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

// ---------- SongLoader (RESTORED) ----------
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
    val playPauseIcon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow

    var isDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf(currentPosition.toFloat()) }
    var lyricLines by remember { mutableStateOf(emptyList<LyricLine>()) }
    var currentLyricIndex by remember { mutableStateOf(-1) }
    var lyricStatus by remember { mutableStateOf("No lyrics loaded.") }
    var showLyrics by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val lyricListState = rememberLazyListState()

    var showEditDialog by remember { mutableStateOf(false) }

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
                    // Detect a downward swipe
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
            IconButton(onClick = onCollapse) { Icon(Icons.Default.KeyboardArrowDown, "Collapse") }
            Text(
                text = currentSong?.albumTitle.toString().uppercase(Locale.getDefault()),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { showEditDialog = true }) { Icon(Icons.Default.MoreVert, "Options") }
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
                        // Use named 'count' to avoid ambiguous overloads
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
                Icon(Icons.Default.Shuffle, "Shuffle", modifier = Modifier.size(28.dp))
            }
            IconButton(onClick = { viewModel.skipPrevious() }) {
                Icon(Icons.Default.SkipPrevious, "Skip Previous", modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { viewModel.playPause() }) {
                Icon(Icons.Default.PlayArrow, "Play/Pause", modifier = Modifier.size(56.dp))
            }
            IconButton(onClick = { viewModel.skipNext() }) {
                Icon(Icons.Default.SkipNext, "Skip Next", modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { /* TODO: Repeat */ }) {
                Icon(Icons.Default.Repeat, "Repeat", modifier = Modifier.size(28.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { /* TODO: Devices */ }) { Icon(Icons.Outlined.Devices, "Devices") }
            IconButton(onClick = { /* TODO: Share */ }) { Icon(Icons.Default.Share, "Share") }
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
}

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
        Spacer(Modifier.width(8.dp))
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
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { viewModel.playPause() }) {
            Icon(playPauseIcon, contentDescription = "Play/Pause", modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = { viewModel.skipNext() }) {
            Icon(Icons.Default.SkipNext, "Skip Next", modifier = Modifier.size(32.dp))
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
                if (currentSong != null && currentRoute != Screen.Player.route) {
                    MiniPlayerBar(viewModel = playerViewModel) {
                        navController.navigate(Screen.Player.route)
                    }
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
            // SongLoader is now defined, so type inference works
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

// ---------- Album Detail (fixed items call) ----------
@Composable
fun AlbumDetailScreen(
    albumId: Long,
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    var album by remember { mutableStateOf<Album?>(null) }
    var songsInAlbum by remember { mutableStateOf<List<Song>>(emptyList()) }
    var mediaItemsList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun formatMimeType(mimeType: String?): String = when (mimeType) {
        "audio/flac" -> "FLAC"
        "audio/mpeg" -> "MP3"
        "audio/vorbis" -> "OGG"
        else -> "AUDIO"
    }

    LaunchedEffect(albumId) {
        isLoading = true
        contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST),
            "${MediaStore.Audio.Albums._ID} = ?",
            arrayOf(albumId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM))
                val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST))
                val artworkUri: Uri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
                album = Album(albumId, title, artist, artworkUri)
            }
        }

        val songProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val selection = "${MediaStore.Audio.Media.ALBUM_ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())
        val sortOrder = "${MediaStore.Audio.Media.TRACK} ASC, ${MediaStore.Audio.Media.TITLE} ASC"

        val loadedSongs = mutableListOf<Song>()
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            songProjection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val mimeTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol)
                val artist = cursor.getString(artistCol)
                val duration = cursor.getLong(durationCol)
                val fileType = formatMimeType(cursor.getString(mimeTypeCol))
                val albumArtUri: Uri? = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    cursor.getLong(albumIdCol)
                )
                val contentUri: Uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                loadedSongs.add(Song(id, title, artist, duration, contentUri, albumArtUri, fileType))
            }
        }
        songsInAlbum = loadedSongs

        mediaItemsList = loadedSongs.map { song ->
            val extras = Bundle().apply { putString("fileType", song.fileType) }
            MediaItem.Builder()
                .setUri(song.contentUri)
                .setMediaId(song.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setArtworkUri(song.albumArtUri)
                        .setExtras(extras)
                        .build()
                )
                .build()
        }

        isLoading = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp)
    ) {
        item {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back")
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = album?.artworkUri,
                    contentDescription = album?.title,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .aspectRatio(1f)
                        .padding(vertical = 16.dp),
                    placeholder = rememberVectorPainter(Icons.Default.MusicNote),
                    error = rememberVectorPainter(Icons.Default.MusicNote)
                )
                Text(
                    text = album?.title ?: "Unknown Album",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = "Artist", modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = album?.artist ?: "Unknown Artist",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Album • ${album?.artist ?: "Unknown Artist"}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    IconButton(onClick = { /*TODO*/ }) { Icon(Icons.Default.Add, "Add", modifier = Modifier.size(28.dp)) }
                    IconButton(onClick = { /*TODO*/ }) { Icon(Icons.Default.ArrowDownward, "Download", modifier = Modifier.size(28.dp)) }
                    IconButton(onClick = { /*TODO*/ }) { Icon(Icons.Default.MoreVert, "More", modifier = Modifier.size(28.dp)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { /*TODO*/ }) { Icon(Icons.Default.Shuffle, "Shuffle", modifier = Modifier.size(28.dp)) }
                    IconButton(
                        onClick = {
                            if (mediaItemsList.isNotEmpty()) viewModel.onSongClick(mediaItemsList, 0)
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow, "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }

        // <-- FIXED: use positional 'items' to iterate songsInAlbum -->
        items(songsInAlbum) { song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val index = mediaItemsList.indexOfFirst { it.mediaId == song.id.toString() }
                        if (index != -1) {
                            viewModel.onSongClick(mediaItemsList, index)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = { /* TODO: More */ }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
            }
        }

    }
}
