package com.wavymusic.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wavymusic.app.playback.PlaybackConnectionStatus
import com.wavymusic.app.playback.PlaybackController
import com.wavymusic.app.playback.PlaybackRepeatMode
import com.wavymusic.app.playback.PlaybackStatus
import com.wavymusic.app.playback.PlaybackUiState
import com.wavymusic.app.ui.theme.WavyColors
import com.wavymusic.app.ui.theme.WavyWaveMark

@Composable
fun MiniPlayerBar(
    state: PlaybackUiState,
    onOpenPlayer: () -> Unit,
    onTogglePlayback: () -> Unit
) {
    val track = state.currentTrack ?: return

    Surface(
        color = WavyColors.SurfaceElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WavyWaveMark(
                modifier = Modifier.size(width = 42.dp, height = 32.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        role = Role.Button,
                        onClick = onOpenPlayer
                    )
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = track.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = WavyColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onTogglePlayback,
                enabled = state.connection == PlaybackConnectionStatus.CONNECTED
            ) {
                Icon(
                    imageVector = if (state.isPlaying) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = if (state.isPlaying) {
                        "Pause playback"
                    } else {
                        "Resume playback"
                    },
                    tint = WavyColors.Cyan
                )
            }
        }
    }
}

@Composable
fun NowPlayingScreen(
    state: PlaybackUiState,
    onPlayTestTrack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onOpenQueue: () -> Unit
) {
    val track = state.currentTrack

    var sliderPosition by remember(track?.queueItemId) {
        mutableFloatStateOf(state.positionMs.toFloat())
    }
    var isSeeking by remember(track?.queueItemId) {
        mutableStateOf(false)
    }

    LaunchedEffect(state.positionMs, isSeeking) {
        if (!isSeeking) {
            sliderPosition = state.positionMs.toFloat()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "NOW PLAYING",
            color = WavyColors.Cyan,
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = 2.sp
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(
                            WavyColors.SurfaceElevated,
                            WavyColors.Surface,
                            androidx.compose.ui.graphics.Color(0xFF292344)
                        )
                    )
                )
                .heightIn(min = 190.dp, max = 300.dp),
            contentAlignment = Alignment.Center
        ) {
            if (track == null) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    WavyWaveMark(
                        modifier = Modifier.size(width = 118.dp, height = 84.dp)
                    )

                    Text(
                        text = "Your sound starts here",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Try the clearly labelled development audio fixture.",
                        color = WavyColors.SecondaryText,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = onPlayTestTrack,
                        enabled = state.connection ==
                            PlaybackConnectionStatus.CONNECTED
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Play test audio")
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WavyWaveMark(
                        modifier = Modifier.size(width = 128.dp, height = 88.dp)
                    )
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = WavyColors.SecondaryText,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = track.album ?: "Album unavailable",
                        color = WavyColors.MutedText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (track != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderPosition.coerceIn(
                        0f,
                        state.durationMs.toFloat().coerceAtLeast(1f)
                    ),
                    onValueChange = {
                        isSeeking = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        onSeekTo(sliderPosition.toLong())
                        isSeeking = false
                    },
                    valueRange = 0f..state.durationMs.toFloat()
                        .coerceAtLeast(1f),
                    enabled = state.durationMs > 0 &&
                        state.connection == PlaybackConnectionStatus.CONNECTED,
                    modifier = Modifier.semantics {
                        contentDescription = "Playback position"
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatPlaybackTime(sliderPosition.toLong()),
                        color = WavyColors.SecondaryText,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        formatPlaybackTime(state.durationMs),
                        color = WavyColors.SecondaryText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = if (state.isShuffleEnabled) {
                            "Turn shuffle off"
                        } else {
                            "Turn shuffle on"
                        },
                        tint = if (state.isShuffleEnabled) {
                            WavyColors.Cyan
                        } else {
                            WavyColors.SecondaryText
                        }
                    )
                }

                IconButton(onClick = onSkipPrevious) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous track",
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = onTogglePlayback,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(WavyColors.Cyan),
                    enabled = state.connection ==
                        PlaybackConnectionStatus.CONNECTED
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = if (state.isPlaying) {
                            "Pause playback"
                        } else {
                            "Resume playback"
                        },
                        tint = WavyColors.Background,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = onSkipNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next track",
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = "Repeat mode ${state.repeatMode.name.lowercase()}",
                        tint = if (state.repeatMode != PlaybackRepeatMode.OFF) {
                            WavyColors.Cyan
                        } else {
                            WavyColors.SecondaryText
                        }
                    )
                }
            }

            PlaybackSpeedMenu(
                speed = state.playbackSpeed,
                onSetSpeed = onSetSpeed
            )

            OutlinedButton(
                onClick = onOpenQueue,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.QueueMusic,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text("View queue (${state.queue.size})")
            }
        } else {
            Text(
                text = when (state.connection) {
                    PlaybackConnectionStatus.CONNECTING ->
                        "Connecting to playback…"
                    PlaybackConnectionStatus.DISCONNECTED ->
                        "Playback service disconnected"
                    PlaybackConnectionStatus.CONNECTED ->
                        "Ready when you are"
                },
                color = WavyColors.SecondaryText
            )
        }

        Text(
            text = when (state.status) {
                PlaybackStatus.IDLE -> "Idle"
                PlaybackStatus.BUFFERING -> "Buffering…"
                PlaybackStatus.PLAYING -> "Playing"
                PlaybackStatus.PAUSED -> "Paused"
                PlaybackStatus.ENDED -> "Ended"
                PlaybackStatus.ERROR -> "Playback error"
            },
            color = if (state.status == PlaybackStatus.ERROR) {
                WavyColors.Error
            } else {
                WavyColors.SecondaryText
            },
            style = MaterialTheme.typography.labelMedium
        )

        state.failure?.let { failure ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (
                        failure.code == com.wavymusic.app.playback.PlaybackFailureCode.NETWORK
                    ) Icons.Filled.WifiOff else Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = WavyColors.Error
                )
                Text(
                    text = failure.message,
                    color = WavyColors.Error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun PlaybackSpeedMenu(
    speed: Float,
    onSetSpeed: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Speed · ${speed}×")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            PlaybackController.supportedPlaybackSpeeds.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option}×") },
                    onClick = {
                        onSetSpeed(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun QueueScreen(
    state: PlaybackUiState,
    onMoveQueueItem: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.QueueMusic,
                contentDescription = null,
                tint = WavyColors.Cyan,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Your queue",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${state.queue.size} item(s)",
                    color = WavyColors.SecondaryText
                )
            }
        }

        if (state.queue.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                WavyWaveMark(
                    modifier = Modifier.size(width = 100.dp, height = 70.dp)
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Your queue is empty",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Start the development test audio from Now Playing, " +
                        "or add tracks from a supported source when available.",
                    color = WavyColors.SecondaryText
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = state.queue,
                    key = { _, item -> item.queueItemId }
                ) { index, track ->
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (
                                index == state.currentQueueIndex
                            ) {
                                WavyColors.SurfaceElevated
                            } else {
                                WavyColors.Surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (
                                    index == state.currentQueueIndex
                                ) Icons.Filled.GraphicEq
                                else Icons.Filled.QueueMusic,
                                contentDescription = if (
                                    index == state.currentQueueIndex
                                ) "Currently selected track"
                                else "Queued track",
                                tint = WavyColors.Cyan
                            )

                            Spacer(Modifier.width(12.dp))

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = track.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = track.artist,
                                    color = WavyColors.SecondaryText,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Column {
                                IconButton(
                                    enabled = index > 0,
                                    onClick = {
                                        onMoveQueueItem(index, index - 1)
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.ArrowUpward,
                                        contentDescription = "Move ${track.title} up"
                                    )
                                }

                                IconButton(
                                    enabled = index < state.queue.lastIndex,
                                    onClick = {
                                        onMoveQueueItem(index, index + 1)
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.ArrowDownward,
                                        contentDescription = "Move ${track.title} down"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1_000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}