package com.wavymusic.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.wavymusic.app.playback.PlaybackConnectionStatus.CONNECTED
import com.wavymusic.app.playback.PlaybackConnectionStatus.CONNECTING
import com.wavymusic.app.playback.PlaybackConnectionStatus.DISCONNECTED
import com.wavymusic.app.playback.PlaybackStatus.BUFFERING
import com.wavymusic.app.playback.PlaybackStatus.ENDED
import com.wavymusic.app.playback.PlaybackStatus.ERROR
import com.wavymusic.app.playback.PlaybackStatus.IDLE
import com.wavymusic.app.playback.PlaybackStatus.PAUSED
import com.wavymusic.app.playback.PlaybackStatus.PLAYING
import com.google.common.util.concurrent.ListenableFuture
import java.net.URI
import java.util.Locale
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private sealed interface SourceResolution {
    data class Resolved(val uri: Uri) : SourceResolution
    data class Rejected(val failure: PlaybackFailure) : SourceResolution
}

private object PlaybackSourceResolver {

    fun resolve(source: PlaybackSource): SourceResolution {
        if (
            source is PlaybackSource.Https &&
            source.expiresAtEpochMs != null &&
            source.expiresAtEpochMs <= System.currentTimeMillis()
        ) {
            return SourceResolution.Rejected(
                PlaybackFailure(
                    PlaybackFailureCode.EXPIRED_URI,
                    "The stream URL has expired. Resolve the source again."
                )
            )
        }

        val parsed = try {
            URI(source.uri)
        } catch (_: Exception) {
            return rejected(
                PlaybackFailureCode.INVALID_URI,
                "The media URI is malformed."
            )
        }

        val scheme = parsed.scheme?.lowercase(Locale.ROOT)
            ?: return rejected(
                PlaybackFailureCode.INVALID_URI,
                "The media URI must be absolute."
            )

        return when (source) {
            is PlaybackSource.Https -> {
                if (scheme != "https" || parsed.host.isNullOrBlank()) {
                    rejected(
                        PlaybackFailureCode.UNSUPPORTED_SCHEME,
                        "Remote media requires a valid HTTPS URI."
                    )
                } else {
                    SourceResolution.Resolved(Uri.parse(source.uri))
                }
            }

            is PlaybackSource.Local -> {
                val validContent =
                    scheme == "content" &&
                        !parsed.rawAuthority.isNullOrBlank()

                val validFile =
                    scheme == "file" &&
                        !parsed.path.isNullOrBlank()

                if (validContent || validFile) {
                    SourceResolution.Resolved(Uri.parse(source.uri))
                } else {
                    rejected(
                        PlaybackFailureCode.UNSUPPORTED_SCHEME,
                        "Local media must use a valid content:// or file:// URI."
                    )
                }
            }
        }
    }

    private fun rejected(
        code: PlaybackFailureCode,
        message: String
    ) = SourceResolution.Rejected(PlaybackFailure(code, message))
}

