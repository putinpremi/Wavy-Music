package com.wavymusic.app.catalog.provider.innertube

import com.wavymusic.app.catalog.provider.CatalogProviderResult
import com.wavymusic.app.catalog.provider.CatalogSearchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeCatalogParserTest {

    private val parser = InnerTubeCatalogParser(
        continuationTtlMs = 10_000L
    )

    @Test
    fun parsesTrackMetadataAndContinuation() {
        val payload = trackPayload(
            trackRenderer(
                videoId = "video-123",
                title = "Nova Song",
                artist = "Nova Artist",
                album = "First Album",
                duration = "3:45"
            ),
            continuation = "next-page-token"
        )

        val result = parser.parse(
            payload,
            CatalogSearchType.TRACKS,
            nowEpochMs = 1_000L
        )

        assertTrue(result is CatalogProviderResult.Success)

        val page = (result as CatalogProviderResult.Success).value
        val track = (page.items.single() as InnerTubeCatalogItem.Track).value

        assertEquals("Nova Song", track.title)
        assertEquals("video-123", track.providerIds.single().itemId)
        assertEquals("Nova Artist", track.artistNames.single())
        assertEquals("First Album", track.releaseTitle)
        assertEquals(225_000L, track.durationMs)

        assertNotNull(track.artwork)
        assertEquals(
            "https://example.invalid/nova.jpg",
            track.artwork?.url
        )

        assertNotNull(page.continuation)
        assertEquals("next-page-token", page.continuation?.token)
        assertEquals(11_000L, page.continuation?.expiresAtEpochMs)
    }

    @Test
    fun duplicateTrackResultIsKeptOnlyOnce() {
        val renderer = trackRenderer(
            videoId = "same-video",
            title = "Same Song",
            artist = "Artist"
        )

        val result = parser.parse(
            trackPayload(renderer, renderer),
            CatalogSearchType.TRACKS,
            nowEpochMs = 1_000L
        )

        assertTrue(result is CatalogProviderResult.Success)

        val page = (result as CatalogProviderResult.Success).value

        assertEquals(1, page.items.size)
        assertTrue(
            page.warnings.any {
                it.code == InnerTubeParseWarningCode.DUPLICATE_ITEM
            }
        )
    }

    @Test
    fun missingTrackIdentifierIsSkippedWithWarning() {
        val malformedRenderer = """
            {
              "musicResponsiveListItemRenderer": {
                "flexColumns": [
                  {
                    "musicResponsiveListItemFlexColumnRenderer": {
                      "text": {
                        "runs": [{"text": "Title without an ID"}]
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val result = parser.parse(
            trackPayload(malformedRenderer),
            CatalogSearchType.TRACKS,
            nowEpochMs = 1_000L
        )

        assertTrue(result is CatalogProviderResult.Success)

        val page = (result as CatalogProviderResult.Success).value
        assertTrue(page.items.isEmpty())
        assertTrue(
            page.warnings.any {
                it.code == InnerTubeParseWarningCode.MISSING_REQUIRED_FIELD
            }
        )
    }

    @Test
    fun malformedJsonReturnsTypedFailure() {
        val result = parser.parse(
            """{"contents": """,
            CatalogSearchType.TRACKS,
            nowEpochMs = 1_000L
        )

        assertTrue(result is CatalogProviderResult.Failure)

        val failure = result as CatalogProviderResult.Failure
        assertEquals(
            "MALFORMED_RESPONSE",
            failure.error.code.name
        )
    }

    @Test
    fun parsesArtistAlbumAndPlaylistRenderersByPageType() {
        val artistPayload = twoRowPayload(
            browseId = "UC_nova_artist",
            pageType = "MUSIC_PAGE_TYPE_ARTIST",
            title = "Nova Artist"
        )

        val artistResult = parser.parse(
            artistPayload,
            CatalogSearchType.ARTISTS,
            nowEpochMs = 1_000L
        )

        assertTrue(artistResult is CatalogProviderResult.Success)
        val artistPage =
            (artistResult as CatalogProviderResult.Success).value

        val artist =
            (artistPage.items.single() as InnerTubeCatalogItem.Artist).value

        assertEquals("Nova Artist", artist.name)
        assertEquals("UC_nova_artist", artist.providerIds.single().itemId)

        val albumResult = parser.parse(
            twoRowPayload(
                browseId = "MPRE_nova_album",
                pageType = "MUSIC_PAGE_TYPE_ALBUM",
                title = "Nova Album"
            ),
            CatalogSearchType.ALBUMS,
            nowEpochMs = 1_000L
        )

        assertTrue(albumResult is CatalogProviderResult.Success)
        val albumPage =
            (albumResult as CatalogProviderResult.Success).value

        val album =
            (albumPage.items.single() as InnerTubeCatalogItem.Album).value

        assertEquals("Nova Album", album.title)

        val playlistResult = parser.parse(
            twoRowPayload(
                browseId = "PL_nova_playlist",
                pageType = "MUSIC_PAGE_TYPE_PLAYLIST",
                title = "Nova Playlist"
            ),
            CatalogSearchType.PUBLIC_PLAYLISTS,
            nowEpochMs = 1_000L
        )

        assertTrue(playlistResult is CatalogProviderResult.Success)
        val playlistPage =
            (playlistResult as CatalogProviderResult.Success).value

        val playlist =
            (playlistPage.items.single() as InnerTubeCatalogItem.Playlist).value

        assertTrue(playlist.isPublic)
        assertTrue(playlist.isSearchablePublicly)
    }

    @Test
    fun aContinuationIsBoundToThisProviderAndHasAnExpiry() {
        val result = parser.parse(
            trackPayload(
                trackRenderer(
                    videoId = "video-123",
                    title = "Nova Song",
                    artist = "Nova Artist"
                ),
                continuation = "opaque-token"
            ),
            CatalogSearchType.TRACKS,
            nowEpochMs = 5_000L
        )

        val page = (result as CatalogProviderResult.Success).value

        assertEquals(
            InnerTubeCatalogParser.PROVIDER_ID,
            page.continuation?.providerId
        )
        assertEquals(15_000L, page.continuation?.expiresAtEpochMs)
        assertFalse(page.continuation?.isExpired(5_001L) ?: true)
        assertTrue(page.continuation?.isExpired(15_000L) ?: false)
    }

    private fun trackPayload(
        vararg renderers: String,
        continuation: String? = null
    ): String {
        val continuationJson = if (continuation == null) {
            ""
        } else {
            """,
              "continuations": [
                {
                  "nextContinuationData": {
                    "continuation": "$continuation"
                  }
                }
              ]
            """
        }

        return """
            {
              "contents": {
                "musicShelfRenderer": {
                  "contents": [
                    ${renderers.joinToString(",")}
                  ]
                }
              }
              $continuationJson
            }
        """.trimIndent()
    }

    private fun trackRenderer(
        videoId: String,
        title: String,
        artist: String,
        album: String = "Sample Album",
        duration: String = "3:00"
    ): String = """
        {
          "musicResponsiveListItemRenderer": {
            "flexColumns": [
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": {
                    "runs": [{"text": "$title"}]
                  }
                }
              },
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": {
                    "runs": [
                      {
                        "text": "$artist",
                        "navigationEndpoint": {
                          "browseEndpoint": {
                            "browseId": "UC_test_artist",
                            "browseEndpointContextSupportedConfigs": {
                              "browseEndpointContextMusicConfig": {
                                "pageType": "MUSIC_PAGE_TYPE_ARTIST"
                              }
                            }
                          }
                        }
                      }
                    ]
                  }
                }
              },
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": {
                    "runs": [
                      {"text": "$album"},
                      {"text": " • "},
                      {"text": "2024"}
                    ]
                  }
                }
              }
            ],
            "fixedColumns": [
              {
                "musicResponsiveListItemFixedColumnRenderer": {
                  "text": {"simpleText": "$duration"}
                }
              }
            ],
            "overlay": {
              "musicItemThumbnailOverlayRenderer": {
                "content": {
                  "musicPlayButtonRenderer": {
                    "playNavigationEndpoint": {
                      "watchEndpoint": {
                        "videoId": "$videoId"
                      }
                    }
                  }
                }
              }
            },
            "thumbnail": {
              "musicThumbnailRenderer": {
                "thumbnail": {
                  "thumbnails": [
                    {
                      "url": "https://example.invalid/nova.jpg",
                      "width": 120,
                      "height": 120
                    }
                  ]
                }
              }
            }
          }
        }
    """.trimIndent()

    private fun twoRowPayload(
        browseId: String,
        pageType: String,
        title: String
    ) = """
        {
          "contents": {
            "sectionListRenderer": {
              "contents": [
                {
                  "musicTwoRowItemRenderer": {
                    "title": {
                      "runs": [{"text": "$title"}]
                    },
                    "subtitle": {
                      "runs": [{"text": "Nova Artist"}]
                    },
                    "navigationEndpoint": {
                      "browseEndpoint": {
                        "browseId": "$browseId",
                        "browseEndpointContextSupportedConfigs": {
                          "browseEndpointContextMusicConfig": {
                            "pageType": "$pageType"
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