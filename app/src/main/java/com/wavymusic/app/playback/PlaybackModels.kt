package com.wavymusic.app.playback

import java.util.UUID

sealed interface PlaybackSource {

    /**
     * An HTTPS stream URL.
     *
     * The track ID remains independent from this URL because stream URLs
     * may expire or be refreshed by an authorized source resolver.
     */
    data class Https(
        val uri: String,
        val expiresAtEpochMs: Long? = null
    ) : PlaybackSource

    /**
     * A local Android content URI or file URI.
     */
    data class Local(
        val uri: String
    ) : PlaybackSource
}

data class PlaybackTrack(
    val id: String,
    val title: String,
    val artist: String,
    val source: PlaybackSource,
    val album: String? = null,

    /**
     * Distinguishes queue entries even when the same track is
     * added more than once.
     */
    val queueItemId: String = UUID.randomUUID().toString()
)

enum class PlaybackConnectionStatus {
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
    ERROR
}

enum class PlaybackRepeatMode {
    OFF,
    ONE,
    ALL
}

enum class PlaybackFailureCode {
    CONNECTION,
    INVALID_URI,
    UNSUPPORTED_SCHEME,
    EXPIRED_URI,
    INVALID_QUEUE,
    NETWORK,
    AUTHORIZATION,
    SOURCE_UNAVAILABLE,
    UNSUPPORTED_CODEC,
    DECODER_INITIALIZATION,
    SOURCE_ERROR,
    UNSUPPORTED_SPEED
}

data class PlaybackFailure(
    val code: PlaybackFailureCode,
    val message: String
)

data class PlaybackUiState(
    val connection: PlaybackConnectionStatus =
        PlaybackConnectionStatus.DISCONNECTED,

    val status: PlaybackStatus = PlaybackStatus.IDLE,

    val currentTrack: PlaybackTrack? = null,
    val queue: List<PlaybackTrack> = emptyList(),
    val currentQueueIndex: Int = -1,

    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,

    val isPlaying: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: PlaybackRepeatMode =
        PlaybackRepeatMode.OFF,

    val playbackSpeed: Float = 1f,

    val failure: PlaybackFailure? = null
)