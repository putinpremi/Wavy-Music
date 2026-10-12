package com.wavymusic.app.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val servicePlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                audioAttributes,
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        servicePlayer.repeatMode = Player.REPEAT_MODE_OFF
        servicePlayer.shuffleModeEnabled = false

        player = servicePlayer

        mediaSession = MediaSession.Builder(
            this,
            servicePlayer
        ).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null

        player?.release()
        player = null

        super.onDestroy()
    }
}