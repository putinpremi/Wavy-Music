package com.wavymusic.app.catalog.provider.innertube

import com.wavymusic.app.catalog.provider.CatalogContinuation
import com.wavymusic.app.catalog.provider.CatalogPage
import com.wavymusic.app.catalog.provider.CatalogProviderError
import com.wavymusic.app.catalog.provider.CatalogProviderErrorCode
import com.wavymusic.app.catalog.provider.CatalogProviderResult
import com.wavymusic.app.catalog.provider.CatalogSearchRequest
import com.wavymusic.app.catalog.provider.CatalogSearchType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeCatalogProviderTest {

    @Test
    fun mapsTransportPayloadToCanonicalTrack() = runBlocking {
        val transport = FakeTransport(
            supportedSearchTypes = setOf(CatalogSearchType.TRACKS),
            result = InnerTubeTransportResult.Success(trackPayload())
        )
        val provider = InnerTubeCatalogProvider(
            transport = transport,
            clock = { 1_000L }
        )

        val result = provider.searchTracks(
            CatalogSearchRequest(query = "Nova Song")
        )

        assertTrue(result is CatalogProviderResult.Success)

        val page = (result as CatalogProviderResult.Success).value
        assertEquals(1, page.items.size)
        assertEquals("Nova Song", page.items.single().title)
        assertEquals("video-123", page.items.single().providerIds.single().itemId)
        assertEquals(CatalogSearchType.TRACKS, transport.lastType)
        assertEquals(1, transport.calls)
    }

    @Test
    fun unsupportedSearchIsNotSentToTransport() = runBlocking {
        val transport = FakeTransport(
            supportedSearchTypes = setOf(CatalogSearchType.TRACKS),
            result = InnerTubeTransportResult.Success(trackPayload())
        )
        val provider = InnerTubeCatalogProvider(transport)

        assertFalse(provider.capabilities.searchAlbums)

        val result = provider.searchAlbums(
            CatalogSearchRequest(query = "Nova Album")
        )

        assertTrue(result is CatalogProviderResult.Failure)
        assertEquals(
            CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
            (result as CatalogProviderResult.Failure).error.code
        )
        assertEquals(0, transport.calls)
    }

    @Test
    fun continuationFromAnotherProviderIsRejected() = runBlocking {
        val transport = FakeTransport()
        val provider = InnerTubeCatalogProvider(transport)

        val result = provider.searchTracks(
            CatalogSearchRequest(
                query = "Nova Song",
                continuation = CatalogContinuation(
                    providerId = "some-other-provider",
                    token = "opaque-token"
                )
            )
        )

        assertTrue(result is CatalogProviderResult.Failure)
        assertEquals(
            CatalogProviderErrorCode.INVALID_CONTINUATION,
            (result as CatalogProviderResult.Failure).error.code
        )
        assertEquals(0, transport.calls)
    }

    @Test
    fun rateLimitErrorIsPreserved() = runBlocking {
        val transport = FakeTransport(
            result = InnerTubeTransportResult.Failure(
                CatalogProviderError(
                    code = CatalogProviderErrorCode.RATE_LIMITED,
                    message = "Rate limit reached",
                    retryAfterMs = 8_000L
                )
            )
        )
        val provider = InnerTubeCatalogProvider(transport)

        val result = provider.searchTracks(
            CatalogSearchRequest(query = "Nova Song")
        )

        assertTrue(result is CatalogProviderResult.Failure)
        val error = (result as CatalogProviderResult.Failure).error

        assertEquals(CatalogProviderErrorCode.RATE_LIMITED, error.code)
        assertEquals(8_000L, error.retryAfterMs)
    }

    @Test
    fun coroutineCancellationIsRethrown() = runBlocking {
        val transport = FakeTransport().apply {
            cancelOnSearch = true
        }
        val provider = InnerTubeCatalogProvider(transport)

        try {
            provider.searchTracks(
                CatalogSearchRequest(query = "Nova Song")
            )
            throw AssertionError("Expected cancellation to propagate")
        } catch (cancelled: CancellationException) {
            assertEquals("Fixture cancellation", cancelled.message)
        }
    }

    private class FakeTransport(
        override val supportedSearchTypes: Set<CatalogSearchType> =
            setOf(CatalogSearchType.TRACKS),
        var result: InnerTubeTransportResult =
            InnerTubeTransportResult.Success(trackPayload())
    ) : InnerTubeCatalogTransport {

        var calls = 0
        var lastType: CatalogSearchType? = null
        var cancelOnSearch = false

        override suspend fun search(
            type: CatalogSearchType,
            request: CatalogSearchRequest
        ): InnerTubeTransportResult {
            calls++
            lastType = type

            if (cancelOnSearch) {
                throw CancellationException("Fixture cancellation")
            }

            return result
        }
    }

    companion object {
        private fun trackPayload() = """
            {
              "contents": {
                "musicShelfRenderer": {
                  "contents": [
                    {
                      "musicResponsiveListItemRenderer": {
                        "flexColumns": [
                          {
                            "musicResponsiveListItemFlexColumnRenderer": {
                              "text": {
                                "runs": [{"text": "Nova Song"}]
                              }
                            }
                          }
                        ],
                        "overlay": {
                          "musicItemThumbnailOverlayRenderer": {
                            "content": {
                              "musicPlayButtonRenderer": {
                                "playNavigationEndpoint": {
                                  "watchEndpoint": {
                                    "videoId": "video-123"
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()
    }
}