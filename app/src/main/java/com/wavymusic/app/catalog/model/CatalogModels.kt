package com.wavymusic.app.catalog.model

data class ProviderItemId(
    val providerId: String,
    val itemId: String,
    val versionQualifier: String? = null
) {
    init {
        require(providerId.isNotBlank())
        require(itemId.isNotBlank())
    }
}

data class CatalogArtwork(
    val url: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val mimeType: String? = null,
    val expiresAtEpochMs: Long? = null
) {
    init {
        require(url.isNotBlank())
        require(widthPx == null || widthPx > 0)
        require(heightPx == null || heightPx > 0)
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

/** A catalog reference does not imply a playable media stream. */
data class CatalogSourceDescriptor(
    val id: String,
    val providerId: String,
    val providerItemId: String,
    val kind: CatalogSourceKind,
    val catalogUri: String? = null,
    val expiresAtEpochMs: Long? = null
) {
    init {
        require(id.isNotBlank())
        require(providerId.isNotBlank())
        require(providerItemId.isNotBlank())
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

data class SourceEligibilityReport(
    val providerId: String,
    val providerItemId: String,
    val status: SourceEligibilityStatus,
    val checkedAtEpochMs: Long? = null,
    val expiresAtEpochMs: Long? = null,
    val reason: String? = null
) {
    init {
        require(providerId.isNotBlank())
        require(providerItemId.isNotBlank())
    }
}

data class CatalogTrack(
    val id: String,
    val title: String,
    val providerIds: Set<ProviderItemId> = emptySet(),
    val artistIds: List<String> = emptyList(),
    val artistNames: List<String> = emptyList(),
    val albumId: String? = null,
    val releaseTitle: String? = null,
    val releaseYear: Int? = null,
    val durationMs: Long? = null,
    val versionQualifier: String? = null,
    val artwork: CatalogArtwork? = null,
    val sources: List<CatalogSourceDescriptor> = emptyList(),
    val sourceEligibility: List<SourceEligibilityReport> = emptyList()
) {
    init {
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(durationMs == null || durationMs >= 0L)
    }
}

data class CatalogArtist(
    val id: String,
    val name: String,
    val providerIds: Set<ProviderItemId> = emptySet(),
    val description: String? = null,
    val artwork: CatalogArtwork? = null,
    val sources: List<CatalogSourceDescriptor> = emptyList()
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
    }
}

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
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(trackCount == null || trackCount >= 0)
    }
}

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
        require(id.isNotBlank())
        require(title.isNotBlank())
        require(entryCount == null || entryCount >= 0)
        require(!isSearchablePublicly || isPublic) {
            "A playlist must be public to be searchable publicly"
        }
    }
}

/** Playlist occurrences remain distinct, even for repeated recordings. */
data class CatalogPlaylistEntry(
    val entryId: String,
    val playlistId: String,
    val position: Int,
    val trackId: String? = null,
    val providerTrackIds: Set<ProviderItemId> = emptySet(),
    val titleSnapshot: String? = null,
    val artistNamesSnapshot: List<String> = emptyList()
) {
    init {
        require(entryId.isNotBlank())
        require(playlistId.isNotBlank())
        require(position >= 0)
    }
}