package com.wavymusic.app.catalog.provider.innertube

import com.wavymusic.app.catalog.model.CatalogAlbum
import com.wavymusic.app.catalog.model.CatalogArtist
import com.wavymusic.app.catalog.model.CatalogPlaylist
import com.wavymusic.app.catalog.model.CatalogTrack
import com.wavymusic.app.catalog.provider.CatalogPage
import com.wavymusic.app.catalog.provider.CatalogProviderError
import com.wavymusic.app.catalog.provider.CatalogProviderErrorCode
import com.wavymusic.app.catalog.provider.CatalogProviderResult
import com.wavymusic.app.catalog.provider.CatalogSearchRequest
import com.wavymusic.app.catalog.provider.CatalogSearchType
import com.wavymusic.app.catalog.provider.MusicCatalogCapabilities
import com.wavymusic.app.catalog.provider.MusicCatalogProvider
import java.io.IOException
import kotlinx.coroutines.CancellationException

sealed interface InnerTubeTransportResult {
    data class Success(
        val payload: String
    ) : InnerTubeTransportResult

    data class Failure(
        val error: CatalogProviderError
    ) : InnerTubeTransportResult
}

/**
 * Transport boundary for a catalog source that has been independently
 * reviewed for permitted access.
 *
 * Do not implement this by inventing undocumented request contexts,
 * extracting private credentials, bypassing restrictions, or resolving
 * protected playback streams. A concrete implementation must explicitly
 * declare the operations it is permitted to perform.
 */
interface InnerTubeCatalogTransport {
    val supportedSearchTypes: Set<CatalogSearchType>

    suspend fun search(
        type: CatalogSearchType,
        request: CatalogSearchRequest
    ): InnerTubeTransportResult
}

/**
 * Provider adapter: maps transport payloads to the shared catalog contract.
 * It does not own network configuration or credentials.
 */
class InnerTubeCatalogProvider(
    private val transport: InnerTubeCatalogTransport,
    private val parser: InnerTubeCatalogParser = InnerTubeCatalogParser(),
    private val clock: () -> Long = System::currentTimeMillis
) : MusicCatalogProvider {

    override val providerId: String = parser.providerId
    override val displayName: String = "YouTube Music catalog adapter"

    override val capabilities: MusicCatalogCapabilities
        get() {
            val supported = transport.supportedSearchTypes
            return MusicCatalogCapabilities(
                searchTracks = CatalogSearchType.TRACKS in supported,
                searchArtists = CatalogSearchType.ARTISTS in supported,
                searchAlbums = CatalogSearchType.ALBUMS in supported,
                searchPublicPlaylists =
                    CatalogSearchType.PUBLIC_PLAYLISTS in supported
            )
        }

    override suspend fun searchTracks(
        request: CatalogSearchRequest
    ): CatalogProviderResult<CatalogPage<CatalogTrack>> =
        search(request, CatalogSearchType.TRACKS) { item ->
            (item as? InnerTubeCatalogItem.Track)?.value
        }

    override suspend fun searchArtists(
        request: CatalogSearchRequest
    ): CatalogProviderResult<CatalogPage<CatalogArtist>> =
        search(request, CatalogSearchType.ARTISTS) { item ->
            (item as? InnerTubeCatalogItem.Artist)?.value
        }

    override suspend fun searchAlbums(
        request: CatalogSearchRequest
    ): CatalogProviderResult<CatalogPage<CatalogAlbum>> =
        search(request, CatalogSearchType.ALBUMS) { item ->
            (item as? InnerTubeCatalogItem.Album)?.value
        }

    override suspend fun searchPublicPlaylists(
        request: CatalogSearchRequest
    ): CatalogProviderResult<CatalogPage<CatalogPlaylist>> =
        search(request, CatalogSearchType.PUBLIC_PLAYLISTS) { item ->
            (item as? InnerTubeCatalogItem.Playlist)?.value
        }

    private suspend fun <T : Any> search(
        request: CatalogSearchRequest,
        type: CatalogSearchType,
        mapItem: (InnerTubeCatalogItem) -> T?
    ): CatalogProviderResult<CatalogPage<T>> {
        if (!capabilities.supports(type)) {
            return failure(
                CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
                "This adapter has not enabled ${type.name.lowercase()} search."
            )
        }

        val continuation = request.continuation

        if (continuation != null && continuation.providerId != providerId) {
            return failure(
                CatalogProviderErrorCode.INVALID_CONTINUATION,
                "The continuation belongs to another provider."
            )
        }

        if (continuation?.isExpired(clock()) == true) {
            return failure(
                CatalogProviderErrorCode.EXPIRED_CONTINUATION,
                "The results page expired. Start a new search."
            )
        }

        val transportResult = try {
            transport.search(type, request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (offline: IOException) {
            return failure(
                CatalogProviderErrorCode.OFFLINE,
                offline.message ?: "Catalog source is offline."
            )
        } catch (error: Exception) {
            return failure(
                CatalogProviderErrorCode.UNKNOWN,
                error.message ?: "Catalog transport failed."
            )
        }

        return when (transportResult) {
            is InnerTubeTransportResult.Failure ->
                CatalogProviderResult.Failure(transportResult.error)

            is InnerTubeTransportResult.Success -> {
                when (
                    val parsed = parser.parse(
                        payload = transportResult.payload,
                        searchType = type,
                        nowEpochMs = clock()
                    )
                ) {
                    is CatalogProviderResult.Failure -> parsed

                    is CatalogProviderResult.Success -> {
                        val items = parsed.value.items.mapNotNull(mapItem)

                        CatalogProviderResult.Success(
                            CatalogPage(
                                items = items,
                                nextContinuation = parsed.value.continuation
                            )
                        )
                    }
                }
            }
        }
    }

    private fun failure(
        code: CatalogProviderErrorCode,
        message: String
    ) = CatalogProviderResult.Failure(
        CatalogProviderError(
            code = code,
            message = message
        )
    )
}