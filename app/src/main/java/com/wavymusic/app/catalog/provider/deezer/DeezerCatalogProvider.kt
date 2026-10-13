
package com.wavymusic.app.catalog.provider.deezer

import com.wavymusic.app.catalog.model.CatalogAlbum
import com.wavymusic.app.catalog.model.CatalogArtist
import com.wavymusic.app.catalog.model.CatalogArtwork
import com.wavymusic.app.catalog.model.CatalogPlaylist
import com.wavymusic.app.catalog.model.CatalogReleaseType
import com.wavymusic.app.catalog.model.CatalogSourceDescriptor
import com.wavymusic.app.catalog.model.CatalogSourceKind
import com.wavymusic.app.catalog.model.CatalogTrack
import com.wavymusic.app.catalog.model.ProviderItemId
import com.wavymusic.app.catalog.model.SourceEligibilityReport
import com.wavymusic.app.catalog.model.SourceEligibilityStatus
import com.wavymusic.app.catalog.provider.CatalogContinuation
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
import java.net.URL
import java.net.URLEncoder
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

sealed interface DeezerTransportResult {
    data class Success(val payload: String) : DeezerTransportResult
    data class Failure(val error: CatalogProviderError) : DeezerTransportResult
}

interface DeezerCatalogTransport {
    suspend fun search(
        query: String,
        type: CatalogSearchType,
        pageSize: Int,
        index: Int
    ): DeezerTransportResult
}

