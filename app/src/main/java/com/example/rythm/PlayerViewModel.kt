// PlayerViewModel.kt
package com.example.rythm

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.lang.Exception

/**
 * PlayerViewModel - manages MediaController state + a small lyrics helper surface.
 *
 * This file preserves your existing media control logic and adds:
 *  - currentLrcText: StateFlow<String?> (raw LRC or plain text)
 *  - currentLyrics: StateFlow<List<LyricLine>> (parsed LRC)
 *  - loadLyricsForSong(songId)
 *  - saveSongMetadataAndLyrics(songId, metadata, lrcText)
 *  - deleteSavedLyrics(songId)
 *
 * For storage we use a tiny in-memory LocalLyricsStore fallback so the app compiles
 * if you don't yet have a dedicated repository. Replace LocalLyricsStore calls
 * with your lyricsRepo (if available) later.
 */

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    // Media controller future + accessor
    private var mediaControllerFuture: ListenableFuture<MediaController>
    val mediaController: MediaController?
        get() = if (mediaControllerFuture.isDone) mediaControllerFuture.get() else null

    // --- UI state exposed as Compose-friendly State ---
    private val _currentSong = mutableStateOf<MediaMetadata?>(null)
    private val _isPlaying = mutableStateOf(false)
    private val _currentPosition = mutableStateOf(0L)
    private val _songDuration = mutableStateOf(0L)

    val currentSong: State<MediaMetadata?> = _currentSong
    val isPlaying: State<Boolean> = _isPlaying
    val currentPosition: State<Long> = _currentPosition
    val songDuration: State<Long> = _songDuration

    // small sheet visible toggle
    private val _isPlayerSheetVisible = mutableStateOf(false)
    val isPlayerSheetVisible: State<Boolean> = _isPlayerSheetVisible
    fun showPlayer() { _isPlayerSheetVisible.value = true }
    fun hidePlayer() { _isPlayerSheetVisible.value = false }

    // --- Lyrics state flows (UI can collect these) ---
    private val _currentLrcText = MutableStateFlow<String?>(null)
    val currentLrcText: StateFlow<String?> = _currentLrcText

    private val _currentLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val currentLyrics: StateFlow<List<LyricLine>> = _currentLyrics

    // --- A tiny local in-memory lyrics store (fallback if you don't have a repo) ---
    private object LocalLyricsStore {
        // songId -> raw LRC text
        private val store = mutableMapOf<Long, String>()
        fun save(songId: Long, lrcText: String) { store[songId] = lrcText }
        fun load(songId: Long): String? = store[songId]
        fun delete(songId: Long) { store.remove(songId) }
    }

    // --- NEW: Get a database instance (keeps your original db usage) ---
    // If StatsDatabase isn't available compilation will fail — keep if your project includes it.
    private val database by lazy { StatsDatabase.getDatabase(getApplication()).songStatDao() }

    // Session token / controller initialization
    private val sessionToken: SessionToken

    init {
        sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        mediaControllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        mediaControllerFuture.addListener(
            {
                mediaController?.addListener(playerListener)
                updatePlayerState()
            },
            ContextCompat.getMainExecutor(getApplication())
        )
        startPolling()
    }

    private fun updatePlayerState() {
        _currentSong.value = mediaController?.mediaMetadata
        _isPlaying.value = mediaController?.isPlaying ?: false
        _songDuration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
        _currentPosition.value = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            _currentSong.value = mediaMetadata
            _songDuration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L

            // When a new song starts, update DB play-count (non-blocking)
            mediaController?.currentMediaItem?.let {
                viewModelScope.launch(Dispatchers.IO) {
                    incrementPlayCount(it)
                }
            }

            // Try to load any stored lyrics for this song automatically
            val id = mediaController?.currentMediaItem?.mediaId?.toLongOrNull()?.let { id ->
                loadLyricsForSong(id)
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                // update position every second if playing
                if (_isPlaying.value) {
                    _currentPosition.value = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
                }
                delay(1000L)
            }
        }
    }

    // --- Public Functions (media control) ---
    fun onSongClick(songList: List<MediaItem>, songIndex: Int) {
        mediaController?.let {
            it.setMediaItems(songList, songIndex, 0L)
            it.prepare()
            it.play()
        }
    }

    fun playPause() {
        if (_isPlaying.value) mediaController?.pause() else mediaController?.play()
    }

    fun skipNext() = mediaController?.seekToNextMediaItem()
    fun skipPrevious() = mediaController?.seekToPreviousMediaItem()
    fun seekTo(position: Long) = mediaController?.seekTo(position)

    // --- Lyrics/storage helpers ---

    /**
     * Loads saved/synced lyrics for a given songId.
     * First it queries LocalLyricsStore (in-memory) — replace with your repo if you have one.
     * If found it will update currentLrcText and currentLyrics (parsed via LrcParser).
     */
    fun loadLyricsForSong(songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // TODO: Replace LocalLyricsStore with real repository call if available:
                val saved = LocalLyricsStore.load(songId)
                if (saved != null) {
                    _currentLrcText.value = saved
                    // parse LRC to lines — safe fallback to emptyList
                    val parsed = try { LyricParser.parse(saved) } catch (e: Exception) { emptyList<LyricLine>() }
                    _currentLyrics.value = parsed
                } else {
                    _currentLrcText.value = null
                    _currentLyrics.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _currentLrcText.value = null
                _currentLyrics.value = emptyList()
            }
        }
    }

    /**
     * Save metadata + optional LRC text for a song.
     * This is callable from UI (e.g. Edit dialog) — it persists lyrics to LocalLyricsStore (fallback).
     * Replace with your actual persistence code (lyricsRepo) if you have one.
     */
    fun saveSongMetadataAndLyrics(songId: Long, metadata: SongMetadata, lrcText: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Persist lyrics to your storage (fallback: LocalLyricsStore as before)
                if (!lrcText.isNullOrBlank()) {
                    LocalLyricsStore.save(songId, lrcText)
                    _currentLrcText.value = lrcText
                    val parsed = try { LyricParser.parse(lrcText) } catch (e: Exception) { emptyList<LyricLine>() }
                    _currentLyrics.value = parsed
                }

                // Persist metadata where you store metadata (DB/repo) - left as TODO
                // TODO: save metadata to your persistent repository if you have one

                // Update the in-memory displayed metadata so UI updates immediately
                val existing = _currentSong.value
                if (existing != null) {
                    val builder = MediaMetadata.Builder()
                        .setTitle(metadata.title)
                        .setArtist(metadata.artist)
                        .setArtworkUri(existing.artworkUri)
                        .setExtras(existing.extras) // preserve extras if any

                    _currentSong.value = builder.build()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    /**
     * Delete saved lyrics for a song (fallback store).
     */
    fun deleteSavedLyrics(songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                LocalLyricsStore.delete(songId)
                // clear current state if currently showing that song's lyrics
                val currentId = mediaController?.currentMediaItem?.mediaId?.toLongOrNull()

                if (currentId == songId) {
                    _currentLrcText.value = null
                    _currentLyrics.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Helper to update the in-memory displayed metadata immediately.
     * This doesn't persist to storage; use saveSongMetadataAndLyrics for persistence.
     */
    fun updateCurrentMetadata(title: String?, artist: String?) {
        val existing = _currentSong.value
        if (existing != null) {
            val builder = MediaMetadata.Builder()
                .setTitle(title ?: existing.title)
                .setArtist(artist ?: existing.artist)
                .setArtworkUri(existing.artworkUri)
                .setExtras(existing.extras)
            _currentSong.value = builder.build()
        }
    }


    // --- NEW: Logic to update the play count in the database ---
    private suspend fun incrementPlayCount(mediaItem: MediaItem) {
        try {
            val songId = mediaItem.mediaId.toLongOrNull() ?: return
            val title = mediaItem.mediaMetadata.title.toString()
            val artist = mediaItem.mediaMetadata.artist.toString()

            val currentStat = database.getStatById(songId)

            if (currentStat == null) {
                val newStat = SongStat(
                    id = songId,
                    title = title,
                    artist = artist,
                    playCount = 1,
                    totalPlayTimeMs = 0
                )
                database.upsertStat(newStat)
            } else {
                val updatedStat = currentStat.copy(
                    playCount = currentStat.playCount + 1
                )
                database.upsertStat(updatedStat)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        try {
            mediaController?.removeListener(playerListener)
            MediaController.releaseFuture(mediaControllerFuture)
        } catch (e: Exception) {
            // ignore cleanup issues
        }
        super.onCleared()
    }
}

/**
 * Factory (unchanged)
 */
class PlayerViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlayerViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
