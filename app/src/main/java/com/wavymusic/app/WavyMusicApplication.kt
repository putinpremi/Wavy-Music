package com.wavymusic.app

import android.app.Application
import com.wavymusic.app.playback.PlaybackController

class WavyMusicApplication : Application() {

    lateinit var playbackController: PlaybackController
        private set

    override fun onCreate() {
        super.onCreate()

        playbackController = PlaybackController(this)

        // Establish the service connection without starting playback.
        playbackController.connect()
    }
}