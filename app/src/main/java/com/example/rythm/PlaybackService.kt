package com.example.rythm

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import java.lang.Exception
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    // Use lateinit for variables that will be initialized in onCreate
    private lateinit var mediaSession: MediaSession
    private lateinit var player: Player
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val database by lazy { StatsDatabase.getDatabase(this).songStatDao() }

    // This is the one and only onCreate function
    @OptIn(UnstableApi::class) // <-- This annotation is required
    override fun onCreate() {
        super.onCreate()

        // 1. Create the ExoPlayer instance.
        player = ExoPlayer.Builder(this).build()

        // 2. Create the MediaSession.
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                // This function also needs the OptIn
                @OptIn(UnstableApi::class)
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    return super.onPlaybackResumption(mediaSession, controller)
                }
            })
            .build()

        // 3. Add the listener to the player
        player.addListener(object : Player.Listener {

            // This is called when a song *ends* and moves to the next
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val finishedSongIndex = player.previousMediaItemIndex
                    if (finishedSongIndex != C.INDEX_UNSET) {
                        val finishedSong = player.getMediaItemAt(finishedSongIndex)
                        updateSongStats(finishedSong)
                    }
                }
            }

            // This is called when the *very last* song finishes
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                if (playbackState == Player.STATE_ENDED) {
                    val finishedSongIndex = player.currentMediaItemIndex
                    if (finishedSongIndex != C.INDEX_UNSET) {
                        val finishedSong = player.getMediaItemAt(finishedSongIndex)
                        updateSongStats(finishedSong)
                    }
                }
            }
        })
    } // <-- This is the end of the *only* onCreate function

    // This is called when our UI (the Activity)
    // tries to connect to this service.
    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession { // Return the non-nullable MediaSession
        return mediaSession
    }

    override fun onDestroy() {
        if (::player.isInitialized) {
            player.release()
        }
        if (::mediaSession.isInitialized) {
            mediaSession.release()
        }
        serviceJob.cancel()
        super.onDestroy()
    }

    private fun updateSongStats(mediaItem: MediaItem) {
        serviceScope.launch {
            try {
                val songId = mediaItem.mediaId.toLongOrNull() ?: return@launch
                val duration = player.duration
                val title = mediaItem.mediaMetadata.title.toString()
                val artist = mediaItem.mediaMetadata.artist.toString()

                if (duration < 10000) { // e.g., don't log plays under 10 seconds
                    return@launch
                }

                val currentStat = database.getStatById(songId)

                if (currentStat == null) {
                    // This case is handled by the ViewModel,
                    // but we add play time as a fallback.
                    val newStat = SongStat(
                        id = songId,
                        title = title,
                        artist = artist,
                        playCount = 0, // VM handles play count
                        totalPlayTimeMs = duration
                    )
                    database.upsertStat(newStat)
                } else {
                    // This is the normal case: add to the total play time
                    val updatedStat = currentStat.copy(
                        totalPlayTimeMs = currentStat.totalPlayTimeMs + duration
                    )
                    database.upsertStat(updatedStat)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}