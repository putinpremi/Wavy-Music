
package com.wavymusic.app.catalog.provider.itunes

import com.wavymusic.app.catalog.model.CatalogAlbum
import com.wavymusic.app.catalog.model.CatalogArtist
import com.wavymusic.app.catalog.model.CatalogReleaseType
import com.wavymusic.app.catalog.model.CatalogSourceDescriptor
import com.wavymusic.app.catalog.model.CatalogSourceKind
import com.wavymusic.app.catalog.model.CatalogTrack
import com.wavymusic.app.catalog.model.ProviderItemId
import com.wavymusic.app.catalog.model.SourceEligibilityReport
import com.wavymusic.app.catalog.model.SourceEligibilityStatus
import com.wavymusic.app.catalog.provider.CatalogPage
import com.wavymusic.app.catalog.provider.CatalogProviderError
import com.wavymusic.app.catalog.provider.CatalogProviderErrorCode
import com.wavymusic.app.catalog.provider.CatalogProviderResult
import com.wavymusic.app.catalog.provider.CatalogSearchRequest
import com.wavymusic.app.catalog.provider.CatalogSearchType
import com.wavymusic.app.catalog.provider.MusicCatalogCapabilities
import com.wavymusic.app.catalog.provider.MusicCatalogProvider
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import com.wavymusic.app.catalog.model.CatalogPlaylist

sealed interface ITunesTransportResult {
    data class Success(
        val payload: String
    ) : ITunesTransportResult

    data class Failure(
        val error: CatalogProviderError
    ) : ITunesTransportResult
}

/**
 * Testable boundary between the provider's metadata mapping and HTTP.
 * The transport returns catalog JSON only; it never resolves playable media.
 */
interface ITunesCatalogTransport {
    suspend fun search(
        query: String,
        type: CatalogSearchType,
        pageSize: Int
    ): ITunesTransportResult
}

