package com.example.rythm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession.Callback
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.common.util.UnstableApi
import java.lang.Exception
import androidx.media3.common.C


class PlaybackService : MediaSessionService() {

    // Use lateinit for variables that will be initialized in onCreate
    private lateinit var mediaSession: MediaSession
    private lateinit var player: Player
    private val serviceJob = SupervisorJob()
    // 2. The "scope" for our coroutines, which runs them in the background (Dispatchers.IO)
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // 3. Get an instance of our database "doorman" (the DAO)
    private val database by lazy { StatsDatabase.getDatabase(this).songStatDao() }

    // This 'onCreate' is called when the Service is first created.
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. Create the ExoPlayer instance.
        player = ExoPlayer.Builder(this).build()

        // 2. Create the MediaSession.
        mediaSession = MediaSession.Builder(this, player)
            // This is the "nook and corner" we were missing.
            // This tells the session to automatically update its
            // metadata (like title, artist) from the MediaItem.
            .setCallback(object : MediaSession.Callback {
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> { // <-- THIS LINE CHANGED
                    // This is for Android Auto, etc. We can just allow it
                    // by calling the default implementation from the parent class.
                    return super.onPlaybackResumption(mediaSession, controller) // <-- THIS LINE CHANGED
                }
            })
            // --- END NEW CODE ---
            .build()

        // --- NEW CODE: Listen for player events ---
        player.addListener(object : Player.Listener {

            // This is called when a song *ends* and moves to the next
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // The transition is automatic (song finished)
                    // The 'previous' index is the one that just finished
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
                    // The playlist is over. The "current" index is the last song.
                    val finishedSongIndex = player.currentMediaItemIndex
                    if (finishedSongIndex != C.INDEX_UNSET) {
                        val finishedSong = player.getMediaItemAt(finishedSongIndex)
                        updateSongStats(finishedSong)
                    }
                }
            }
        })
    }

    // This is called when our UI (the Activity)
    // tries to connect to this service.
    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession { // Return the non-nullable MediaSession
        return mediaSession
    }

    // This 'onDestroy' is called when the Service is being shut down.
    // We must release our player and session to free up resources.
    //regular comment
    override fun onDestroy() {
        // Check if player has been initialized before releasing
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
        // Launch a new background task
        serviceScope.launch {
            try {
                // Get the song's ID and details
                val songId = mediaItem.mediaId.toLongOrNull() ?: return@launch
                val duration = player.duration
                val title = mediaItem.mediaMetadata.title.toString()
                val artist = mediaItem.mediaMetadata.artist.toString()

                // If the song was too short (e.g., a skip), don't count it
                if (duration < 10000) { // e.g., don't log plays under 10 seconds
                    return@launch
                }

                // 1. GET the current stats from the database
                val currentStat = database.getStatById(songId)

                if (currentStat == null) {
                    // 2a. INSERT a new record if it's the first play
                    val newStat = SongStat(
                        id = songId,
                        title = title,
                        artist = artist,
                        playCount = 1,
                        totalPlayTimeMs = duration
                    )
                    database.upsertStat(newStat)
                } else {
                    // 2b. UPDATE the existing record
                    val updatedStat = currentStat.copy(
                        playCount = currentStat.playCount + 1,
                        totalPlayTimeMs = currentStat.totalPlayTimeMs + duration
                    )
                    database.upsertStat(updatedStat)
                }

            } catch (e: Exception) {
                // Log any errors (optional but good practice)
                e.printStackTrace()
            }
        }
    }
}