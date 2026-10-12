package com.wavymusic.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object WavyBrand {
    const val NAME = "Wavy Music"
    const val TAGLINE = "Feel every frequency."
    const val APPLICATION_ID = "com.wavymusic.app"

    const val BACKGROUND_ARGB: Long = 0xFF080D19
    const val SURFACE_ARGB: Long = 0xFF172235
    const val CYAN_ARGB: Long = 0xFF48E5FF
    const val VIOLET_ARGB: Long = 0xFF9272FF
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            WavyMusicApp()
        }
    }
}

@Composable
private fun WavyMusicApp() {
    val background = Color(WavyBrand.BACKGROUND_ARGB)
    val surface = Color(WavyBrand.SURFACE_ARGB)
    val cyan = Color(WavyBrand.CYAN_ARGB)
    val violet = Color(WavyBrand.VIOLET_ARGB)

    val colors = darkColorScheme(
        primary = cyan,
        secondary = violet,
        background = background,
        surface = surface,
        onBackground = Color.White,
        onSurface = Color.White
    )

    MaterialTheme(
        colorScheme = colors
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(
                        horizontal = 24.dp,
                        vertical = 28.dp
                    ),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "WAVY",
                    color = cyan,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "MUSIC",
                    color = Color.White,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 52.sp,
                        lineHeight = 58.sp,
                        letterSpacing = (-1).sp
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = WavyBrand.TAGLINE,
                    color = violet,
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "Your sound, in motion.",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "A native Android music experience, " +
                                "designed around sound, discovery, and " +
                                "a player that belongs to you.",
                            color = Color(0xFFB9C6DC),
                            style = MaterialTheme.typography.bodyLarge
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "The Android foundation comes first. " +
                                "Playback, music discovery, and your library " +
                                "will be implemented and tested in separate phases.",
                            color = Color(0xFF8F9DB5),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(cyan)
                    )

                    Spacer(modifier = Modifier.size(10.dp))

                    Text(
                        text = "PHASE 0  •  FOUNDATION",
                        color = cyan,
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 1.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "FEEL EVERY FREQUENCY.",
                    color = Color(0xFF71809B),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}