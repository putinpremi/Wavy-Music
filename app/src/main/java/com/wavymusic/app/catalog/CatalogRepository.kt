package com.wavymusic.app.catalog

import com.wavymusic.app.catalog.matching.TrackMatcher
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
import com.wavymusic.app.catalog.provider.MusicCatalogProvider
import com.wavymusic.app.catalog.storage.CatalogOwnerType
import com.wavymusic.app.catalog.storage.CatalogRecentSearchEntity
import com.wavymusic.app.catalog.storage.CatalogSearchCacheEntity
import com.wavymusic.app.catalog.storage.CatalogSearchCacheResultEntity
import com.wavymusic.app.catalog.storage.CatalogDao
import com.wavymusic.app.catalog.storage.toArtistLinks
import com.wavymusic.app.catalog.storage.toDomain
import com.wavymusic.app.catalog.storage.toEligibilityEntities
import com.wavymusic.app.catalog.storage.toEntity
import com.wavymusic.app.catalog.storage.toProviderIdentityEntities
import com.wavymusic.app.catalog.storage.toSourceEntities
import com.wavymusic.app.catalog.storage.toArtistLinks as toAlbumArtistLinks
import com.wavymusic.app.catalog.storage.toEntity as toCatalogEntity
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

enum class CatalogSearchItemType {
    TRACK,
    ARTIST,
    ALBUM,
    PLAYLIST,
    UNKNOWN
}

data class CatalogSearchHit(
    val providerId: String,
    val itemId: String,
    val itemType: CatalogSearchItemType,
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null
) {
    /** Provider is part of the key so IDs from different providers never collide. */
    val stableKey: String
        get() = "$providerId|${itemType.name}|$itemId"
}

sealed interface CatalogSearchState {
    data object Idle : CatalogSearchState

    /** Cached items remain available while the provider refresh is running. */
    data class Loading(
        val items: List<CatalogSearchHit>,
        val isRefreshing: Boolean
    ) : CatalogSearchState

    data class Success(
        val items: List<CatalogSearchHit>,
        val continuations: List<CatalogContinuation>,
        val fromCache: Boolean = false,
        val warnings: List<CatalogProviderError> = emptyList()
    ) : CatalogSearchState

    data class Empty(
        val continuations: List<CatalogContinuation> = emptyList()
    ) : CatalogSearchState

    data class Offline(
        val items: List<CatalogSearchHit>,
        val message: String
    ) : CatalogSearchState

    data class RateLimited(
        val items: List<CatalogSearchHit>,
        val retryAfterMs: Long?,
        val message: String
    ) : CatalogSearchState

    data class Failure(
        val items: List<CatalogSearchHit>,
        val error: CatalogProviderError
    ) : CatalogSearchState
}

/**
 * Deterministic cache keys. Continuation pages have their own entries,
 * and provider identity is part of every key.
 */
internal object CatalogSearchCacheKeys {
    fun create(
        providerId: String,
        type: CatalogSearchType,
        normalizedQuery: String,
        continuationToken: String? = null
    ): String {
        val input = listOf(
            providerId,
            type.name,
            normalizedQuery,
            continuationToken.orEmpty()
        ).joinToString("\u0000")

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }

        return "catalog:$providerId:$digest"
    }
}

