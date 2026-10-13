
package com.wavymusic.app.catalog.provider.itunes

import com.wavymusic.app.catalog.model.SourceEligibilityStatus
import com.wavymusic.app.catalog.provider.CatalogContinuation
import com.wavymusic.app.catalog.provider.CatalogProviderError
import com.wavymusic.app.catalog.provider.CatalogProviderErrorCode
import com.wavymusic.app.catalog.provider.CatalogProviderResult
import com.wavymusic.app.catalog.provider.CatalogSearchRequest
import com.wavymusic.app.catalog.provider.CatalogSearchType
import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ITunesCatalogProviderTest {

    @Test
    fun mapsTrackMetadataWithoutClaimingPlaybackEligibility() = runBlocking {
        val transport = FakeTransport(
            mapOf(
                CatalogSearchType.TRACKS to
                    ITunesTransportResult.Success(trackPayload())
            )
        )
        val provider = ITunesCatalogProvider(
            transport = transport,
            clock = { 1_234L }
        )

        val result = provider.searchTracks(
            CatalogSearchRequest(query = "Nova Song")
        )

        assertTrue(result is CatalogProviderResult.Success)

        val page = (result as CatalogProviderResult.Success).value
        val track = page.items.single()

        assertEquals("Nova Song", track.title)
        assertEquals("itunes-search:track:123", track.id)
        assertEquals("123", track.providerIds.single().itemId)
        assertEquals("Nova Artist", track.artistNames.single())
        assertEquals("Nova Album", track.releaseTitle)
        assertEquals(2024, track.releaseYear)
        assertEquals(210_000L, track.durationMs)

        assertEquals(
            SourceEligibilityStatus.CATALOG_ONLY,
            track.sourceEligibility.single().status
        )
        assertEquals(1_234L, track.sourceEligibility.single().checkedAtEpochMs)
        assertEquals(
            "https://itunes.apple.com/us/album/nova-song/123?i=123",
            track.sources.single().catalogUri
        )

        // Artwork and audio previews are not used in this batch.
        assertNull(track.artwork)
        assertTrue(transport.calls.single().type == CatalogSearchType.TRACKS)
    }

    @Test
    fun mapsArtistSearchResults() = runBlocking {
        val transport = FakeTransport(
            mapOf(
                CatalogSearchType.ARTISTS to
                    ITunesTransportResult.Success(artistPayload())
            )
        )
        val provider = ITunesCatalogProvider(transport)

        val result = provider.searchArtists(
            CatalogSearchRequest(query = "Nova Artist")
        )

        assertTrue(result is CatalogProviderResult.Success)

        val artist = (result as CatalogProviderResult.Success)
            .value.items.single()

        assertEquals("Nova Artist", artist.name)
        assertEquals("itunes-search:artist:456", artist.id)
        assertEquals("456", artist.providerIds.single().itemId)
        assertEquals(
            "https://itunes.apple.com/us/artist/nova-artist/456",
            artist.sources.single().catalogUri
        )
        assertEquals(CatalogSearchType.ARTISTS, transport.calls.single().type)
    }

    @Test
    fun mapsAlbumSearchResults() = runBlocking {
        val transport = FakeTransport(
            mapOf(
                CatalogSearchType.ALBUMS to
                    ITunesTransportResult.Success(albumPayload())
            )
        )
        val provider = ITunesCatalogProvider(transport)

        val result = provider.searchAlbums(
            CatalogSearchRequest(query = "Nova Album")
        )

        assertTrue(result is CatalogProviderResult.Success)

        val album = (result as CatalogProviderResult.Success)
            .value.items.single()

        assertEquals("Nova Album", album.title)
        assertEquals("itunes-search:album:789", album.id)
        assertEquals("789", album.providerIds.single().itemId)
        assertEquals("Nova Artist", album.artistNames.single())
        assertEquals(2023, album.releaseYear)
        assertEquals(11, album.trackCount)
        assertEquals(CatalogSearchType.ALBUMS, transport.calls.single().type)
    }

    @Test
    fun publicPlaylistSearchIsExplicitlyUnsupported() = runBlocking {
        val transport = FakeTransport(emptyMap())
        val provider = ITunesCatalogProvider(transport)

        assertFalse(provider.capabilities.searchPublicPlaylists)

        val result = provider.searchPublicPlaylists(
            CatalogSearchRequest(query = "Chill mix")
        )

        assertTrue(result is CatalogProviderResult.Failure)
        assertEquals(
            CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
            (result as CatalogProviderResult.Failure).error.code
        )
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun continuationIsRejectedInsteadOfRepeatingTheFirstPage() = runBlocking {
        val transport = FakeTransport(emptyMap())
        val provider = ITunesCatalogProvider(transport)

        val result = provider.searchTracks(
            CatalogSearchRequest(
                query = "Nova Song",
                continuation = CatalogContinuation(
                    providerId = provider.providerId,
                    token = "page-2"
                )
            )
        )

        assertTrue(result is CatalogProviderResult.Failure)
        assertEquals(
            CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
            (result as CatalogProviderResult.Failure).error.code
        )
        assertTrue(transport.calls.isEmpty())
    }

    @Test
    fun malformedJsonReturnsTypedFailure() = runBlocking {
        val provider = ITunesCatalogProvider(
            FakeTransport(
                mapOf(
                    CatalogSearchType.TRACKS to
                        ITunesTransportResult.Success("{not-json")
                )
            )
        )

        val result = provider.searchTracks(
            CatalogSearchRequest(query = "Nova Song")
        )

        assertTrue(result is CatalogProviderResult.Failure)
        assertEquals(
            CatalogProviderErrorCode.MALFORMED_RESPONSE,
            (result as CatalogProviderResult.Failure).error.code
        )
    }

    @Test
    fun rateLimitErrorAndRetryDelayArePreserved() = runBlocking {
        val transport = FakeTransport(
            mapOf(
                CatalogSearchType.TRACKS to
                    ITunesTransportResult.Failure(
                        CatalogProviderError(
                            code = CatalogProviderErrorCode.RATE_LIMITED,
                            message = "Try again later.",
                            retryAfterMs = 7_000L
                        )
                    )
            )
        )
        val provider = ITunesCatalogProvider(transport)

        val result = provider.searchTracks(
            CatalogSearchRequest(query = "Nova Song")
        )

        assertTrue(result is CatalogProviderResult.Failure)

        val error = (result as CatalogProviderResult.Failure).error
        assertEquals(CatalogProviderErrorCode.RATE_LIMITED, error.code)
        assertEquals(7_000L, error.retryAfterMs)
    }

    @Test
    fun generatedUrlUsesPublicEndpointAndEncodesSearchText() {
        val transport = HttpITunesCatalogTransport(
            countryCode = "IN",
            minRequestIntervalMs = 0L
        )

        val url = transport.buildSearchUrl(
            query = "Nova artist & me",
            type = CatalogSearchType.TRACKS,
            pageSize = 25
        )
        val uri = URI(url)

        assertEquals("https", uri.scheme)
        assertEquals("itunes.apple.com", uri.host)

        val parameters = uri.rawQuery
            .split("&")
            .associate { part ->
                val separator = part.indexOf('=')
                part.substring(0, separator) to
                    part.substring(separator + 1)
            }

        assertEquals(
            "Nova artist & me",
            URLDecoder.decode(
                parameters.getValue("term"),
                "UTF-8"
            )
        )
        assertEquals("in", parameters["country"])
        assertEquals("music", parameters["media"])
        assertEquals("song", parameters["entity"])
        assertEquals("25", parameters["limit"])
    }

    private class FakeTransport(
        private val responses: Map<CatalogSearchType, ITunesTransportResult>
    ) : ITunesCatalogTransport {

        data class Call(
            val query: String,
            val type: CatalogSearchType,
            val pageSize: Int
        )

        val calls = mutableListOf<Call>()

        override suspend fun search(
            query: String,
            type: CatalogSearchType,
            pageSize: Int
        ): ITunesTransportResult {
            calls += Call(query, type, pageSize)

            return responses[type] ?: ITunesTransportResult.Failure(
                CatalogProviderError(
                    code = CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
                    message = "No fixture registered for ${type.name}."
                )
            )
        }
    }

    private companion object {
        fun trackPayload() = """
            {
              "resultCount": 1,
              "results": [
                {
                  "wrapperType": "track",
                  "kind": "song",
                  "trackId": 123,
                  "trackName": "Nova Song",
                  "artistId": 456,
                  "artistName": "Nova Artist",
                  "collectionId": 789,
                  "collectionName": "Nova Album",
                  "releaseDate": "2024-11-21T08:00:00Z",
                  "trackTimeMillis": 210000,
                  "trackViewUrl": "https://itunes.apple.com/us/album/nova-song/123?i=123",
                  "artworkUrl100": "https://example.invalid/artwork.jpg"
                }
              ]
            }
        """.trimIndent()

        fun artistPayload() = """
            {
              "resultCount": 1,
              "results": [
                {
                  "wrapperType": "artist",
                  "artistType": "Artist",
                  "artistId": 456,
                  "artistName": "Nova Artist",
                  "artistViewUrl": "https://itunes.apple.com/us/artist/nova-artist/456"
                }
              ]
            }
        """.trimIndent()

        fun albumPayload() = """
            {
              "resultCount": 1,
              "results": [
                {
                  "wrapperType": "collection",
                  "collectionType": "Album",
                  "collectionId": 789,
                  "collectionName": "Nova Album",
                  "artistId": 456,
                  "artistName": "Nova Artist",
                  "releaseDate": "2023-05-01T07:00:00Z",
                  "trackCount": 11,
                  "collectionViewUrl": "https://itunes.apple.com/us/album/nova-album/789"
                }
              ]
            }
        """.trimIndent()
    }
}
