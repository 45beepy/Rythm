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
    // The "scope" for our coroutines, which runs them in the background (Dispatchers.IO)
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // Get an instance of our database "doorman" (the DAO)
    private val database by lazy { StatsDatabase.getDatabase(this).songStatDao() }
    // Get an instance of our *new* history doorman
    private val historyDatabase by lazy { StatsDatabase.getDatabase(this).playbackHistoryDao() }

    // This 'onCreate' is called when the Service is first created.
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. Create the ExoPlayer instance.
        player = ExoPlayer.Builder(this).build()

        // 2. Create the MediaSession.
        mediaSession = MediaSession.Builder(this, player)
            // This tells the session to automatically update its
            // metadata (like title, artist) from the MediaItem.
            .setCallback(object : MediaSession.Callback {
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    // This is for Android Auto, etc. We can just allow it
                    // by calling the default implementation from the parent class.
                    return super.onPlaybackResumption(mediaSession, controller)
                }
            })
            .build()


        // --- NEW CODE: Listen for player events ---
        player.addListener(object : Player.Listener {

            // This is called when a *new* song starts playing
            override fun onIsPlayingChanged(playing: Boolean) {
                super.onIsPlayingChanged(playing)

                // We only care about when the player *starts* playing
                if (playing) {
                    player.currentMediaItem?.let { startedSong ->
                        // Update its stats in the database!
                        logSongPlay(startedSong)
                    }
                }
            }
        })
        // --- END OF NEW CODE ---
    }

    // This is called when our UI (the Activity)
    // tries to connect to this service.
    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession { // Return the non-nullable MediaSession
        return mediaSession
    }

    // This 'onDestroy' is called when the Service is being shut down.
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

    private fun logSongPlay(mediaItem: MediaItem) {
        serviceScope.launch {
            try {
                // Get the song's ID and details
                val songId = mediaItem.mediaId.toLongOrNull() ?: return@launch
                val title = mediaItem.mediaMetadata.title.toString()
                val artist = mediaItem.mediaMetadata.artist.toString()

                // We don't know the duration yet, so we'll log it when it's ready.
                // For now, let's just log the history item.

                // --- THIS IS THE NEW PART ---
                // Log this play event to our history table
                val historyItem = PlaybackHistory(
                    songId = songId,
                    title = title,
                    artist = artist,
                    timestamp = System.currentTimeMillis() // Log the exact time it started
                )
                historyDatabase.addHistoryItem(historyItem)
                // --- END OF NEW PART ---

                // --- This is your existing stats logic ---
                // We can still update the play count here
                val currentStat = database.getStatById(songId)
                if (currentStat == null) {
                    val newStat = SongStat(
                        id = songId,
                        title = title,
                        artist = artist,
                        playCount = 1,
                        totalPlayTimeMs = 0 // We don't know duration yet
                    )
                    database.upsertStat(newStat)
                } else {
                    val updatedStat = currentStat.copy(
                        playCount = currentStat.playCount + 1
                        // We're no longer updating totalPlayTimeMs here
                    )
                    database.upsertStat(updatedStat)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}