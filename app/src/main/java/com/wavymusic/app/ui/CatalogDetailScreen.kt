
package com.wavymusic.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wavymusic.app.catalog.model.CatalogAlbum
import com.wavymusic.app.catalog.model.CatalogArtist
import com.wavymusic.app.ui.theme.WavyColors

@Composable
fun CatalogDetailScreen(
    state: CatalogDetailUiState,
    fallbackTitle: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Back to search")
        }

        when (state) {
            CatalogDetailUiState.Loading -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = WavyColors.Surface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Loading catalog details…",
                            modifier = Modifier.padding(start = 16.dp),
                            color = WavyColors.SecondaryText
                        )
                    }
                }
            }

            CatalogDetailUiState.NotFound -> {
                DetailCard(title = fallbackTitle ?: "Details unavailable") {
                    Text(
                        text = "This item is not available in the local catalog cache. " +
                            "Return to Search and try again while online.",
                        color = WavyColors.SecondaryText
                    )
                }
            }

            is CatalogDetailUiState.Error -> {
                DetailCard(title = fallbackTitle ?: "Unable to load details") {
                    Text(
                        text = state.message,
                        color = WavyColors.SecondaryText
                    )
                }
            }

            is CatalogDetailUiState.ArtistLoaded -> {
                ArtistDetails(
                    artist = state.artist,
                    context = context
                )
            }

            is CatalogDetailUiState.AlbumLoaded -> {
                AlbumDetails(
                    album = state.album,
                    context = context
                )
            }
        }
    }
}

@Composable
private fun ArtistDetails(
    artist: CatalogArtist,
    context: Context
) {
    DetailCard(title = artist.name) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = WavyColors.Cyan
            )
            Text(
                text = "Artist",
                modifier = Modifier.padding(start = 10.dp),
                color = WavyColors.SecondaryText
            )
        }

        artist.description?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, color = WavyColors.SecondaryText)
        }

        Text(
            text = "Provider identities",
            fontWeight = FontWeight.SemiBold
        )

        if (artist.providerIds.isEmpty()) {
            Text(
                text = "No provider IDs were stored.",
                color = WavyColors.SecondaryText
            )
        } else {
            artist.providerIds.forEach { identity ->
                Text(
                    text = "${identity.providerId} · ${identity.itemId}",
                    color = WavyColors.SecondaryText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        CatalogLinkButton(
            url = artist.sources.firstNotNullOfOrNull {
                it.catalogUri?.takeIf(String::isNotBlank)
            },
            context = context
        )
    }
}

@Composable
private fun AlbumDetails(
    album: CatalogAlbum,
    context: Context
) {
    DetailCard(title = album.title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.Album,
                contentDescription = null,
                tint = WavyColors.Cyan
            )
            Text(
                text = "Album",
                modifier = Modifier.padding(start = 10.dp),
                color = WavyColors.SecondaryText
            )
        }

        if (album.artistNames.isNotEmpty()) {
            DetailFact(
                label = "Artist",
                value = album.artistNames.joinToString(" • ")
            )
        }

        album.releaseYear?.let {
            DetailFact(label = "Release year", value = it.toString())
        }

        album.releaseDate?.let {
            DetailFact(label = "Release date", value = it.take(10))
        }

        DetailFact(
            label = "Release type",
            value = album.releaseType.name.lowercase()
                .replaceFirstChar(Char::uppercase)
        )

        album.trackCount?.let {
            DetailFact(label = "Tracks", value = it.toString())
        }

        Text(
            text = "Catalog metadata only. Track playback is not verified.",
            color = WavyColors.SecondaryText,
            style = MaterialTheme.typography.bodySmall
        )

        CatalogLinkButton(
            url = album.sources.firstNotNullOfOrNull {
                it.catalogUri?.takeIf(String::isNotBlank)
            },
            context = context
        )
    }
}

@Composable
private fun CatalogLinkButton(
    url: String?,
    context: Context
) {
    if (url.isNullOrBlank()) return

    Button(
        onClick = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Open catalog page")
    }
}

@Composable
private fun DetailFact(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = WavyColors.SecondaryText
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = WavyColors.PrimaryText
        )
    }
}

@Composable
private fun DetailCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = WavyColors.Surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = WavyColors.PrimaryText
            )
            content()
        }
    }
}
