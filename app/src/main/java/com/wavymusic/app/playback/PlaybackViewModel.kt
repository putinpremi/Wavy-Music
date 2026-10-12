package com.wavymusic.app.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.wavymusic.app.WavyMusicApplication
import kotlinx.coroutines.flow.StateFlow

class PlaybackViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val controller =
        (application as WavyMusicApplication).playbackController

    val state: StateFlow<PlaybackUiState> = controller.state

    init {
        controller.connect()
    }

    fun playDevelopmentTestTrack() =
        controller.playDevelopmentTestTrack()

    fun togglePlayback() =
        controller.togglePlayback()

    fun play() =
        controller.play()

    fun pause() =
        controller.pause()

    fun seekTo(positionMs: Long) =
        controller.seekTo(positionMs)

    fun skipNext() =
        controller.skipNext()

    fun skipPrevious() =
        controller.skipPrevious()

    fun moveQueueItem(fromIndex: Int, toIndex: Int) =
        controller.moveQueueItem(fromIndex, toIndex)

    fun toggleShuffle() {
        controller.setShuffleEnabled(!state.value.isShuffleEnabled)
    }

    fun cycleRepeatMode() {
        val next = when (state.value.repeatMode) {
            PlaybackRepeatMode.OFF -> PlaybackRepeatMode.ONE
            PlaybackRepeatMode.ONE -> PlaybackRepeatMode.ALL
            PlaybackRepeatMode.ALL -> PlaybackRepeatMode.OFF
        }
        controller.setRepeatMode(next)
    }

    fun setPlaybackSpeed(speed: Float) =
        controller.setPlaybackSpeed(speed)
}