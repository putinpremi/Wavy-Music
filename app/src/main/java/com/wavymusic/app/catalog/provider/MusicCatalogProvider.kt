package com.wavymusic.app.catalog.provider

import com.wavymusic.app.catalog.model.CatalogAlbum
import com.wavymusic.app.catalog.model.CatalogArtist
import com.wavymusic.app.catalog.model.CatalogPlaylist
import com.wavymusic.app.catalog.model.CatalogTrack

/** Search types supported by the provider-independent catalog UI. */
enum class CatalogSearchType {
    TRACKS,
    ARTISTS,
    ALBUMS,
    PUBLIC_PLAYLISTS
}

/** Opaque continuation token scoped to exactly one provider. */
data class CatalogContinuation(
    val providerId: String,
    val token: String,
    val expiresAtEpochMs: Long? = null
) {
    init {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(token.isNotBlank()) { "Continuation token must not be blank" }
    }

    fun isExpired(nowEpochMs: Long): Boolean =
        expiresAtEpochMs != null && nowEpochMs >= expiresAtEpochMs
}

/** Empty pages may still have a continuation token. */
data class CatalogPage<T>(
    val items: List<T>,
    val nextContinuation: CatalogContinuation? = null
) {
    val hasMore: Boolean
        get() = nextContinuation != null
}

data class CatalogSearchRequest(
    val query: String,
    val pageSize: Int = DEFAULT_PAGE_SIZE,
    val continuation: CatalogContinuation? = null
) {
    init {
        require(pageSize in MIN_PAGE_SIZE..MAX_PAGE_SIZE) {
            "pageSize must be between $MIN_PAGE_SIZE and $MAX_PAGE_SIZE"
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 25
        const val MIN_PAGE_SIZE = 1
        const val MAX_PAGE_SIZE = 100
    }
}

data class MusicCatalogCapabilities(
    val searchTracks: Boolean = false,
    val searchArtists: Boolean = false,
    val searchAlbums: Boolean = false,
    /** True only when public playlists can actually be searched. */
    val searchPublicPlaylists: Boolean = false
) {
    fun supports(type: CatalogSearchType): Boolean = when (type) {
        CatalogSearchType.TRACKS -> searchTracks
        CatalogSearchType.ARTISTS -> searchArtists
        CatalogSearchType.ALBUMS -> searchAlbums
        CatalogSearchType.PUBLIC_PLAYLISTS -> searchPublicPlaylists
    }
}

enum class CatalogProviderErrorCode {
    NETWORK,
    OFFLINE,
    RATE_LIMITED,
    AUTHORIZATION_REQUIRED,
    UNSUPPORTED_OPERATION,
    INVALID_CONTINUATION,
    EXPIRED_CONTINUATION,
    MALFORMED_RESPONSE,
    PROVIDER_CHANGED,
    SOURCE_UNAVAILABLE,
    POLICY_BLOCKED,
    UNKNOWN
}

data class CatalogProviderError(
    val code: CatalogProviderErrorCode,
    val message: String,
    val retryAfterMs: Long? = null
) {
    init {
        require(message.isNotBlank()) { "Error message must not be blank" }
        require(retryAfterMs == null || retryAfterMs >= 0L) {
            "retryAfterMs cannot be negative"
        }
    }
}

/** Typed provider result. Coroutine cancellation must be rethrown, not wrapped. */
sealed interface CatalogProviderResult<out T> {
    data class Success<T>(
        val value: T
    ) : CatalogProviderResult<T>

    data class Failure(
        val error: CatalogProviderError
    ) : CatalogProviderResult<Nothing>
}

/**
 * Independently testable catalog adapter contract.
 *
 * Implementations must validate provider-specific continuation tokens,
 * must not fabricate missing metadata, and must propagate coroutine
 * cancellation. Unsupported search types should return
 * UNSUPPORTED_OPERATION.
 *
 * These methods return catalog metadata only and never imply permission
 * to play or download.
 */
interface MusicCatalogProvider {
    val providerId: String
    val displayName: String
    val capabilities: MusicCatalogCapabilities

    suspend fun searchTracks(
        request: CatalogSearchRequest
    ): CatalogProviderResult<CatalogPage<CatalogTrack>>

    suspend fun searchArtists(
        request: CatalogSearchRequest
    ): CatalogProviderResult<CatalogPage<CatalogArtist>>

    suspend fun searchAlbums(
        request: CatalogSearchRequest
    ): CatalogProviderResult<CatalogPage<CatalogAlbum>>

    suspend fun searchPublicPlaylists(
        request: CatalogSearchRequest
    ): CatalogProviderResult<CatalogPage<CatalogPlaylist>>
}