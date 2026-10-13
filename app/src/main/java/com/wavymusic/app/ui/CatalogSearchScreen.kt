package com.wavymusic.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wavymusic.app.catalog.CatalogSearchHit
import com.wavymusic.app.catalog.CatalogSearchItemType
import com.wavymusic.app.catalog.provider.CatalogSearchType
import com.wavymusic.app.catalog.storage.CatalogRecentSearchEntity
import com.wavymusic.app.ui.theme.WavyColors

@Composable
fun CatalogSearchScreen(
    state: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onSearchTypeChanged: (CatalogSearchType) -> Unit,
    onRecentSearchSelected: (CatalogRecentSearchEntity) -> Unit,
    onClearRecentSearches: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenResult: (CatalogSearchHit) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = WavyColors.PrimaryText
        )

        Text(
            text = "Find songs, artists, albums, and playlists.",
            style = MaterialTheme.typography.bodyMedium,
            color = WavyColors.SecondaryText
        )

        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search music") },
            placeholder = { Text("Songs, artists, albums") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null
                )
            },
            shape = RoundedCornerShape(18.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SearchTypeChip(
                label = "Songs",
                selected = state.searchType == CatalogSearchType.TRACKS,
                onClick = { onSearchTypeChanged(CatalogSearchType.TRACKS) }
            )
            SearchTypeChip(
                label = "Artists",
                selected = state.searchType == CatalogSearchType.ARTISTS,
                onClick = { onSearchTypeChanged(CatalogSearchType.ARTISTS) }
            )
            SearchTypeChip(
                label = "Albums",
                selected = state.searchType == CatalogSearchType.ALBUMS,
                onClick = { onSearchTypeChanged(CatalogSearchType.ALBUMS) }
            )
            SearchTypeChip(
                label = "Playlists",
                selected = state.searchType == CatalogSearchType.PUBLIC_PLAYLISTS,
                onClick = {
                    onSearchTypeChanged(CatalogSearchType.PUBLIC_PLAYLISTS)
                }
            )
        }

        if (state.query.isBlank() && state.recentSearches.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent searches",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                TextButton(onClick = onClearRecentSearches) {
                    Text("Clear")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.recentSearches.take(5).forEach { recent ->
                    TextButton(
                        onClick = { onRecentSearchSelected(recent) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = recent.queryText,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (state.isRefreshing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (!state.message.isNullOrBlank()) {
            Text(
                text = state.message,
                color = WavyColors.SecondaryText,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (state.results.isEmpty()) {
            SearchStatusPanel(state)
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = state.results,
                key = { hit -> hit.stableKey }
            ) { hit ->
                CatalogResultCard(
                    hit = hit,
                    onOpenResult = onOpenResult
                )
            }

            if (state.continuations.isNotEmpty()) {
                item(key = "catalog-load-more") {
                    Button(
                        onClick = onLoadMore,
                        enabled = !state.isLoadingMore &&
                            !state.isRefreshing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        if (state.isLoadingMore) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }

                        Text(
                            if (state.isLoadingMore) {
                                "Loading more…"
                            } else {
                                "Load more"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun SearchStatusPanel(state: SearchUiState) {
    val message = when (state.status) {
        CatalogSearchUiStatus.IDLE ->
            "Enter a search to get started."

        CatalogSearchUiStatus.LOADING ->
            "Searching the catalog…"

        CatalogSearchUiStatus.RESULTS ->
            "No visible results."

        CatalogSearchUiStatus.EMPTY ->
            "No results were returned for this search."

        CatalogSearchUiStatus.NOT_CONFIGURED ->
            "No live catalog provider is connected. Your query is not sent " +
                "to a music service until a provider transport is configured."

        CatalogSearchUiStatus.PROVIDER_UNAVAILABLE ->
            state.message ?: "No connected provider supports this search."

        CatalogSearchUiStatus.OFFLINE ->
            state.message ?: "The catalog could not be reached. Check your connection."

        CatalogSearchUiStatus.RATE_LIMITED -> {
            val retrySeconds = state.retryAfterMs?.let {
                (it + 999L) / 1_000L
            }

            if (retrySeconds != null) {
                "The catalog has rate-limited requests. Try again in " +
                    "$retrySeconds seconds."
            } else {
                state.message ?: "The catalog has temporarily rate-limited requests."
            }
        }

        CatalogSearchUiStatus.ERROR ->
            state.message ?: "The search could not be completed."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = WavyColors.Surface
        )
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.status == CatalogSearchUiStatus.LOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(12.dp))
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = WavyColors.SecondaryText
            )
        }
    }
}

@Composable
private fun CatalogResultCard(
    hit: CatalogSearchHit,
    onOpenResult: (CatalogSearchHit) -> Unit
) {
    val icon: ImageVector = when (hit.itemType) {
        CatalogSearchItemType.TRACK,
        CatalogSearchItemType.UNKNOWN -> Icons.Filled.MusicNote

        CatalogSearchItemType.ARTIST -> Icons.Filled.Person
        CatalogSearchItemType.ALBUM -> Icons.Filled.Album
        CatalogSearchItemType.PLAYLIST -> Icons.Filled.LibraryMusic
    }

    // A catalog track does not contain a verified playable source in this
    // search-hit model. Only artist and album results have existing detail
    // destinations connected by this phase.
    val canOpenDetails =
        hit.itemType == CatalogSearchItemType.ARTIST ||
            hit.itemType == CatalogSearchItemType.ALBUM
    
    val sourceLabel = when (hit.providerId) {
        "itunes-search" -> "Apple iTunes"
        "deezer-public" -> "Deezer"
        else -> hit.providerId
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (canOpenDetails) {
                    Modifier.clickable { onOpenResult(hit) }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = WavyColors.Surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WavyColors.Cyan,
                modifier = Modifier.size(30.dp)
            )

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = hit.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!hit.subtitle.isNullOrBlank()) {
                    Text(
                        text = hit.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = WavyColors.SecondaryText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "Source · $sourceLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = WavyColors.SecondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = when (hit.itemType) {
                        CatalogSearchItemType.TRACK ->
                            "Catalog metadata only · playback is not verified"

                        CatalogSearchItemType.ARTIST ->
                            "Artist · open details"

                        CatalogSearchItemType.ALBUM ->
                            "Album · open details"

                        CatalogSearchItemType.PLAYLIST ->
                            "Playlist · detail screen not connected yet"

                        CatalogSearchItemType.UNKNOWN ->
                            "Catalog item · type not recognized"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = WavyColors.SecondaryText
                )
            }
        }
    }
}