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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var mediaControllerFuture: ListenableFuture<MediaController>
    val mediaController: MediaController?
        get() = if (mediaControllerFuture.isDone) mediaControllerFuture.get() else null

    // --- STATE ---
    private val _currentSong = mutableStateOf<MediaMetadata?>(null)
    private val _isPlaying = mutableStateOf(false)
    private val _currentPosition = mutableStateOf(0L)
    private val _songDuration = mutableStateOf(0L)

    val currentSong: State<MediaMetadata?> = _currentSong
    val isPlaying: State<Boolean> = _isPlaying
    val currentPosition: State<Long> = _currentPosition
    val songDuration: State<Long> = _songDuration

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
        }
        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }
        // onVolumeChanged removed
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                if (_isPlaying.value) {
                    _currentPosition.value = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
                }
                delay(1000L)
            }
        }
    }

    // --- Public Functions ---

    fun onSongClick(songList: List<MediaItem>, songIndex: Int) {
        mediaController?.let {
            it.setMediaItems(songList, songIndex, 0L)
            it.prepare()
            it.play()
        }
    }

    fun playPause() {
        if (_isPlaying.value) {
            mediaController?.pause()
        } else {
            mediaController?.play()
        }
    }

    fun skipNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    // setVolume function removed

    override fun onCleared() {
        mediaController?.removeListener(playerListener)
        MediaController.releaseFuture(mediaControllerFuture)
        super.onCleared()
    }
}

class PlayerViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlayerViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}