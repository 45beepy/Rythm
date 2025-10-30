package com.example.rythm // <-- Make sure this matches your package name!

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

// This is our Playback Service.
// It inherits from 'MediaSessionService', which is the base class
// for a background media-playing service.
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    // This 'onCreate' is called when the Service is first created.
    override fun onCreate() {
        super.onCreate()

        // 1. Create the ExoPlayer instance. This is the actual "player."
        player = ExoPlayer.Builder(this).build()

        // 2. Create the MediaSession. This is the "connector"
        //    that lets our UI (and the Android system) talk to the player.
        mediaSession = MediaSession.Builder(this, player!!)
            .build()
    }

    // This is called when our UI (the Activity)
    // tries to connect to this service.
    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? {
        return mediaSession
    }

    // This 'onDestroy' is called when the Service is being shut down.
    // We must release our player and session to free up resources.
    override fun onDestroy() {
        mediaSession?.run {
            player?.release()
            release()
            mediaSession = null
            player = null
        }
        super.onDestroy()
    }
}