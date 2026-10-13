package com.wavymusic.app.catalog.model

/**
 * A provider's identity for an item. IDs are namespaced by provider and are
 * never treated as globally unique. versionQualifier is preserved verbatim.
 */
data class ProviderItemId(
    val providerId: String,
    val itemId: String,
    val versionQualifier: String? = null
) {
    init {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(itemId.isNotBlank()) { "itemId must not be blank" }
    }
}

/** A reusable image reference. Artwork URLs may expire and are not identities. */
data class CatalogArtwork(
    val url: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val mimeType: String? = null,
    val expiresAtEpochMs: Long? = null
) {
    init {
        require(url.isNotBlank()) { "Artwork URL must not be blank" }
        require(widthPx == null || widthPx > 0) {
            "widthPx must be positive"
        }
        require(heightPx == null || heightPx > 0) {
            "heightPx must be positive"
        }
    }
}

enum class CatalogReleaseType {
    ALBUM,
    EP,
    SINGLE,
    COMPILATION,
    SOUNDTRACK,
    OTHER,
    UNKNOWN
}

enum class CatalogSourceKind {
    TRACK,
    ARTIST,
    ALBUM,
    PLAYLIST
}

/**
 * A provider catalog link, not a media stream.
 *
 * This intentionally has no playback URI: catalog visibility does not grant
 * playback or download rights.
 */
data class CatalogSourceDescriptor(
    val id: String,
    val providerId: String,
    val providerItemId: String,
    val kind: CatalogSourceKind,
    val catalogUri: String? = null,
    val expiresAtEpochMs: Long? = null
) {
    init {
        require(id.isNotBlank()) { "Source descriptor id must not be blank" }
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(providerItemId.isNotBlank()) { "providerItemId must not be blank" }
    }
}

enum class SourceEligibilityStatus {
    NOT_ASSESSED,
    CATALOG_ONLY,
    ELIGIBLE_FOR_PLAYBACK,
    REQUIRES_AUTHORIZATION,
    EXPIRED,
    UNSUPPORTED,
    POLICY_BLOCKED,
    UNAVAILABLE
}

/**
 * An assessment is descriptive only.
 *
 * ELIGIBLE_FOR_PLAYBACK does not itself carry a playable URI. The playback
 * layer still requires a separate, validated PlaybackSource.
 */
data class SourceEligibilityReport(
    val providerId: String,
    val providerItemId: String,
    val status: SourceEligibilityStatus,
    val checkedAtEpochMs: Long? = null,
    val expiresAtEpochMs: Long? = null,
    val reason: String? = null
) {
    init {
        require(providerId.isNotBlank()) { "providerId must not be blank" }
        require(providerItemId.isNotBlank()) { "providerItemId must not be blank" }
    }
}

/** Canonical recording metadata returned by a catalog provider. */
data class CatalogTrack(
    /** Stable ID assigned by the catalog layer, not a display title. */
    val id: String,
    val title: String,
    val providerIds: Set<ProviderItemId> = emptySet(),
    val artistIds: List<String> = emptyList(),
    val artistNames: List<String> = emptyList(),
    val albumId: String? = null,
    val releaseTitle: String? = null,
    val releaseYear: Int? = null,
    val durationMs: Long? = null,
    /** Explicit provider/editor version label, preserved without normalization. */
    val versionQualifier: String? = null,
    val artwork: CatalogArtwork? = null,
    val sources: List<CatalogSourceDescriptor> = emptyList(),
    val sourceEligibility: List<SourceEligibilityReport> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Track id must not be blank" }
        require(title.isNotBlank()) { "Track title must not be blank" }
        require(durationMs == null || durationMs >= 0L) {
            "durationMs cannot be negative"
        }
    }
}

/** Canonical artist record, deduplicated only by stable identity. */
data class CatalogArtist(
    val id: String,
    val name: String,
    val providerIds: Set<ProviderItemId> = emptySet(),
    val description: String? = null,
    val artwork: CatalogArtwork? = null,
    val sources: List<CatalogSourceDescriptor> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Artist id must not be blank" }
        require(name.isNotBlank()) { "Artist name must not be blank" }
    }
}

/** Release/album metadata, kept distinct from the recording itself. */
data class CatalogAlbum(
    val id: String,
    val title: String,
    val providerIds: Set<ProviderItemId> = emptySet(),
    val artistIds: List<String> = emptyList(),
    val artistNames: List<String> = emptyList(),
    val releaseDate: String? = null,
    val releaseYear: Int? = null,
    val releaseType: CatalogReleaseType = CatalogReleaseType.UNKNOWN,
    val trackCount: Int? = null,
    val versionQualifier: String? = null,
    val artwork: CatalogArtwork? = null,
    val sources: List<CatalogSourceDescriptor> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Album id must not be blank" }
        require(title.isNotBlank()) { "Album title must not be blank" }
        require(trackCount == null || trackCount >= 0) {
            "trackCount cannot be negative"
        }
    }
}

/** Public-playlist metadata; support and visibility are explicit flags. */
data class CatalogPlaylist(
    val id: String,
    val title: String,
    val providerIds: Set<ProviderItemId> = emptySet(),
    val ownerName: String? = null,
    val description: String? = null,
    val isPublic: Boolean = false,
    val isSearchablePublicly: Boolean = false,
    val entryCount: Int? = null,
    val artwork: CatalogArtwork? = null,
    val sources: List<CatalogSourceDescriptor> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Playlist id must not be blank" }
        require(title.isNotBlank()) { "Playlist title must not be blank" }
        require(entryCount == null || entryCount >= 0) {
            "entryCount cannot be negative"
        }
        require(!isSearchablePublicly || isPublic) {
            "A playlist cannot be searchable publicly unless it is public"
        }
    }
}

/**
 * One occurrence in a playlist.
 *
 * Entries are not deduplicated by track ID: the same recording may
 * intentionally appear multiple times.
 */
data class CatalogPlaylistEntry(
    val entryId: String,
    val playlistId: String,
    /** Zero-based position as supplied or mapped by the provider. */
    val position: Int,
    val trackId: String? = null,
    val providerTrackIds: Set<ProviderItemId> = emptySet(),
    val titleSnapshot: String? = null,
    val artistNamesSnapshot: List<String> = emptyList()
) {
    init {
        require(entryId.isNotBlank()) { "Playlist entry id must not be blank" }
        require(playlistId.isNotBlank()) { "Playlist id must not be blank" }
        require(position >= 0) { "Playlist position cannot be negative" }
    }
}