class ITunesCatalogProvider(
    private val transport: ITunesCatalogTransport,
    private val clock: () -> Long = System::currentTimeMillis
) : MusicCatalogProvider {

    override val providerId: String = PROVIDER_ID
    override val displayName: String = "Apple iTunes catalog"

    override val capabilities = MusicCatalogCapabilities(
        searchTracks = true,
        searchArtists = true,
        searchAlbums = true,
        searchPublicPlaylists = false
    )

    override suspend fun searchTracks(
        request: CatalogSearchRequest
    ): CatalogProviderResult<CatalogPage<CatalogTrack>> =
        search(request, CatalogSearchType.TRACKS, ::parseTrack)

    override suspend fun searchArtists(
        request: CatalogSearchRequest
    ): CatalogProviderResult<CatalogPage<CatalogArtist>> =
        search(request, CatalogSearchType.ARTISTS, ::parseArtist)

    override suspend fun searchAlbums(
        request: CatalogSearchRequest
    ): CatalogProviderResult<CatalogPage<CatalogAlbum>> =
        search(request, CatalogSearchType.ALBUMS, ::parseAlbum)

    override suspend fun searchPublicPlaylists(
        request: CatalogSearchRequest
    ): CatalogProviderResult<CatalogPage<CatalogPlaylist>> =
        failure(
            CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
            "The Apple iTunes Search API does not support public playlist search."
        )

    private suspend fun <T : Any> search(
        request: CatalogSearchRequest,
        type: CatalogSearchType,
        mapItem: (JSONObject) -> T?
    ): CatalogProviderResult<CatalogPage<T>> {
        if (!capabilities.supports(type)) {
            return failure(
                CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
                "${type.name.lowercase()} search is not supported by this provider."
            )
        }

        if (request.query.isBlank()) {
            return CatalogProviderResult.Success(CatalogPage(emptyList()))
        }

        val continuation = request.continuation
        if (continuation != null) {
            if (continuation.providerId != providerId) {
                return failure(
                    CatalogProviderErrorCode.INVALID_CONTINUATION,
                    "The continuation belongs to another provider."
                )
            }

            // This endpoint does not document an offset/cursor parameter.
            // Do not fabricate a continuation or repeat page one.
            return failure(
                CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
                "This provider does not expose catalog pagination."
            )
        }

        val response = try {
            transport.search(
                query = request.query.trim(),
                type = type,
                pageSize = request.pageSize
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (offline: IOException) {
            return failure(
                CatalogProviderErrorCode.OFFLINE,
                offline.message ?: "The catalog network request failed."
            )
        } catch (error: Exception) {
            return failure(
                CatalogProviderErrorCode.UNKNOWN,
                error.message ?: "The catalog request could not be completed."
            )
        }

        return when (response) {
            is ITunesTransportResult.Failure ->
                CatalogProviderResult.Failure(response.error)

            is ITunesTransportResult.Success ->
                parsePayload(response.payload, mapItem)
        }
    }

    private fun <T : Any> parsePayload(
        payload: String,
        mapItem: (JSONObject) -> T?
    ): CatalogProviderResult<CatalogPage<T>> {
        val rows = try {
            JSONObject(payload).optJSONArray("results")
                ?: return failure(
                    CatalogProviderErrorCode.MALFORMED_RESPONSE,
                    "The catalog response did not include a results array."
                )
        } catch (_: JSONException) {
            return failure(
                CatalogProviderErrorCode.MALFORMED_RESPONSE,
                "The catalog response was not valid JSON."
            )
        }

        val items = mutableListOf<T>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            mapItem(row)?.let(items::add)
        }

        return CatalogProviderResult.Success(
            CatalogPage(items = items)
        )
    }

    private fun parseTrack(row: JSONObject): CatalogTrack? {
        if (row.optionalText("kind") != "song") return null

        val itemId = row.numericId("trackId") ?: return null
        val title = row.optionalText("trackName") ?: return null
        val artistName = row.optionalText("artistName")
        val artistId = row.numericId("artistId")
        val albumId = row.numericId("collectionId")
        val releaseDate = row.optionalText("releaseDate")

        return CatalogTrack(
            id = "$PROVIDER_ID:track:$itemId",
            title = title,
            providerIds = setOf(
                ProviderItemId(
                    providerId = PROVIDER_ID,
                    itemId = itemId
                )
            ),
            artistIds = listOfNotNull(
                artistId?.let { "$PROVIDER_ID:artist:$it" }
            ),
            artistNames = listOfNotNull(artistName),
            albumId = albumId?.let { "$PROVIDER_ID:album:$it" },
            releaseTitle = row.optionalText("collectionName"),
            releaseYear = releaseDate?.take(4)?.toIntOrNull(),
            durationMs = row.optLong("trackTimeMillis", 0L)
                .takeIf { it > 0L },
            // Artwork and previews are intentionally not consumed in this
            // batch; their usage requires the appropriate store promotion.
            artwork = null,
            sources = listOf(
                catalogSource(
                    itemId = itemId,
                    kind = CatalogSourceKind.TRACK,
                    catalogUrl = row.optionalText("trackViewUrl")
                )
            ),
            sourceEligibility = listOf(
                SourceEligibilityReport(
                    providerId = PROVIDER_ID,
                    providerItemId = itemId,
                    status = SourceEligibilityStatus.CATALOG_ONLY,
                    checkedAtEpochMs = clock(),
                    reason = "Catalog metadata only. A playable source has not been verified."
                )
            )
        )
    }

    private fun parseArtist(row: JSONObject): CatalogArtist? {
        val itemId = row.numericId("artistId") ?: return null
        val name = row.optionalText("artistName") ?: return null

        return CatalogArtist(
            id = "$PROVIDER_ID:artist:$itemId",
            name = name,
            providerIds = setOf(
                ProviderItemId(
                    providerId = PROVIDER_ID,
                    itemId = itemId
                )
            ),
            sources = listOf(
                catalogSource(
                    itemId = itemId,
                    kind = CatalogSourceKind.ARTIST,
                    catalogUrl = row.optionalText("artistViewUrl")
                )
            )
        )
    }

    private fun parseAlbum(row: JSONObject): CatalogAlbum? {
        if (row.optionalText("wrapperType") != "collection") return null

        val itemId = row.numericId("collectionId") ?: return null
        val title = row.optionalText("collectionName") ?: return null
        val artistId = row.numericId("artistId")
        val artistName = row.optionalText("artistName")
        val releaseDate = row.optionalText("releaseDate")

        return CatalogAlbum(
            id = "$PROVIDER_ID:album:$itemId",
            title = title,
            providerIds = setOf(
                ProviderItemId(
                    providerId = PROVIDER_ID,
                    itemId = itemId
                )
            ),
            artistIds = listOfNotNull(
                artistId?.let { "$PROVIDER_ID:artist:$it" }
            ),
            artistNames = listOfNotNull(artistName),
            releaseDate = releaseDate,
            releaseYear = releaseDate?.take(4)?.toIntOrNull(),
            releaseType = CatalogReleaseType.ALBUM,
            trackCount = row.optInt("trackCount", -1)
                .takeIf { it >= 0 },
            sources = listOf(
                catalogSource(
                    itemId = itemId,
                    kind = CatalogSourceKind.ALBUM,
                    catalogUrl = row.optionalText("collectionViewUrl")
                )
            )
        )
    }

    private fun catalogSource(
        itemId: String,
        kind: CatalogSourceKind,
        catalogUrl: String?
    ) = CatalogSourceDescriptor(
        id = "$PROVIDER_ID:${kind.name.lowercase()}:$itemId",
        providerId = PROVIDER_ID,
        providerItemId = itemId,
        kind = kind,
        catalogUri = safeAppleStoreUrl(catalogUrl)
    )

    private fun <T : Any> failure(
        code: CatalogProviderErrorCode,
        message: String,
        retryAfterMs: Long? = null
    ): CatalogProviderResult<CatalogPage<T>> =
        CatalogProviderResult.Failure(
            CatalogProviderError(
                code = code,
                message = message,
                retryAfterMs = retryAfterMs
            )
        )

    private fun JSONObject.optionalText(key: String): String? {
        if (isNull(key)) return null

        val value = when (val raw = opt(key)) {
            is String -> raw
            is Number -> raw.toString()
            else -> return null
        }

        return value.trim()
            .takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun JSONObject.numericId(key: String): String? =
        optionalText(key)?.takeIf { it.toLongOrNull() != null }

    companion object {
        const val PROVIDER_ID = "itunes-search"
    }
}

/**
 * HTTP transport for Apple's documented public iTunes Search API.
 *
 * The endpoint documents a limit parameter, but no offset/cursor parameter.
 * This implementation therefore limits requests and does not claim paging.
 */
class HttpITunesCatalogTransport(
    countryCode: String = "IN",
    private val minRequestIntervalMs: Long = MIN_REQUEST_INTERVAL_MS,
    private val clockMs: () -> Long = System::currentTimeMillis
) : ITunesCatalogTransport {

    private val country = countryCode.lowercase(Locale.ROOT)
    private val requestMutex = Mutex()
    private var lastRequestStartedAtMs: Long? = null

    init {
        require(countryCode.matches(Regex("[A-Za-z]{2}"))) {
            "countryCode must be a two-letter country code."
        }
        require(minRequestIntervalMs >= 0L) {
            "minRequestIntervalMs cannot be negative."
        }
    }

    override suspend fun search(
        query: String,
        type: CatalogSearchType,
        pageSize: Int
    ): ITunesTransportResult {
        if (type == CatalogSearchType.PUBLIC_PLAYLISTS) {
            return transportFailure(
                CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
                "Public playlist search is not supported."
            )
        }

        return requestMutex.withLock {
            val previous = lastRequestStartedAtMs
            if (previous != null) {
                val elapsed = (clockMs() - previous).coerceAtLeast(0L)
                val waitMs = (minRequestIntervalMs - elapsed)
                    .coerceAtLeast(0L)

                if (waitMs > 0L) delay(waitMs)
            }

            currentCoroutineContext().ensureActive()
            lastRequestStartedAtMs = clockMs()

            withContext(Dispatchers.IO) {
                executeSearch(query, type, pageSize)
            }
        }
    }

    internal fun buildSearchUrl(
        query: String,
        type: CatalogSearchType,
        pageSize: Int
    ): String {
        val entity = when (type) {
            CatalogSearchType.TRACKS -> "song"
            CatalogSearchType.ARTISTS -> "musicArtist"
            CatalogSearchType.ALBUMS -> "album"
            CatalogSearchType.PUBLIC_PLAYLISTS ->
                throw IllegalArgumentException("Playlist search is unsupported.")
        }

        val encodedTerm = URLEncoder.encode(
            query,
            StandardCharsets.UTF_8.name()
        )

        val limit = pageSize.coerceIn(1, MAX_RESULTS)

        return "$SEARCH_ENDPOINT?term=$encodedTerm" +
            "&country=$country" +
            "&media=music" +
            "&entity=$entity" +
            "&limit=$limit"
    }

    private fun executeSearch(
        query: String,
        type: CatalogSearchType,
        pageSize: Int
    ): ITunesTransportResult {
        var connection: HttpURLConnection? = null

        return try {
            connection = URL(buildSearchUrl(query, type, pageSize))
                .openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = NETWORK_TIMEOUT_MS
            connection.readTimeout = NETWORK_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty(
                "User-Agent",
                "WavyMusic/0.1 (Android catalog metadata)"
            )

            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val payload = connection.inputStream
                        .bufferedReader(StandardCharsets.UTF_8)
                        .use { it.readText() }

                    if (payload.isBlank()) {
                        transportFailure(
                            CatalogProviderErrorCode.MALFORMED_RESPONSE,
                            "The catalog returned an empty response."
                        )
                    } else {
                        ITunesTransportResult.Success(payload)
                    }
                }

                HTTP_TOO_MANY_REQUESTS ->
                    transportFailure(
                        CatalogProviderErrorCode.RATE_LIMITED,
                        "The catalog is temporarily rate-limiting requests.",
                        retryAfterMs = retryAfterMs(connection)
                    )

                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN ->
                    transportFailure(
                        CatalogProviderErrorCode.AUTHORIZATION_REQUIRED,
                        "The catalog denied this request."
                    )

                in 500..599 ->
                    transportFailure(
                        CatalogProviderErrorCode.SOURCE_UNAVAILABLE,
                        "The catalog service is temporarily unavailable (HTTP $status)."
                    )

                else ->
                    transportFailure(
                        CatalogProviderErrorCode.SOURCE_UNAVAILABLE,
                        "The catalog returned HTTP $status."
                    )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (offline: IOException) {
            transportFailure(
                CatalogProviderErrorCode.OFFLINE,
                offline.message ?: "Could not connect to the catalog service."
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun retryAfterMs(
        connection: HttpURLConnection
    ): Long? {
        val seconds = connection.getHeaderField("Retry-After")
            ?.trim()
            ?.toLongOrNull()
            ?.coerceAtLeast(0L)
            ?: return null

        return seconds.coerceAtMost(Long.MAX_VALUE / 1_000L) * 1_000L
    }

    private fun transportFailure(
        code: CatalogProviderErrorCode,
        message: String,
        retryAfterMs: Long? = null
    ) = ITunesTransportResult.Failure(
        CatalogProviderError(
            code = code,
            message = message,
            retryAfterMs = retryAfterMs
        )
    )

    companion object {
        private const val SEARCH_ENDPOINT = "https://itunes.apple.com/search"
        private const val NETWORK_TIMEOUT_MS = 10_000
        private const val MAX_RESULTS = 50

        // Apple's Search API documents approximately 20 calls/minute.
        private const val MIN_REQUEST_INTERVAL_MS = 3_500L
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

private fun safeAppleStoreUrl(value: String?): String? {
    if (value.isNullOrBlank()) return null

    return runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase(Locale.ROOT)

        if (
            uri.scheme.equals("https", ignoreCase = true) &&
            host != null &&
            host in setOf("itunes.apple.com", "music.apple.com")
        ) {
            uri.toString()
        } else {
            null
        }
    }.getOrNull()
}

/**
 * Placeholder type so the unsupported playlist method can return the same
 * generic shape without suggesting playlist support.
 */
data class CatalogPlaylistPlaceholder(
    val unsupported: Boolean = true
)
