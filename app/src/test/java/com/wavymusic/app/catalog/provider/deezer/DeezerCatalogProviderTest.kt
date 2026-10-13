
package com.wavymusic.app.catalog.provider.deezer

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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeezerCatalogProviderTest {

    @Test
    fun mapsTrackMetadataAndKeepsItCatalogOnly() = runBlocking {
        val provider = providerFor(
            CatalogSearchType.TRACKS,
            trackPayload()
        )

        val result = provider.searchTracks(
            CatalogSearchRequest(query = "Nova Song")
        )

        assertTrue(result is CatalogProviderResult.Success)

        val track = (result as CatalogProviderResult.Success)
            .value.items.single()

        assertEquals("Nova Song", track.title)
        assertEquals("deezer-public:track:123", track.id)
        assertEquals("123", track.providerIds.single().itemId)
        assertEquals(listOf("Nova Artist"), track.artistNames)
        assertEquals("Nova Album", track.releaseTitle)
        assertEquals(185_000L, track.durationMs)

        assertEquals(
            SourceEligibilityStatus.CATALOG_ONLY,
            track.sourceEligibility.single().status
        )
        assertEquals(
            "https://www.deezer.com/track/123",
            track.sources.single().catalogUri
        )
        assertNotNull(track.artwork)
    }

    @Test
    fun mapsArtistMetadataAndCatalogLink() = runBlocking {
        val provider = providerFor(
            CatalogSearchType.ARTISTS,
            artistPayload()
        )

        val result = provider.searchArtists(
            CatalogSearchRequest(query = "Nova Artist")
        )

        assertTrue(result is CatalogProviderResult.Success)

        val artist = (result as CatalogProviderResult.Success)
            .value.items.single()

        assertEquals("Nova Artist", artist.name)
        assertEquals("deezer-public:artist:456", artist.id)
        assertEquals("456", artist.providerIds.single().itemId)
        assertEquals(
            "https://www.deezer.com/artist/456",
            artist.sources.single().catalogUri
        )
    }

    @Test
    fun mapsAlbumMetadataAndReleaseType() = runBlocking {
        val provider = providerFor(
            CatalogSearchType.ALBUMS,
            albumPayload()
        )

        val result = provider.searchAlbums(
            CatalogSearchRequest(query = "Nova Album")
        )

        assertTrue(result is CatalogProviderResult.Success)

        val album = (result as CatalogProviderResult.Success)
            .value.items.single()

        assertEquals("Nova Album", album.title)
        assertEquals("deezer-public:album:789", album.id)
        assertEquals("789", album.providerIds.single().itemId)
        assertEquals(listOf("Nova Artist"), album.artistNames)
        assertEquals(2022, album.releaseYear)
        assertEquals(12, album.trackCount)
        assertEquals("SINGLE", album.releaseType.name)
    }

    @Test
    fun mapsPublicPlaylistAndDoesNotInventPrivateVisibility() = runBlocking {
        val provider = providerFor(
            CatalogSearchType.PUBLIC_PLAYLISTS,
            playlistPayload(isPublic = true)
        )

        val result = provider.searchPublicPlaylists(
            CatalogSearchRequest(query = "Nova Mix")
        )

        assertTrue(result is CatalogProviderResult.Success)

        val playlist = (result as CatalogProviderResult.Success)
            .value.items.single()

        assertEquals("Nova Mix", playlist.title)
        assertEquals("deezer-public:playlist:999", playlist.id)
        assertEquals("Curator", playlist.ownerName)
        assertTrue(playlist.isPublic)
        assertTrue(playlist.isSearchablePublicly)
        assertEquals(30, playlist.entryCount)
    }

    @Test
    fun privatePlaylistIsOmitted() = runBlocking {
        val provider = providerFor(
            CatalogSearchType.PUBLIC_PLAYLISTS,
            playlistPayload(isPublic = false)
        )

        val result = provider.searchPublicPlaylists(
            CatalogSearchRequest(query = "Private Mix")
        )

        assertTrue(result is CatalogProviderResult.Success)
        assertTrue(
            (result as CatalogProviderResult.Success).value.items.isEmpty()
        )
    }

    @Test
    fun continuationLoadsTheNextOffsetAndStopsAtLastPage() = runBlocking {
        val calls = mutableListOf<Int>()

        val transport = FakeTransport { type, _, _, index ->
            calls += index
            assertEquals(CatalogSearchType.TRACKS, type)

            if (index == 0) {
                DeezerTransportResult.Success(
                    trackPayload(
                        id = 123,
                        title = "First Song",
                        total = 2,
                        hasNext = true
                    )
                )
            } else {
                DeezerTransportResult.Success(
                    trackPayload(
                        id = 124,
                        title = "Second Song",
                        total = 2,
                        hasNext = false
                    )
                )
            }
        }

        val provider = DeezerCatalogProvider(transport)

        val first = provider.searchTracks(
            CatalogSearchRequest(
                query = "Nova",
                pageSize = 1
            )
        ) as CatalogProviderResult.Success

        assertEquals("First Song", first.value.items.single().title)
        assertEquals("1", first.value.nextContinuation?.token)
        assertEquals(
            provider.providerId,
            first.value.nextContinuation?.providerId
        )

        val second = provider.searchTracks(
            CatalogSearchRequest(
                query = "Nova",
                pageSize = 1,
                continuation = first.value.nextContinuation
            )
        ) as CatalogProviderResult.Success

        assertEquals("Second Song", second.value.items.single().title)
        assertNull(second.value.nextContinuation)
        assertEquals(listOf(0, 1), calls)
    }

    @Test
    fun continuationFromAnotherProviderIsRejectedBeforeTransport() =
        runBlocking {
            val transport = FakeTransport { _, _, _, _ ->
                error("Transport must not be called for an invalid token.")
            }
            val provider = DeezerCatalogProvider(transport)

            val result = provider.searchTracks(
                CatalogSearchRequest(
                    query = "Nova",
                    continuation = CatalogContinuation(
                        providerId = "other-provider",
                        token = "25"
                    )
                )
            )

            assertTrue(result is CatalogProviderResult.Failure)
            assertEquals(
                CatalogProviderErrorCode.INVALID_CONTINUATION,
                (result as CatalogProviderResult.Failure).error.code
            )
        }

    @Test
    fun malformedPayloadReturnsTypedFailure() = runBlocking {
        val provider = providerFor(
            CatalogSearchType.TRACKS,
            "{not-json"
        )

        val result = provider.searchTracks(
            CatalogSearchRequest(query = "Nova")
        )

        assertTrue(result is CatalogProviderResult.Failure)
        assertEquals(
            CatalogProviderErrorCode.MALFORMED_RESPONSE,
            (result as CatalogProviderResult.Failure).error.code
        )
    }

    @Test
    fun rateLimitFailureIsPreserved() = runBlocking {
        val provider = DeezerCatalogProvider(
            FakeTransport { _, _, _, _ ->
                DeezerTransportResult.Failure(
                    CatalogProviderError(
                        code = CatalogProviderErrorCode.RATE_LIMITED,
                        message = "Rate limit reached.",
                        retryAfterMs = 5_000L
                    )
                )
            }
        )

        val result = provider.searchTracks(
            CatalogSearchRequest(query = "Nova")
        )

        assertTrue(result is CatalogProviderResult.Failure)

        val error = (result as CatalogProviderResult.Failure).error
        assertEquals(CatalogProviderErrorCode.RATE_LIMITED, error.code)
        assertEquals(5_000L, error.retryAfterMs)
    }

    @Test
    fun searchUrlEncodesQueryAndUsesCorrectEndpoint() {
        val transport = HttpDeezerCatalogTransport(
            minRequestIntervalMs = 0L
        )

        val url = transport.buildSearchUrl(
            query = "Nova artist & me",
            type = CatalogSearchType.ARTISTS,
            pageSize = 25,
            index = 50
        )

        val uri = URI(url)
        val parameters = uri.rawQuery.split("&").associate { part ->
            val separator = part.indexOf('=')
            part.substring(0, separator) to
                part.substring(separator + 1)
        }

        assertEquals("https", uri.scheme)
        assertEquals("api.deezer.com", uri.host)
        assertEquals("/search/artist", uri.path)
        assertEquals(
            "Nova artist & me",
            URLDecoder.decode(
                parameters.getValue("q"),
                "UTF-8"
            )
        )
        assertEquals("25", parameters["limit"])
        assertEquals("50", parameters["index"])
        assertFalse(url.contains("preview="))
    }

    private fun providerFor(
        type: CatalogSearchType,
        payload: String
    ) = DeezerCatalogProvider(
        FakeTransport { requestedType, _, _, _ ->
            if (requestedType == type) {
                DeezerTransportResult.Success(payload)
            } else {
                DeezerTransportResult.Failure(
                    CatalogProviderError(
                        CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
                        "No fixture for $requestedType."
                    )
                )
            }
        }
    )

    private class FakeTransport(
        private val handler: (
            CatalogSearchType,
            String,
            Int,
            Int
        ) -> DeezerTransportResult
    ) : DeezerCatalogTransport {

        override suspend fun search(
            query: String,
            type: CatalogSearchType,
            pageSize: Int,
            index: Int
        ): DeezerTransportResult =
            handler(type, query, pageSize, index)
    }

    private companion object {
        fun trackPayload(
            id: Int = 123,
            title: String = "Nova Song",
            total: Int = 1,
            hasNext: Boolean = false
        ): String {
            val nextJson = if (hasNext) {
                "\"https://api.deezer.com/search/track?q=Nova&index=1&limit=1\""
            } else {
                "null"
            }

            return """
                {
                  "total": $total,
                  "next": $nextJson,
                  "data": [
                    {
                      "id": $id,
                      "title": "$title",
                      "duration": 185,
                      "link": "https://www.deezer.com/track/$id",
                      "preview": "https://example.invalid/preview.mp3",
                      "artist": {
                        "id": 456,
                        "name": "Nova Artist"
                      },
                      "album": {
                        "id": 789,
                        "title": "Nova Album",
                        "cover_medium": "https://cdn-images.dzcdn.net/images/cover/test.jpg"
                      }
                    }
                  ]
                }
            """.trimIndent()
        }

        fun artistPayload() = """
            {
              "total": 1,
              "next": null,
              "data": [
                {
                  "id": 456,
                  "name": "Nova Artist",
                  "picture_medium": "https://cdn-images.dzcdn.net/images/artist/test.jpg",
                  "link": "https://www.deezer.com/artist/456",
                  "type": "artist"
                }
              ]
            }
        """.trimIndent()

        fun albumPayload() = """
            {
              "total": 1,
              "next": null,
              "data": [
                {
                  "id": 789,
                  "title": "Nova Album",
                  "record_type": "single",
                  "release_date": "2022-06-12",
                  "nb_tracks": 12,
                  "cover_medium": "https://cdn-images.dzcdn.net/images/cover/album.jpg",
                  "link": "https://www.deezer.com/album/789",
                  "artist": {
                    "id": 456,
                    "name": "Nova Artist"
                  }
                }
              ]
            }
        """.trimIndent()

        fun playlistPayload(isPublic: Boolean) = """
            {
              "total": 1,
              "next": null,
              "data": [
                {
                  "id": 999,
                  "title": "Nova Mix",
                  "public": $isPublic,
                  "nb_tracks": 30,
                  "picture_medium": "https://cdn-images.dzcdn.net/images/playlist/mix.jpg",
                  "link": "https://www.deezer.com/playlist/999",
                  "user": {
                    "name": "Curator"
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