class PlaybackController(context: Context) : Player.Listener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command ->
        mainHandler.post(command)
    }

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var closed = false
    private var progressScheduled = false
    private var reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
    private var playbackFailure: PlaybackFailure? = null

    private val knownTracks = mutableMapOf<String, PlaybackTrack>()

    private val reconnectRunnable = Runnable {
        connect()
    }

    private val progressRunnable = Runnable {
        progressScheduled = false
        controller?.let(::syncState)
    }

    companion object {
        private const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L

        const val DEVELOPMENT_TEST_AUDIO_URL =
            "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"

        val supportedPlaybackSpeeds = listOf(
            0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f
        )
    }

    @MainThread
    fun connect() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { connect() }
            return
        }

        if (closed || controller != null) return
        if (controllerFuture?.isDone == false) return

        _state.update { it.copy(connection = CONNECTING) }

        val token = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java)
        )

        val future = MediaController.Builder(appContext, token)
            .setListener(
                object : MediaController.Listener {
                    override fun onDisconnected(
                        controller: MediaController
                    ) {
                        handleDisconnection(controller)
                    }
                }
            )
            .buildAsync()

        controllerFuture = future

        future.addListener(
            {
                if (controllerFuture !== future) return@addListener

                controllerFuture = null

                if (closed) {
                    MediaController.releaseFuture(future)
                    return@addListener
                }

                try {
                    val connected = future.get()
                    if (closed) {
                        connected.release()
                        return@addListener
                    }

                    controller = connected
                    connected.addListener(this)
                    reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
                    mainHandler.removeCallbacks(reconnectRunnable)

                    _state.update {
                        it.copy(
                            connection = CONNECTED,
                            failure = if (
                                it.failure?.code ==
                                PlaybackFailureCode.CONNECTION
                            ) null else it.failure
                        )
                    }

                    syncState(connected)
                } catch (error: Exception) {
                    _state.update {
                        it.copy(
                            connection = DISCONNECTED,
                            failure = PlaybackFailure(
                                PlaybackFailureCode.CONNECTION,
                                error.cause?.message
                                    ?: error.message
                                    ?: "Unable to connect to the playback service."
                            )
                        )
                    }
                    scheduleReconnect()
                }
            },
            mainExecutor
        )
    }

    private fun handleDisconnection(disconnected: MediaController) {
        if (controller !== disconnected) return

        controller = null
        disconnected.removeListener(this)
        disconnected.release()

        cancelProgressUpdates()

        _state.update {
            it.copy(
                connection = DISCONNECTED,
                failure = PlaybackFailure(
                    PlaybackFailureCode.CONNECTION,
                    "Playback disconnected. Reconnecting…"
                )
            )
        }

        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (closed) return

        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.postDelayed(reconnectRunnable, reconnectDelayMs)

        reconnectDelayMs =
            (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }

    fun playDevelopmentTestTrack() {
        setQueue(
            tracks = listOf(
                PlaybackTrack(
                    id = "exoplayer-development-fixture",
                    title = "Development Test Audio",
                    artist = "ExoPlayer test media",
                    album = "Wavy development fixture",
                    source = PlaybackSource.Https(
                        DEVELOPMENT_TEST_AUDIO_URL
                    )
                )
            ),
            startIndex = 0,
            autoplay = true
        )
    }

    fun setQueue(
        tracks: List<PlaybackTrack>,
        startIndex: Int = 0,
        autoplay: Boolean = false
    ) {
        withController { active ->
            if (tracks.isEmpty()) {
                active.pause()
                active.clearMediaItems()
                knownTracks.clear()
                playbackFailure = null
                _state.update { it.copy(failure = null) }
                syncState(active)
                return@withController
            }

            if (
                startIndex !in tracks.indices ||
                tracks.any {
                    it.id.isBlank() ||
                        it.title.isBlank() ||
                        it.queueItemId.isBlank()
                } ||
                tracks.map { it.queueItemId }.toSet().size != tracks.size
            ) {
                publishFailure(
                    PlaybackFailure(
                        PlaybackFailureCode.INVALID_QUEUE,
                        "The queue contains invalid or duplicate item identities."
                    )
                )
                return@withController
            }

            val resolved = mutableListOf<MediaItem>()
            var rejected: PlaybackFailure? = null

            for (track in tracks) {
                when (val result = PlaybackSourceResolver.resolve(track.source)) {
                    is SourceResolution.Resolved -> {
                        val metadata = MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setAlbumTitle(track.album)
                            .build()

                        resolved += MediaItem.Builder()
                            .setMediaId(track.queueItemId)
                            .setUri(result.uri)
                            .setMediaMetadata(metadata)
                            .build()
                    }

                    is SourceResolution.Rejected -> {
                        rejected = result.failure
                        break
                    }
                }
            }

            if (rejected != null) {
                publishFailure(rejected!!)
                return@withController
            }

            playbackFailure = null
            knownTracks.clear()
            tracks.forEach { knownTracks[it.queueItemId] = it }

            if (!autoplay) active.pause()

            active.setMediaItems(resolved, startIndex, 0L)
            _state.update { it.copy(failure = null) }

            if (autoplay) {
                active.prepare()
                active.play()
            }

            syncState(active)
        }
    }

    fun play() = withController { active ->
        playbackFailure = null

        if (
            active.mediaItemCount > 0 &&
            active.playbackState == Player.STATE_IDLE
        ) {
            active.prepare()
        }

        active.play()
        syncState(active)
    }

    fun pause() = withController { active ->
        active.pause()
        syncState(active)
    }

    fun togglePlayback() {
        if (_state.value.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) = withController { active ->
        if (active.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            val target = positionMs.coerceAtLeast(0L)
            val duration = active.duration

            active.seekTo(
                if (duration != C.TIME_UNSET && duration >= 0L) {
                    target.coerceAtMost(duration)
                } else {
                    target
                }
            )
        }
        syncState(active)
    }

    fun skipNext() = withController { active ->
        if (active.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
            active.seekToNextMediaItem()
        }
        syncState(active)
    }

    fun skipPrevious() = withController { active ->
        if (active.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
            active.seekToPreviousMediaItem()
        }
        syncState(active)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) =
        withController { active ->
            if (
                fromIndex !in 0 until active.mediaItemCount ||
                toIndex !in 0 until active.mediaItemCount
            ) {
                publishFailure(
                    PlaybackFailure(
                        PlaybackFailureCode.INVALID_QUEUE,
                        "The requested queue position is invalid."
                    )
                )
                return@withController
            }

            if (active.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) {
                active.moveMediaItem(fromIndex, toIndex)
            }
            syncState(active)
        }

    fun setShuffleEnabled(enabled: Boolean) =
        withController { active ->
            if (active.isCommandAvailable(Player.COMMAND_SET_SHUFFLE_MODE)) {
                active.shuffleModeEnabled = enabled
            }
            syncState(active)
        }

    fun setRepeatMode(mode: PlaybackRepeatMode) =
        withController { active ->
            if (active.isCommandAvailable(Player.COMMAND_SET_REPEAT_MODE)) {
                active.repeatMode = when (mode) {
                    PlaybackRepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    PlaybackRepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    PlaybackRepeatMode.ALL -> Player.REPEAT_MODE_ALL
                }
            }
            syncState(active)
        }

    fun setPlaybackSpeed(speed: Float) =
        withController { active ->
            if (supportedPlaybackSpeeds.none { it == speed }) {
                publishFailure(
                    PlaybackFailure(
                        PlaybackFailureCode.UNSUPPORTED_SPEED,
                        "Choose a supported playback speed."
                    )
                )
                return@withController
            }

            if (active.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) {
                active.setPlaybackSpeed(speed)
            }
            syncState(active)
        }

    override fun onEvents(player: Player, events: Player.Events) {
        syncState(player)
    }

    override fun onPlayerError(error: PlaybackException) {
        playbackFailure = mapPlaybackFailure(error)
        controller?.let(::syncState) ?: publishFailure(playbackFailure!!)
    }

    private fun mapPlaybackFailure(error: PlaybackException): PlaybackFailure {
        var cause: Throwable? = error

        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                val code = when (cause.responseCode) {
                    401, 403 -> PlaybackFailureCode.AUTHORIZATION
                    404, 410 -> PlaybackFailureCode.SOURCE_UNAVAILABLE
                    else -> PlaybackFailureCode.NETWORK
                }

                return PlaybackFailure(
                    code,
                    when (code) {
                        PlaybackFailureCode.AUTHORIZATION ->
                            "The source did not authorize this request."
                        PlaybackFailureCode.SOURCE_UNAVAILABLE ->
                            "The media source is unavailable or expired."
                        else -> "The media server rejected the request."
                    }
                )
            }
            cause = cause.cause
        }

        val errorName = error.errorCodeName.uppercase(Locale.ROOT)

        val code = when {
            "NETWORK" in errorName ||
                "CONNECTION" in errorName ||
                "TIMEOUT" in errorName ->
                PlaybackFailureCode.NETWORK
            "DECODING_FORMAT_UNSUPPORTED" in errorName ->
                PlaybackFailureCode.UNSUPPORTED_CODEC
            "DECODER" in errorName ->
                PlaybackFailureCode.DECODER_INITIALIZATION
            "FILE_NOT_FOUND" in errorName ->
                PlaybackFailureCode.SOURCE_UNAVAILABLE
            else -> PlaybackFailureCode.SOURCE_ERROR
        }

        return PlaybackFailure(
            code,
            error.message ?: "Playback failed. Try another authorized source."
        )
    }

    private fun syncState(player: Player) {
        val current = player.currentMediaItem
        val index = player.currentMediaItemIndex
            .takeIf { it in 0 until player.mediaItemCount }
            ?: -1

        val queue = (0 until player.mediaItemCount).map { position ->
            trackFor(player.getMediaItemAt(position))
        }

        val duration = player.duration.let {
            if (it == C.TIME_UNSET || it < 0L) 0L else it
        }

        val status = when {
            playbackFailure != null -> ERROR
            player.playbackState == Player.STATE_BUFFERING -> BUFFERING
            player.playbackState == Player.STATE_ENDED -> ENDED
            player.playbackState == Player.STATE_IDLE -> IDLE
            player.isPlaying -> PLAYING
            else -> PAUSED
        }

        _state.value = PlaybackUiState(
            connection = CONNECTED,
            status = status,
            currentTrack = current?.let(::trackFor),
            queue = queue,
            currentQueueIndex = index,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
            isPlaying = player.isPlaying,
            isShuffleEnabled = player.shuffleModeEnabled,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> PlaybackRepeatMode.ONE
                Player.REPEAT_MODE_ALL -> PlaybackRepeatMode.ALL
                else -> PlaybackRepeatMode.OFF
            },
            playbackSpeed = player.playbackParameters.speed,
            failure = playbackFailure
                ?: _state.value.failure?.takeIf {
                    it.code == PlaybackFailureCode.CONNECTION
                }
        )

        if (player.isPlaying && !progressScheduled) {
            progressScheduled = true
            mainHandler.postDelayed(progressRunnable, 500L)
        } else if (!player.isPlaying) {
            cancelProgressUpdates()
        }
    }

    private fun trackFor(item: MediaItem): PlaybackTrack {
        knownTracks[item.mediaId]?.let { return it }

        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        val source = if (uri.startsWith("https://", ignoreCase = true)) {
            PlaybackSource.Https(uri)
        } else {
            PlaybackSource.Local(uri)
        }

        return PlaybackTrack(
            id = item.mediaId,
            queueItemId = item.mediaId,
            title = item.mediaMetadata.title?.toString()
                ?.takeIf { it.isNotBlank() } ?: "Unknown title",
            artist = item.mediaMetadata.artist?.toString()
                ?.takeIf { it.isNotBlank() } ?: "Unknown artist",
            album = item.mediaMetadata.albumTitle?.toString(),
            source = source
        )
    }

    private fun cancelProgressUpdates() {
        mainHandler.removeCallbacks(progressRunnable)
        progressScheduled = false
    }

    private fun publishFailure(failure: PlaybackFailure) {
        playbackFailure = failure
        _state.update { it.copy(status = ERROR, failure = failure) }
    }

    private fun withController(action: (MediaController) -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            executeWithController(action)
        } else {
            mainHandler.post { executeWithController(action) }
        }
    }

    private fun executeWithController(action: (MediaController) -> Unit) {
        val active = controller

        if (active == null) {
            publishFailure(
                PlaybackFailure(
                    PlaybackFailureCode.CONNECTION,
                    "Playback is connecting. Try again shortly."
                )
            )
            connect()
            return
        }

        action(active)
    }

    @MainThread
    fun close() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { close() }
            return
        }

        if (closed) return

        closed = true
        mainHandler.removeCallbacks(reconnectRunnable)
        cancelProgressUpdates()

        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null

        controller?.let {
            it.removeListener(this)
            it.release()
        }
        controller = null

        _state.update {
            it.copy(connection = DISCONNECTED)
        }
    }
}