package com.example.rythm

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    // Use lateinit for variables that will be initialized in onCreate
    private lateinit var mediaSession: MediaSession
    private lateinit var player: Player

    // This 'onCreate' is called when the Service is first created.
    override fun onCreate() {
        super.onCreate()

        // 1. Create the ExoPlayer instance. This is the actual "player."
        player = ExoPlayer.Builder(this).build()

        // 2. Create the MediaSession. Now we don't need the '!!' operator
        //    because 'player' is guaranteed to be initialized.
        mediaSession = MediaSession.Builder(this, player)
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
