package com.example.rythm

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession.Callback
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {

    // Use lateinit for variables that will be initialized in onCreate
    private lateinit var mediaSession: MediaSession
    private lateinit var player: Player

    // This 'onCreate' is called when the Service is first created.
    override fun onCreate() {
        super.onCreate()

        // 1. Create the ExoPlayer instance.
        player = ExoPlayer.Builder(this).build()

        // 2. Create the MediaSession.
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                // --- CORRECTED CODE ---
                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): ListenableFuture<MediaItemsWithStartPosition> {
                    // This is where you would restore the last played media item and position.
                    // For now, we'll return an empty list to allow resumption without a specific item.
                    return Futures.immediateFuture(
                        MediaItemsWithStartPosition(
                            emptyList(),
                            /* startIndex = */ 0,
                            /* startPositionMs = */ 0
                        )
                    )
                }
            })
            // --- END CORRECTED CODE ---
            .build()
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
        super.onDestroy()
    }
}