class DeezerCatalogProvider(
    private val transport: DeezerCatalogTransport,
    private val clock: () -> Long = System::currentTimeMillis
) : MusicCatalogProvider {

    override val providerId = PROVIDER_ID
    override val displayName = "Deezer catalog"

    override val capabilities = MusicCatalogCapabilities(
        searchTracks = true,
        searchArtists = true,
        searchAlbums = true,
        searchPublicPlaylists = true
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
        search(request, CatalogSearchType.PUBLIC_PLAYLISTS, ::parsePlaylist)

    private suspend fun <T : Any> search(
        request: CatalogSearchRequest,
        type: CatalogSearchType,
        mapItem: (JSONObject) -> T?
    ): CatalogProviderResult<CatalogPage<T>> {
        if (request.query.isBlank()) {
            return CatalogProviderResult.Success(CatalogPage(emptyList()))
        }

        val now = clock()
        val continuation = request.continuation

        if (continuation != null) {
            if (continuation.providerId != providerId) {
                return failure(
                    CatalogProviderErrorCode.INVALID_CONTINUATION,
                    "The continuation belongs to another provider."
                )
            }

            if (continuation.isExpired(now)) {
                return failure(
                    CatalogProviderErrorCode.EXPIRED_CONTINUATION,
                    "This results page has expired. Start a new search."
                )
            }
        }

        val index = continuation?.token?.toIntOrNull() ?: 0

        if (
            index < 0 ||
            index > MAX_OFFSET ||
            (continuation != null && continuation.token.toIntOrNull() == null)
        ) {
            return failure(
                CatalogProviderErrorCode.INVALID_CONTINUATION,
                "The catalog continuation is invalid."
            )
        }

        val response = try {
            transport.search(
                query = request.query.trim(),
                type = type,
                pageSize = request.pageSize,
                index = index
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            return failure(
                CatalogProviderErrorCode.OFFLINE,
                error.message ?: "The catalog network request failed."
            )
        } catch (error: Exception) {
            return failure(
                CatalogProviderErrorCode.UNKNOWN,
                error.message ?: "The catalog request could not be completed."
            )
        }

        return when (response) {
            is DeezerTransportResult.Failure ->
                CatalogProviderResult.Failure(response.error)

            is DeezerTransportResult.Success ->
                parsePayload(
                    payload = response.payload,
                    index = index,
                    mapItem = mapItem
                )
        }
    }

    private fun <T : Any> parsePayload(
        payload: String,
        index: Int,
        mapItem: (JSONObject) -> T?
    ): CatalogProviderResult<CatalogPage<T>> {
        val root = try {
            JSONObject(payload)
        } catch (_: JSONException) {
            return failure(
                CatalogProviderErrorCode.MALFORMED_RESPONSE,
                "The catalog response was not valid JSON."
            )
        }

        val apiError = root.optJSONObject("error")
        if (apiError != null) {
            val message = apiError.optString(
                "message",
                "The catalog service returned an error."
            ).ifBlank { "The catalog service returned an error." }

            val code = if (
                message.contains("rate", ignoreCase = true) ||
                message.contains("quota", ignoreCase = true)
            ) {
                CatalogProviderErrorCode.RATE_LIMITED
            } else {
                CatalogProviderErrorCode.SOURCE_UNAVAILABLE
            }

            return failure(code, message)
        }

        val rows = root.optJSONArray("data")
            ?: return failure(
                CatalogProviderErrorCode.MALFORMED_RESPONSE,
                "The catalog response did not include a data array."
            )

        val items = mutableListOf<T>()
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            mapItem(row)?.let(items::add)
        }

        val nextIndex = index + rows.length()
        val total = root.optInt("total", -1)
        val hasNextLink = root.optString("next", "")
            .let { it.isNotBlank() && it != "null" }

        val hasMore = rows.length() > 0 &&
            (hasNextLink || (total >= 0 && nextIndex < total))

        val nextContinuation = if (hasMore) {
            CatalogContinuation(
                providerId = PROVIDER_ID,
                token = nextIndex.toString(),
                expiresAtEpochMs = safeAdd(clock(), CONTINUATION_TTL_MS)
            )
        } else {
            null
        }

        return CatalogProviderResult.Success(
            CatalogPage(
                items = items,
                nextContinuation = nextContinuation
            )
        )
    }

    private fun parseTrack(row: JSONObject): CatalogTrack? {
        val itemId = row.numericId("id") ?: return null
        val title = row.optionalText("title") ?: return null

        val artist = row.optJSONObject("artist")
        val album = row.optJSONObject("album")
        val artistId = artist?.numericId("id")
        val artistName = artist?.optionalText("name")
        val albumId = album?.numericId("id")
        val albumTitle = album?.optionalText("title")
        val durationSeconds = row.optLong("duration", 0L)
        val now = clock()

        return CatalogTrack(
            id = "$PROVIDER_ID:track:$itemId",
            title = title,
            providerIds = setOf(
                ProviderItemId(PROVIDER_ID, itemId)
            ),
            artistIds = listOfNotNull(
                artistId?.let { "$PROVIDER_ID:artist:$it" }
            ),
            artistNames = listOfNotNull(artistName),
            albumId = albumId?.let { "$PROVIDER_ID:album:$it" },
            releaseTitle = albumTitle,
            durationMs = durationSeconds
                .takeIf { it > 0L && it <= Long.MAX_VALUE / 1_000L }
                ?.times(1_000L),
            artwork = album?.optionalText("cover_medium")?.let(::artwork),
            sources = listOf(
                catalogSource(
                    itemId = itemId,
                    kind = CatalogSourceKind.TRACK,
                    url = row.optionalText("link")
                )
            ),
            sourceEligibility = listOf(
                SourceEligibilityReport(
                    providerId = PROVIDER_ID,
                    providerItemId = itemId,
                    status = SourceEligibilityStatus.CATALOG_ONLY,
                    checkedAtEpochMs = now,
                    reason = "Catalog metadata only. Playback has not been verified."
                )
            )
        )
    }

    private fun parseArtist(row: JSONObject): CatalogArtist? {
        val itemId = row.numericId("id") ?: return null
        val name = row.optionalText("name") ?: return null

        return CatalogArtist(
            id = "$PROVIDER_ID:artist:$itemId",
            name = name,
            providerIds = setOf(
                ProviderItemId(PROVIDER_ID, itemId)
            ),
            artwork = row.optionalText("picture_medium")?.let(::artwork),
            sources = listOf(
                catalogSource(
                    itemId = itemId,
                    kind = CatalogSourceKind.ARTIST,
                    url = row.optionalText("link")
                )
            )
        )
    }

    private fun parseAlbum(row: JSONObject): CatalogAlbum? {
        val itemId = row.numericId("id") ?: return null
        val title = row.optionalText("title") ?: return null
        val artist = row.optJSONObject("artist")
        val artistId = artist?.numericId("id")
        val artistName = artist?.optionalText("name")
        val releaseDate = row.optionalText("release_date")

        return CatalogAlbum(
            id = "$PROVIDER_ID:album:$itemId",
            title = title,
            providerIds = setOf(
                ProviderItemId(PROVIDER_ID, itemId)
            ),
            artistIds = listOfNotNull(
                artistId?.let { "$PROVIDER_ID:artist:$it" }
            ),
            artistNames = listOfNotNull(artistName),
            releaseDate = releaseDate,
            releaseYear = releaseDate?.take(4)?.toIntOrNull(),
            releaseType = releaseType(row.optionalText("record_type")),
            trackCount = row.optInt("nb_tracks", -1)
                .takeIf { it >= 0 },
            artwork = row.optionalText("cover_medium")?.let(::artwork),
            sources = listOf(
                catalogSource(
                    itemId = itemId,
                    kind = CatalogSourceKind.ALBUM,
                    url = row.optionalText("link")
                )
            )
        )
    }

    private fun parsePlaylist(row: JSONObject): CatalogPlaylist? {
        val itemId = row.numericId("id") ?: return null
        val title = row.optionalText("title") ?: return null

        // Public visibility must be explicit; never infer it from the title or URL.
        val isPublic = row.optBoolean("public", false)
        if (!isPublic) return null

        val ownerName = row.optJSONObject("user")?.optionalText("name")

        return CatalogPlaylist(
            id = "$PROVIDER_ID:playlist:$itemId",
            title = title,
            providerIds = setOf(
                ProviderItemId(PROVIDER_ID, itemId)
            ),
            ownerName = ownerName,
            isPublic = true,
            isSearchablePublicly = true,
            entryCount = row.optInt("nb_tracks", -1)
                .takeIf { it >= 0 },
            artwork = row.optionalText("picture_medium")?.let(::artwork),
            sources = listOf(
                catalogSource(
                    itemId = itemId,
                    kind = CatalogSourceKind.PLAYLIST,
                    url = row.optionalText("link")
                )
            )
        )
    }

    private fun artwork(value: String): CatalogArtwork? {
        val safe = safeArtworkUrl(value) ?: return null
        return CatalogArtwork(url = safe)
    }

    private fun catalogSource(
        itemId: String,
        kind: CatalogSourceKind,
        url: String?
    ) = CatalogSourceDescriptor(
        id = "$PROVIDER_ID:${kind.name.lowercase(Locale.ROOT)}:$itemId",
        providerId = PROVIDER_ID,
        providerItemId = itemId,
        kind = kind,
        catalogUri = safeCatalogUrl(url)
    )

    private fun releaseType(value: String?): CatalogReleaseType =
        when (value?.lowercase(Locale.ROOT)) {
            "album" -> CatalogReleaseType.ALBUM
            "single" -> CatalogReleaseType.SINGLE
            "ep" -> CatalogReleaseType.EP
            "compile", "compilation" -> CatalogReleaseType.COMPILATION
            "soundtrack" -> CatalogReleaseType.SOUNDTRACK
            "other" -> CatalogReleaseType.OTHER
            else -> CatalogReleaseType.UNKNOWN
        }

    private fun <T : Any> failure(
        code: CatalogProviderErrorCode,
        message: String
    ): CatalogProviderResult<CatalogPage<T>> =
        CatalogProviderResult.Failure(
            CatalogProviderError(
                code = code,
                message = message.ifBlank { "The catalog request failed." }
            )
        )

    companion object {
        const val PROVIDER_ID = "deezer-public"

        const val DEFAULT_PAGE_SIZE = 25
        const val MAX_PAGE_SIZE = 100
        const val MAX_OFFSET = 10_000

        private const val CONTINUATION_TTL_MS = 15 * 60 * 1_000L

        private fun safeAdd(a: Long, b: Long): Long =
            if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
    }
}

class HttpDeezerCatalogTransport(
    private val minRequestIntervalMs: Long = 1_200L,
    private val clockMs: () -> Long = System::currentTimeMillis
) : DeezerCatalogTransport {

    private val mutex = Mutex()
    private var lastRequestStartedAtMs: Long? = null

    init {
        require(minRequestIntervalMs >= 0L)
    }

    override suspend fun search(
        query: String,
        type: CatalogSearchType,
        pageSize: Int,
        index: Int
    ): DeezerTransportResult {
        if (query.isBlank()) {
            return transportFailure(
                CatalogProviderErrorCode.MALFORMED_RESPONSE,
                "The search query cannot be blank."
            )
        }

        if (index !in 0..DeezerCatalogProvider.MAX_OFFSET) {
            return transportFailure(
                CatalogProviderErrorCode.INVALID_CONTINUATION,
                "The requested page offset is invalid."
            )
        }

        return mutex.withLock {
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
                executeSearch(query, type, pageSize, index)
            }
        }
    }

    internal fun buildSearchUrl(
        query: String,
        type: CatalogSearchType,
        pageSize: Int,
        index: Int
    ): String {
        val endpoint = when (type) {
            CatalogSearchType.TRACKS -> "track"
            CatalogSearchType.ARTISTS -> "artist"
            CatalogSearchType.ALBUMS -> "album"
            CatalogSearchType.PUBLIC_PLAYLISTS -> "playlist"
        }

        val encodedQuery = URLEncoder.encode(
            query,
            StandardCharsets.UTF_8.name()
        )

        val limit = pageSize.coerceIn(
            1,
            DeezerCatalogProvider.MAX_PAGE_SIZE
        )

        val safeIndex = index.coerceIn(0, DeezerCatalogProvider.MAX_OFFSET)

        return "$API_ROOT/search/$endpoint" +
            "?q=$encodedQuery&limit=$limit&index=$safeIndex"
    }

    private fun executeSearch(
        query: String,
        type: CatalogSearchType,
        pageSize: Int,
        index: Int
    ): DeezerTransportResult {
        var connection: HttpURLConnection? = null

        return try {
            connection = URL(
                buildSearchUrl(query, type, pageSize, index)
            ).openConnection() as HttpURLConnection

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
                        DeezerTransportResult.Success(payload)
                    }
                }

                HTTP_TOO_MANY_REQUESTS ->
                    transportFailure(
                        CatalogProviderErrorCode.RATE_LIMITED,
                        "Deezer has temporarily rate-limited requests.",
                        retryAfterMs(connection)
                    )

                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN ->
                    transportFailure(
                        CatalogProviderErrorCode.AUTHORIZATION_REQUIRED,
                        "Deezer denied this catalog request."
                    )

                in 500..599 ->
                    transportFailure(
                        CatalogProviderErrorCode.SOURCE_UNAVAILABLE,
                        "Deezer is temporarily unavailable (HTTP $status)."
                    )

                else ->
                    transportFailure(
                        CatalogProviderErrorCode.SOURCE_UNAVAILABLE,
                        "Deezer returned HTTP $status."
                    )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (offline: IOException) {
            transportFailure(
                CatalogProviderErrorCode.OFFLINE,
                offline.message ?: "Could not connect to Deezer."
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun retryAfterMs(connection: HttpURLConnection): Long? {
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
    ) = DeezerTransportResult.Failure(
        CatalogProviderError(
            code = code,
            message = message,
            retryAfterMs = retryAfterMs
        )
    )

    companion object {
        private const val API_ROOT = "https://api.deezer.com"
        private const val NETWORK_TIMEOUT_MS = 10_000
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}

private fun JSONObject.optionalText(key: String): String? {
    if (isNull(key)) return null

    val value = when (val raw = opt(key)) {
        is String -> raw
        is Number -> raw.toString()
        else -> return null
    }

    return value.trim().takeIf {
        it.isNotEmpty() && it != "null"
    }
}

private fun JSONObject.numericId(key: String): String? =
    optionalText(key)?.takeIf {
        it.toLongOrNull() != null && it != "0"
    }

private fun safeCatalogUrl(value: String?): String? {
    if (value.isNullOrBlank()) return null

    return runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase(Locale.ROOT)

        if (
            uri.scheme.equals("https", ignoreCase = true) &&
            host != null &&
            (host == "deezer.com" || host.endsWith(".deezer.com"))
        ) {
            uri.toString()
        } else {
            null
        }
    }.getOrNull()
}

private fun safeArtworkUrl(value: String?): String? {
    if (value.isNullOrBlank()) return null

    return runCatching {
        val uri = URI(value)
        val host = uri.host?.lowercase(Locale.ROOT)

        if (
            uri.scheme.equals("https", ignoreCase = true) &&
            host != null &&
            (host == "dzcdn.net" || host.endsWith(".dzcdn.net"))
        ) {
            uri.toString()
        } else {
            null
        }
    }.getOrNull()
}
