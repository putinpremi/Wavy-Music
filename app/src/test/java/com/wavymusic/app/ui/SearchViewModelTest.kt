package com.wavymusic.app.ui

import android.os.Looper
import com.wavymusic.app.catalog.CatalogSearchDataSource
import com.wavymusic.app.catalog.CatalogSearchHit
import com.wavymusic.app.catalog.CatalogSearchItemType
import com.wavymusic.app.catalog.CatalogSearchState
import com.wavymusic.app.catalog.provider.CatalogContinuation
import com.wavymusic.app.catalog.provider.CatalogPage
import com.wavymusic.app.catalog.provider.CatalogProviderError
import com.wavymusic.app.catalog.provider.CatalogProviderErrorCode
import com.wavymusic.app.catalog.provider.CatalogSearchType
import com.wavymusic.app.catalog.storage.CatalogRecentSearchEntity
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchViewModelTest {

    @Test
    fun configuredSearchIsDebouncedAndPublishesResults() {
        val source = FakeDataSource { call ->
            flowOf(
                CatalogSearchState.Success(
                    items = listOf(trackHit("night-drive", "Night Drive")),
                    continuations = emptyList()
                )
            )
        }

        val viewModel = SearchViewModel(source)

        viewModel.onQueryChanged("  Night Drive ")

        assertTrue(source.calls.isEmpty())

        idleForDebounce()

        assertEquals(1, source.calls.size)
        assertEquals("Night Drive", source.calls.single().query)
        assertEquals(CatalogSearchUiStatus.RESULTS, viewModel.uiState.value.status)
        assertEquals(
            "fixture|TRACK|night-drive",
            viewModel.uiState.value.results.single().stableKey
        )
    }

    @Test
    fun unconfiguredProviderDoesNotPretendToSearch() {
        val source = FakeDataSource(
            isConfigured = false,
            responder = {
                error("An unconfigured provider must not be called.")
            }
        )

        val viewModel = SearchViewModel(source)

        viewModel.onQueryChanged("Night Drive")

        assertEquals(
            CatalogSearchUiStatus.NOT_CONFIGURED,
            viewModel.uiState.value.status
        )
        assertTrue(source.calls.isEmpty())
        assertTrue(viewModel.uiState.value.results.isEmpty())
        assertTrue(
            viewModel.uiState.value.message
                ?.contains("has not been sent") == true
        )
    }

    @Test
    fun supersededQueryIsCancelled() {
        val source = FakeDataSource { call ->
            if (call.query == "first") {
                flow<CatalogSearchState> {
                    emit(CatalogSearchState.Loading(emptyList(), false))
                    awaitCancellation()
                }
            } else {
                flowOf(
                    CatalogSearchState.Success(
                        items = listOf(trackHit("second", "Second")),
                        continuations = emptyList()
                    )
                )
            }
        }

        val viewModel = SearchViewModel(source)

        viewModel.onQueryChanged("first")
        idleForDebounce()

        assertEquals("first", source.calls.single().query)

        viewModel.onQueryChanged("second")
        idleForDebounce()

        assertTrue(source.cancelledQueries.contains("first"))
        assertEquals("second", source.calls.last().query)
        assertEquals(CatalogSearchUiStatus.RESULTS, viewModel.uiState.value.status)
        assertEquals("Second", viewModel.uiState.value.results.single().title)
    }

    @Test
    fun paginationAppendsItemsWithoutDuplicateProviderIdentities() {
        val continuation = CatalogContinuation(
            providerId = "fixture",
            token = "page-2"
        )

        val source = FakeDataSource { call ->
            if (call.continuation == null) {
                flowOf(
                    CatalogSearchState.Success(
                        items = listOf(trackHit("one", "First")),
                        continuations = listOf(continuation)
                    )
                )
            } else {
                flowOf(
                    CatalogSearchState.Success(
                        items = listOf(
                            trackHit("one", "Duplicate title"),
                            trackHit("two", "Second")
                        ),
                        continuations = emptyList()
                    )
                )
            }
        }

        val viewModel = SearchViewModel(source)
        viewModel.onQueryChanged("mix")
        idleForDebounce()

        assertEquals(1, viewModel.uiState.value.results.size)
        assertEquals(1, viewModel.uiState.value.continuations.size)

        viewModel.loadMore()

        assertEquals(2, source.calls.size)
        assertEquals(continuation, source.calls.last().continuation)
        assertEquals(
            listOf("First", "Second"),
            viewModel.uiState.value.results.map { it.title }
        )
        assertTrue(viewModel.uiState.value.continuations.isEmpty())
    }

    @Test
    fun unsupportedSearchTypeIsShownAsUnavailable() {
        val source = FakeDataSource { _ ->
            flowOf(
                CatalogSearchState.Failure(
                    items = emptyList(),
                    error = CatalogProviderError(
                        code = CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
                        message = "Artist search is not supported."
                    )
                )
            )
        }

        val viewModel = SearchViewModel(source)
        viewModel.onSearchTypeChanged(CatalogSearchType.ARTISTS)
        viewModel.onQueryChanged("Nova")
        idleForDebounce()

        assertEquals(
            CatalogSearchUiStatus.PROVIDER_UNAVAILABLE,
            viewModel.uiState.value.status
        )
        assertEquals(
            "Artist search is not supported.",
            viewModel.uiState.value.message
        )
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    private fun idleForDebounce() {
        shadowOf(Looper.getMainLooper()).idleFor(
            Duration.ofMillis(SearchViewModel.SEARCH_DEBOUNCE_MS + 150L)
        )
    }

    private fun trackHit(
        id: String,
        title: String
    ) = CatalogSearchHit(
        providerId = "fixture",
        itemId = id,
        itemType = CatalogSearchItemType.TRACK,
        title = title,
        subtitle = "Fixture artist"
    )

    private data class SearchCall(
        val query: String,
        val type: CatalogSearchType,
        val continuation: CatalogContinuation?
    )

    private class FakeDataSource(
        override val isConfigured: Boolean = true,
        private val responder: (SearchCall) -> Flow<CatalogSearchState>
    ) : CatalogSearchDataSource {

        val calls = mutableListOf<SearchCall>()
        val cancelledQueries = mutableListOf<String>()

        override fun search(
            query: String,
            type: CatalogSearchType,
            pageSize: Int,
            continuation: CatalogContinuation?
        ): Flow<CatalogSearchState> = flow {
            val call = SearchCall(query, type, continuation)
            calls += call

            try {
                emitAll(responder(call))
            } catch (cancelled: CancellationException) {
                cancelledQueries += query
                throw cancelled
            }
        }

        override suspend fun recentSearches(
            type: CatalogSearchType?,
            limit: Int
        ): List<CatalogRecentSearchEntity> = emptyList()

        override suspend fun clearRecentSearches() = Unit
    }
}