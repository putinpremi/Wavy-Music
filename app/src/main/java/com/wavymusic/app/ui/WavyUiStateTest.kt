package com.wavymusic.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavyUiStateTest {

    @Test
    fun navigationRoutesAreUnique() {
        assertEquals(
            WavyRoutes.all.size,
            WavyRoutes.all.toSet().size
        )
    }

    @Test
    fun navigationIncludesAllInitialDestinations() {
        assertTrue(WavyRoutes.all.contains(WavyRoutes.HOME))
        assertTrue(WavyRoutes.all.contains(WavyRoutes.SEARCH))
        assertTrue(WavyRoutes.all.contains(WavyRoutes.LIBRARY))
        assertTrue(WavyRoutes.all.contains(WavyRoutes.SETTINGS))
        assertTrue(WavyRoutes.all.contains(WavyRoutes.NOW_PLAYING))
        assertTrue(WavyRoutes.all.contains(WavyRoutes.QUEUE))
        assertTrue(WavyRoutes.all.contains(WavyRoutes.LYRICS))
        assertTrue(WavyRoutes.all.contains(WavyRoutes.ARTIST))
        assertTrue(WavyRoutes.all.contains(WavyRoutes.ALBUM))
        assertTrue(WavyRoutes.all.contains(WavyRoutes.DOWNLOADS))
    }

    @Test
    fun searchStartsWithoutInventedCatalogResults() {
        val viewModel = SearchViewModel()
        val initialState = viewModel.uiState.value

        assertEquals("", initialState.query)
        assertEquals(
            CatalogContentState.NotConfigured,
            initialState.contentState
        )
    }

    @Test
    fun searchQueryIsHeldInViewModelState() {
        val viewModel = SearchViewModel()

        viewModel.onQueryChanged("favorite song")

        assertEquals(
            "favorite song",
            viewModel.uiState.value.query
        )

        assertEquals(
            CatalogContentState.NotConfigured,
            viewModel.uiState.value.contentState
        )
    }

    @Test
    fun miniPlayerStaysHiddenWithoutTrackMetadata() {
        assertFalse(MiniPlayerUiState().isVisible)

        assertFalse(
            MiniPlayerUiState(
                title = "   ",
                artist = "Artist"
            ).isVisible
        )
    }

    @Test
    fun miniPlayerBecomesVisibleForARealTrackTitle() {
        val state = MiniPlayerUiState(
            title = "Track title",
            artist = "Artist",
            isPlaying = false
        )

        assertTrue(state.isVisible)
        assertEquals("Track title", state.title)
        assertEquals("Artist", state.artist)
    }

    @Test
    fun catalogStatesRemainDistinct() {
        assertNotEquals(
            CatalogContentState.Empty,
            CatalogContentState.NotConfigured
        )

        assertNotEquals(
            CatalogContentState.Offline,
            CatalogContentState.Loading
        )

        assertNotEquals(
            CatalogContentState.Error("Provider unavailable"),
            CatalogContentState.Empty
        )
    }
}