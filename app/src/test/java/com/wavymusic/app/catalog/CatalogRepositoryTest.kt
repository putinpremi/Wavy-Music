package com.wavymusic.app.catalog

import androidx.room.Room
import com.wavymusic.app.catalog.model.CatalogTrack
import com.wavymusic.app.catalog.provider.CatalogContinuation
import com.wavymusic.app.catalog.provider.CatalogPage
import com.wavymusic.app.catalog.provider.CatalogProviderError
import com.wavymusic.app.catalog.provider.CatalogProviderErrorCode
import com.wavymusic.app.catalog.provider.CatalogProviderResult
import com.wavymusic.app.catalog.provider.CatalogSearchRequest
import com.wavymusic.app.catalog.provider.CatalogSearchType
import com.wavymusic.app.catalog.provider.MusicCatalogProvider
import com.wavymusic.app.catalog.provider.MusicCatalogCapabilities
import com.wavymusic.app.catalog.storage.CatalogSearchCacheEntity
import com.wavymusic.app.catalog.storage.CatalogSearchCacheResultEntity
import com.wavymusic.app.catalog.storage.WavyCatalogDatabase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogRepositoryTest {

    private lateinit var database: WavyCatalogDatabase
    private lateinit var dao: com.wavymusic.app.catalog.storage.CatalogDao
    private lateinit var provider: FakeTrackProvider
    private lateinit var repository: CatalogRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            WavyCatalogDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        dao = database.catalogDao()
        provider = FakeTrackProvider()
        repository = CatalogRepository(
            providers = listOf(provider),
            dao = dao,
            clock = { 1_000_000L }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun blankQueryDoesNotCallProviderOrStoreRecentSearch() = runBlocking {
        val states = repository.search(
            query = "    ",
            type = CatalogSearchType.TRACKS
        ).toList()

        assertEquals(listOf(CatalogSearchState.Idle), states)
        assertEquals(0, provider.calls)
        assertTrue(dao.getRecentSearches(null, 10).isEmpty())
    }

    @Test
    fun cachedItemsAreEmittedBeforeFreshResults() = runBlocking {
        val normalized = "sample song"
        val cacheKey = CatalogSearchCacheKeys.create(
            providerId = provider.providerId,
            type = CatalogSearchType.TRACKS,
            normalizedQuery = normalized
        )

        val cache = CatalogSearchCacheEntity(
            cacheKey = cacheKey,
            queryText = "Sample Song",
            normalizedQuery = normalized,
            searchType = CatalogSearchType.TRACKS.name,
            providerId = provider.providerId,
            cachedAtEpochMs = 500L,
            expiresAtEpochMs = 800L
        )

        dao.replaceSearchCache(
            cache,
            listOf(
                CatalogSearchCacheResultEntity(
                    cacheKey = cacheKey,
                    position = 0,
                    itemType = CatalogSearchItemType.TRACK.name,
                    catalogItemId = "cached-track",
                    titleSnapshot = "Cached Song",
                    subtitleSnapshot = "Cached Artist"
                )
            )
        )

        provider.result = CatalogProviderResult.Success(
            CatalogPage(
                items = listOf(
                    CatalogTrack(
                        id = "fresh-track",
                        title = "Fresh Song",
                        artistNames = listOf("Fresh Artist"),
                        durationMs = 200_000L
                    )
                )
            )
        )

        val states = repository.search(
            query = " Sample Song ",
            type = CatalogSearchType.TRACKS
        ).toList()

        assertTrue(states.first() is CatalogSearchState.Loading)

        val loading = states.first() as CatalogSearchState.Loading
        assertTrue(loading.isRefreshing)
        assertEquals("Cached Song", loading.items.single().title)

        val final = states.last() as CatalogSearchState.Success
        assertEquals("Fresh Song", final.items.single().title)
        assertFalse(final.fromCache)

        assertEquals(
            "Fresh Song",
            dao.getSearchCacheResults(cacheKey).single().titleSnapshot
        )
    }

    @Test
    fun successfulSearchRecordsNormalizedRecentQuery() = runBlocking {
        provider.result = CatalogProviderResult.Success(
            CatalogPage(emptyList())
        )

        repository.search(
            query = "  Sämple-Song! ",
            type = CatalogSearchType.TRACKS
        ).toList()

        val recent = dao.getRecentSearch("sample song", "TRACKS")

        assertTrue(recent != null)
        assertEquals("Sämple-Song!", recent?.queryText)
        assertEquals(1, recent?.useCount)
    }

    @Test
    fun providerRateLimitProducesRateLimitedState() = runBlocking {
        provider.result = CatalogProviderResult.Failure(
            CatalogProviderError(
                code = CatalogProviderErrorCode.RATE_LIMITED,
                message = "Try again later",
                retryAfterMs = 5_000L
            )
        )

        val states = repository.search(
            query = "Sample Song",
            type = CatalogSearchType.TRACKS
        ).toList()

        val final = states.last() as CatalogSearchState.RateLimited
        assertEquals(5_000L, final.retryAfterMs)
        assertEquals("Try again later", final.message)
    }

    @Test
    fun continuationBelongingToUnknownProviderIsRejected() = runBlocking {
        val states = repository.search(
            query = "Sample Song",
            type = CatalogSearchType.TRACKS,
            continuation = CatalogContinuation(
                providerId = "missing-provider",
                token = "opaque-token"
            )
        ).toList()

        assertTrue(states.last() is CatalogSearchState.Failure)
        assertEquals(0, provider.calls)
    }

    private class FakeTrackProvider : MusicCatalogProvider {
        override val providerId = "fixture-provider"
        override val displayName = "Fixture Provider"

        override val capabilities = MusicCatalogCapabilities(
            searchTracks = true
        )

        var calls = 0

        var result: CatalogProviderResult<CatalogPage<CatalogTrack>> =
            CatalogProviderResult.Success(CatalogPage(emptyList()))

        override suspend fun searchTracks(
            request: CatalogSearchRequest
        ): CatalogProviderResult<CatalogPage<CatalogTrack>> {
            calls++
            return result
        }

        override suspend fun searchArtists(
            request: CatalogSearchRequest
        ) = unsupported<CatalogPage<com.wavymusic.app.catalog.model.CatalogArtist>>()

        override suspend fun searchAlbums(
            request: CatalogSearchRequest
        ) = unsupported<CatalogPage<com.wavymusic.app.catalog.model.CatalogAlbum>>()

        override suspend fun searchPublicPlaylists(
            request: CatalogSearchRequest
        ) = unsupported<CatalogPage<com.wavymusic.app.catalog.model.CatalogPlaylist>>()

        private fun <T> unsupported(): CatalogProviderResult<T> =
            CatalogProviderResult.Failure(
                CatalogProviderError(
                    CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
                    "Unsupported by test provider"
                )
            )
    }
}