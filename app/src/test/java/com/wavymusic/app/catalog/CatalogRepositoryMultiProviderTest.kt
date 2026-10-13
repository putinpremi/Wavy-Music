
package com.wavymusic.app.catalog

import androidx.room.Room
import com.wavymusic.app.catalog.model.CatalogAlbum
import com.wavymusic.app.catalog.model.CatalogArtist
import com.wavymusic.app.catalog.model.CatalogPlaylist
import com.wavymusic.app.catalog.model.CatalogTrack
import com.wavymusic.app.catalog.provider.CatalogContinuation
import com.wavymusic.app.catalog.provider.CatalogPage
import com.wavymusic.app.catalog.provider.CatalogProviderError
import com.wavymusic.app.catalog.provider.CatalogProviderErrorCode
import com.wavymusic.app.catalog.provider.CatalogProviderResult
import com.wavymusic.app.catalog.provider.CatalogSearchRequest
import com.wavymusic.app.catalog.provider.CatalogSearchType
import com.wavymusic.app.catalog.provider.MusicCatalogCapabilities
import com.wavymusic.app.catalog.provider.MusicCatalogProvider
import com.wavymusic.app.catalog.storage.WavyCatalogDatabase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogRepositoryMultiProviderTest {

    private lateinit var database: WavyCatalogDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            WavyCatalogDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun identicalTitlesFromDifferentProvidersRemainSeparate() = runBlocking {
        val apple = FakeTrackProvider("itunes-search") {
            CatalogProviderResult.Success(
                CatalogPage(
                    items = listOf(
                        CatalogTrack(
                            id = "itunes-search:track:111",
                            title = "Same Song",
                            artistNames = listOf("Same Artist")
                        )
                    )
                )
            )
        }

        val deezer = FakeTrackProvider("deezer-public") {
            CatalogProviderResult.Success(
                CatalogPage(
                    items = listOf(
                        CatalogTrack(
                            id = "deezer-public:track:222",
                            title = "Same Song",
                            artistNames = listOf("Same Artist")
                        )
                    )
                )
            )
        }

        val repository = CatalogRepository(
            providers = listOf(apple, deezer),
            dao = database.catalogDao(),
            clock = { 1_000_000L }
        )

        val states = repository.search(
            query = "Same Song",
            type = CatalogSearchType.TRACKS
        ).toList()

        val final = states.last() as CatalogSearchState.Success

        assertEquals(2, final.items.size)
        assertEquals(
            listOf("Same Song", "Same Song"),
            final.items.map { it.title }
        )
        assertEquals(
            setOf("itunes-search", "deezer-public"),
            final.items.map { it.providerId }.toSet()
        )
        assertEquals(
            2,
            final.items.map { it.stableKey }.toSet().size
        )
        assertEquals(1, apple.requests.size)
        assertEquals(1, deezer.requests.size)
    }

    @Test
    fun successfulProviderResultsSurviveAnotherProvidersFailure() =
        runBlocking {
            val apple = FakeTrackProvider("itunes-search") {
                CatalogProviderResult.Failure(
                    CatalogProviderError(
                        code = CatalogProviderErrorCode.OFFLINE,
                        message = "Apple catalog unavailable in this test."
                    )
                )
            }

            val deezer = FakeTrackProvider("deezer-public") {
                CatalogProviderResult.Success(
                    CatalogPage(
                        items = listOf(
                            CatalogTrack(
                                id = "deezer-public:track:222",
                                title = "Available Song",
                                artistNames = listOf("Available Artist")
                            )
                        )
                    )
                )
            }

            val repository = CatalogRepository(
                providers = listOf(apple, deezer),
                dao = database.catalogDao(),
                clock = { 1_000_000L }
            )

            val states = repository.search(
                query = "Available Song",
                type = CatalogSearchType.TRACKS
            ).toList()

            val final = states.last() as CatalogSearchState.Success

            assertEquals(1, final.items.size)
            assertEquals("Available Song", final.items.single().title)
            assertEquals("deezer-public", final.items.single().providerId)

            assertTrue(
                final.warnings.any {
                    it.code == CatalogProviderErrorCode.OFFLINE
                }
            )
            assertEquals(1, apple.requests.size)
            assertEquals(1, deezer.requests.size)
        }

    @Test
    fun continuationQueriesOnlyItsOwningProvider() = runBlocking {
        val apple = FakeTrackProvider("itunes-search") {
            CatalogProviderResult.Success(
                CatalogPage(
                    items = listOf(
                        CatalogTrack(
                            id = "itunes-search:track:111",
                            title = "Apple Result"
                        )
                    )
                )
            )
        }

        val deezer = FakeTrackProvider("deezer-public") {
            CatalogProviderResult.Success(
                CatalogPage(
                    items = listOf(
                        CatalogTrack(
                            id = "deezer-public:track:333",
                            title = "Deezer Next Page"
                        )
                    )
                )
            )
        }

        val repository = CatalogRepository(
            providers = listOf(apple, deezer),
            dao = database.catalogDao(),
            clock = { 1_000_000L }
        )

        val continuation = CatalogContinuation(
            providerId = "deezer-public",
            token = "25"
        )

        val states = repository.search(
            query = "Demo Song",
            type = CatalogSearchType.TRACKS,
            continuation = continuation
        ).toList()

        val final = states.last() as CatalogSearchState.Success

        assertEquals(1, final.items.size)
        assertEquals("Deezer Next Page", final.items.single().title)
        assertEquals("deezer-public", final.items.single().providerId)

        assertTrue(apple.requests.isEmpty())
        assertEquals(1, deezer.requests.size)
        assertEquals(
            continuation,
            deezer.requests.single().continuation
        )
    }

    private class FakeTrackProvider(
        override val providerId: String,
        private val handler: suspend (
            CatalogSearchRequest
        ) -> CatalogProviderResult<CatalogPage<CatalogTrack>>
    ) : MusicCatalogProvider {

        override val displayName: String = providerId

        override val capabilities = MusicCatalogCapabilities(
            searchTracks = true
        )

        val requests = mutableListOf<CatalogSearchRequest>()

        override suspend fun searchTracks(
            request: CatalogSearchRequest
        ): CatalogProviderResult<CatalogPage<CatalogTrack>> {
            requests.add(request)
            return handler(request)
        }

        override suspend fun searchArtists(
            request: CatalogSearchRequest
        ): CatalogProviderResult<CatalogPage<CatalogArtist>> =
            unsupported()

        override suspend fun searchAlbums(
            request: CatalogSearchRequest
        ): CatalogProviderResult<CatalogPage<CatalogAlbum>> =
            unsupported()

        override suspend fun searchPublicPlaylists(
            request: CatalogSearchRequest
        ): CatalogProviderResult<CatalogPage<CatalogPlaylist>> =
            unsupported()

        private fun <T> unsupported(): CatalogProviderResult<CatalogPage<T>> =
            CatalogProviderResult.Failure(
                CatalogProviderError(
                    code = CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
                    message = "This test provider supports track search only."
                )
            )
    }
}
