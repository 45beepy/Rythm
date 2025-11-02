package com.example.rythm

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


class PlaybackService : MediaSessionService() {

    // Use lateinit for variables that will be initialized in onCreate
    private lateinit var mediaSession: MediaSession
    private lateinit var player: Player

    // This 'onCreate' is called when the Service is first created.
    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. Create the ExoPlayer instance.
        player = ExoPlayer.Builder(this).build()

        // 2. Create the MediaSession.
        mediaSession = MediaSession.Builder(this, player)
            // --- NEW CODE ---
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