class CatalogRepository(
    providers: List<MusicCatalogProvider>,
    private val dao: CatalogDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val cacheTtlMs: Long = DEFAULT_CACHE_TTL_MS
) {
    private val providers = providers.toList()

    init {
        require(cacheTtlMs > 0L) { "Cache TTL must be positive" }
        require(this.providers.all { it.providerId.isNotBlank() }) {
            "Provider IDs must not be blank"
        }
        require(this.providers.map { it.providerId }.distinct().size ==
            this.providers.size
        ) {
            "Provider IDs must be unique within a repository"
        }
    }

    /**
     * Emits Loading with cached items before the network refresh.
     *
     * Without a continuation, all registered providers supporting the
     * requested type are queried. With one, only its owning provider is
     * queried. The returned page is scoped to that request.
     */
    fun search(
        query: String,
        type: CatalogSearchType,
        pageSize: Int = CatalogSearchRequest.DEFAULT_PAGE_SIZE,
        continuation: CatalogContinuation? = null
    ): Flow<CatalogSearchState> = flow {
        val normalized = TrackMatcher.normalize(query)

        if (normalized.isBlank()) {
            emit(CatalogSearchState.Idle)
            return@flow
        }

        var visibleItems = emptyList<CatalogSearchHit>()

        try {
            require(
                pageSize in CatalogSearchRequest.MIN_PAGE_SIZE..
                    CatalogSearchRequest.MAX_PAGE_SIZE
            ) { "Unsupported page size" }

            if (continuation != null && continuation.isExpired(clock())) {
                emit(
                    CatalogSearchState.Failure(
                        emptyList(),
                        CatalogProviderError(
                            code = CatalogProviderErrorCode.EXPIRED_CONTINUATION,
                            message = "This results page has expired. Search again."
                        )
                    )
                )
                return@flow
            }

            val candidates = selectProviders(type, continuation)

            if (candidates.isEmpty()) {
                emit(
                    CatalogSearchState.Failure(
                        emptyList(),
                        CatalogProviderError(
                            code = CatalogProviderErrorCode.UNSUPPORTED_OPERATION,
                            message = "No configured provider supports ${type.name.lowercase()} search."
                        )
                    )
                )
                return@flow
            }

            if (continuation == null) {
                dao.recordRecentSearch(
                    normalizedQuery = normalized,
                    queryText = query.trim(),
                    searchType = type.name,
                    lastSearchedAtEpochMs = clock()
                )
            }

            val itemsByProvider =
                linkedMapOf<String, List<CatalogSearchHit>>()

            val continuationsByProvider =
                linkedMapOf<String, CatalogContinuation>()

            // Load cached pages for every provider before making requests.
            for (provider in candidates) {
                val cacheKey = CatalogSearchCacheKeys.create(
                    providerId = provider.providerId,
                    type = type,
                    normalizedQuery = normalized,
                    continuationToken = continuation?.token
                )

                val cached = dao.getSearchCache(cacheKey)

                if (cached != null) {
                    val cachedHits = dao.getSearchCacheResults(cacheKey)
                        .map { row ->
                            CatalogSearchHit(
                                providerId = cached.providerId,
                                itemId = row.catalogItemId,
                                itemType = runCatching {
                                    CatalogSearchItemType.valueOf(row.itemType)
                                }.getOrDefault(CatalogSearchItemType.UNKNOWN),
                                title = row.titleSnapshot,
                                subtitle = row.subtitleSnapshot,
                                artworkUrl = row.artworkUrlSnapshot
                            )
                        }

                    itemsByProvider[provider.providerId] = cachedHits

                    val cachedToken = cached.continuationToken
                    if (!cachedToken.isNullOrBlank()) {
                        val next = CatalogContinuation(
                            providerId = cached.continuationProviderId
                                ?: cached.providerId,
                            token = cachedToken,
                            expiresAtEpochMs =
                                cached.continuationExpiresAtEpochMs
                        )

                        if (!next.isExpired(clock())) {
                            continuationsByProvider[provider.providerId] = next
                        }
                    }
                }
            }

            visibleItems = flattenItems(candidates, itemsByProvider)

            emit(
                CatalogSearchState.Loading(
                    items = visibleItems,
                    isRefreshing = visibleItems.isNotEmpty()
                )
            )

            val errors = mutableListOf<CatalogProviderError>()
            var successfulProviders = 0

            for (provider in candidates) {
                val requestContinuation = continuation?.takeIf {
                    it.providerId == provider.providerId
                }

                val request = CatalogSearchRequest(
                    query = query.trim(),
                    pageSize = pageSize,
                    continuation = requestContinuation
                )

                when (val result = requestPage(provider, type, request)) {
                    is CatalogProviderResult.Failure -> {
                        errors += result.error
                    }

                    is CatalogProviderResult.Success -> {
                        val page = result.value
                        val nextToken = page.nextContinuation

                        if (
                            nextToken != null &&
                            nextToken.providerId != provider.providerId
                        ) {
                            errors += CatalogProviderError(
                                code = CatalogProviderErrorCode.PROVIDER_CHANGED,
                                message = "Provider ${provider.providerId} returned a continuation belonging to another provider."
                            )
                            continue
                        }

                        // Persist domain items before adding them to the cache.
                        page.items.forEach { item ->
                            persistCatalogItem(type, item)
                        }

                        val hits = page.items.map { item ->
                            item.toSearchHit(provider.providerId, type)
                        }

                        val cacheKey = CatalogSearchCacheKeys.create(
                            providerId = provider.providerId,
                            type = type,
                            normalizedQuery = normalized,
                            continuationToken = continuation?.token
                        )

                        val now = clock()
                        val validNextToken = nextToken?.takeUnless {
                            it.isExpired(now)
                        }

                        val cache = CatalogSearchCacheEntity(
                            cacheKey = cacheKey,
                            queryText = query.trim(),
                            normalizedQuery = normalized,
                            searchType = type.name,
                            providerId = provider.providerId,
                            cachedAtEpochMs = now,
                            expiresAtEpochMs = now + cacheTtlMs,
                            continuationProviderId =
                                validNextToken?.providerId,
                            continuationToken = validNextToken?.token,
                            continuationExpiresAtEpochMs =
                                validNextToken?.expiresAtEpochMs
                        )

                        val cacheRows = hits.mapIndexed { position, hit ->
                            CatalogSearchCacheResultEntity(
                                cacheKey = cacheKey,
                                position = position,
                                itemType = hit.itemType.name,
                                catalogItemId = hit.itemId,
                                titleSnapshot = hit.title,
                                subtitleSnapshot = hit.subtitle,
                                artworkUrlSnapshot = hit.artworkUrl
                            )
                        }

                        dao.replaceSearchCache(cache, cacheRows)

                        itemsByProvider[provider.providerId] = hits
                        successfulProviders++

                        if (validNextToken != null) {
                            continuationsByProvider[provider.providerId] =
                                validNextToken
                        } else {
                            continuationsByProvider.remove(provider.providerId)
                        }
                    }
                }
            }

            visibleItems = flattenItems(candidates, itemsByProvider)

            val nextPages = continuationsByProvider.values.toList()

            if (errors.isNotEmpty()) {
                if (visibleItems.isNotEmpty()) {
                    emit(
                        CatalogSearchState.Success(
                            items = visibleItems,
                            continuations = nextPages,
                            fromCache = successfulProviders == 0,
                            warnings = errors
                        )
                    )
                } else {
                    val rateLimit = errors.firstOrNull {
                        it.code == CatalogProviderErrorCode.RATE_LIMITED
                    }

                    val offlineOnly = errors.all {
                        it.code == CatalogProviderErrorCode.OFFLINE ||
                            it.code == CatalogProviderErrorCode.NETWORK
                    }

                    when {
                        rateLimit != null -> emit(
                            CatalogSearchState.RateLimited(
                                items = emptyList(),
                                retryAfterMs = rateLimit.retryAfterMs,
                                message = rateLimit.message
                            )
                        )

                        offlineOnly -> emit(
                            CatalogSearchState.Offline(
                                items = emptyList(),
                                message = errors.joinToString { it.message }
                            )
                        )

                        else -> emit(
                            CatalogSearchState.Failure(
                                items = emptyList(),
                                error = errors.first()
                            )
                        )
                    }
                }
            } else if (visibleItems.isEmpty()) {
                emit(CatalogSearchState.Empty(nextPages))
            } else {
                emit(
                    CatalogSearchState.Success(
                        items = visibleItems,
                        continuations = nextPages
                    )
                )
            }
        } catch (cancelled: CancellationException) {
            // Cancellation belongs to the caller and must remain cancellation.
            throw cancelled
        } catch (error: Exception) {
            emit(
                CatalogSearchState.Failure(
                    items = visibleItems,
                    error = CatalogProviderError(
                        code = if (error is IOException) {
                            CatalogProviderErrorCode.OFFLINE
                        } else {
                            CatalogProviderErrorCode.UNKNOWN
                        },
                        message = error.message
                            ?: "Catalog search could not be completed."
                    )
                )
            )
        }
    }

    suspend fun recentSearches(
        type: CatalogSearchType? = null,
        limit: Int = 10
    ): List<CatalogRecentSearchEntity> {
        require(limit in 1..50) { "limit must be between 1 and 50" }
        return dao.getRecentSearches(type?.name, limit)
    }

    suspend fun clearRecentSearches() {
        dao.clearRecentSearches()
    }

    private fun selectProviders(
        type: CatalogSearchType,
        continuation: CatalogContinuation?
    ): List<MusicCatalogProvider> {
        if (continuation == null) {
            return providers.filter { it.capabilities.supports(type) }
        }

        val owner = providers.firstOrNull {
            it.providerId == continuation.providerId
        } ?: return emptyList()

        return if (owner.capabilities.supports(type)) {
            listOf(owner)
        } else {
            emptyList()
        }
    }

    private suspend fun requestPage(
        provider: MusicCatalogProvider,
        type: CatalogSearchType,
        request: CatalogSearchRequest
    ): CatalogProviderResult<CatalogPage<out Any>> {
        return try {
            withContext(Dispatchers.IO) {
                when (type) {
                    CatalogSearchType.TRACKS ->
                        provider.searchTracks(request)

                    CatalogSearchType.ARTISTS ->
                        provider.searchArtists(request)

                    CatalogSearchType.ALBUMS ->
                        provider.searchAlbums(request)

                    CatalogSearchType.PUBLIC_PLAYLISTS ->
                        provider.searchPublicPlaylists(request)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            CatalogProviderResult.Failure(
                CatalogProviderError(
                    CatalogProviderErrorCode.OFFLINE,
                    error.message ?: "Network connection is unavailable."
                )
            )
        } catch (error: Exception) {
            CatalogProviderResult.Failure(
                CatalogProviderError(
                    CatalogProviderErrorCode.UNKNOWN,
                    error.message ?: "Provider request failed."
                )
            )
        }
    }

    private suspend fun persistCatalogItem(
        type: CatalogSearchType,
        item: Any
    ) {
        when (type) {
            CatalogSearchType.TRACKS -> {
                val track = item as CatalogTrack
                dao.upsertTrack(track.toEntity())

                val links = track.toArtistLinks()
                if (links.isNotEmpty()) {
                    dao.deleteTrackArtists(track.id)
                    dao.upsertTrackArtists(links)
                }

                val identities = track.toProviderIdentityEntities()
                if (identities.isNotEmpty()) {
                    dao.upsertProviderIdentities(identities)
                }

                val sources = track.toSourceEntities()
                if (sources.isNotEmpty()) {
                    dao.upsertSourceDescriptors(sources)
                }

                val eligibility = track.toEligibilityEntities()
                if (eligibility.isNotEmpty()) {
                    dao.upsertSourceEligibility(eligibility)
                }
            }

            CatalogSearchType.ARTISTS -> {
                val artist = item as CatalogArtist
                dao.upsertArtist(artist.toEntity())

                val identities = artist.toProviderIdentityEntities()
                if (identities.isNotEmpty()) {
                    dao.upsertProviderIdentities(identities)
                }

                val sources = artist.toSourceEntities()
                if (sources.isNotEmpty()) {
                    dao.upsertSourceDescriptors(sources)
                }
            }

            CatalogSearchType.ALBUMS -> {
                val album = item as CatalogAlbum
                dao.upsertAlbum(album.toEntity())

                val links = album.toAlbumArtistLinks()
                if (links.isNotEmpty()) {
                    dao.deleteAlbumArtists(album.id)
                    dao.upsertAlbumArtists(links)
                }

                val identities = album.toProviderIdentityEntities()
                if (identities.isNotEmpty()) {
                    dao.upsertProviderIdentities(identities)
                }

                val sources = album.toSourceEntities()
                if (sources.isNotEmpty()) {
                    dao.upsertSourceDescriptors(sources)
                }
            }

            CatalogSearchType.PUBLIC_PLAYLISTS -> {
                val playlist = item as CatalogPlaylist
                dao.upsertPlaylist(playlist.toEntity())

                val identities = playlist.toProviderIdentityEntities()
                if (identities.isNotEmpty()) {
                    dao.upsertProviderIdentities(identities)
                }

                val sources = playlist.toSourceEntities()
                if (sources.isNotEmpty()) {
                    dao.upsertSourceDescriptors(sources)
                }
            }
        }
    }

    private fun Any.toSearchHit(
        providerId: String,
        type: CatalogSearchType
    ): CatalogSearchHit = when (type) {
        CatalogSearchType.TRACKS -> {
            val item = this as CatalogTrack
            CatalogSearchHit(
                providerId = providerId,
                itemId = item.id,
                itemType = CatalogSearchItemType.TRACK,
                title = item.title,
                subtitle = item.artistNames
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(" • "),
                artworkUrl = item.artwork?.url
            )
        }

        CatalogSearchType.ARTISTS -> {
            val item = this as CatalogArtist
            CatalogSearchHit(
                providerId = providerId,
                itemId = item.id,
                itemType = CatalogSearchItemType.ARTIST,
                title = item.name,
                subtitle = item.description,
                artworkUrl = item.artwork?.url
            )
        }

        CatalogSearchType.ALBUMS -> {
            val item = this as CatalogAlbum
            CatalogSearchHit(
                providerId = providerId,
                itemId = item.id,
                itemType = CatalogSearchItemType.ALBUM,
                title = item.title,
                subtitle = listOfNotNull(
                    item.artistNames.takeIf { it.isNotEmpty() }
                        ?.joinToString(" • "),
                    item.releaseYear?.toString()
                ).joinToString(" • ").ifBlank { null },
                artworkUrl = item.artwork?.url
            )
        }

        CatalogSearchType.PUBLIC_PLAYLISTS -> {
            val item = this as CatalogPlaylist
            CatalogSearchHit(
                providerId = providerId,
                itemId = item.id,
                itemType = CatalogSearchItemType.PLAYLIST,
                title = item.title,
                subtitle = item.ownerName,
                artworkUrl = item.artwork?.url
            )
        }
    }

    private fun flattenItems(
        candidates: List<MusicCatalogProvider>,
        itemsByProvider: Map<String, List<CatalogSearchHit>>
    ): List<CatalogSearchHit> =
        candidates.flatMap { provider ->
            itemsByProvider[provider.providerId].orEmpty()
        }.distinctBy { it.stableKey }

    companion object {
        const val DEFAULT_CACHE_TTL_MS = 15 * 60 * 1000L
    }
}