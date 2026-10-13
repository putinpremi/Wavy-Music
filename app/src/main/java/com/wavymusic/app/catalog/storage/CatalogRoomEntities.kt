package com.wavymusic.app.catalog.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wavymusic.app.catalog.model.CatalogAlbum
import com.wavymusic.app.catalog.model.CatalogArtist
import com.wavymusic.app.catalog.model.CatalogArtwork
import com.wavymusic.app.catalog.model.CatalogPlaylist
import com.wavymusic.app.catalog.model.CatalogPlaylistEntry
import com.wavymusic.app.catalog.model.CatalogReleaseType
import com.wavymusic.app.catalog.model.CatalogSourceDescriptor
import com.wavymusic.app.catalog.model.CatalogSourceKind
import com.wavymusic.app.catalog.model.CatalogTrack
import com.wavymusic.app.catalog.model.ProviderItemId
import com.wavymusic.app.catalog.model.SourceEligibilityReport
import com.wavymusic.app.catalog.model.SourceEligibilityStatus

internal object CatalogOwnerType {
    const val TRACK = "TRACK"
    const val ARTIST = "ARTIST"
    const val ALBUM = "ALBUM"
    const val PLAYLIST = "PLAYLIST"
    const val PLAYLIST_ENTRY = "PLAYLIST_ENTRY"
}

@Entity(
    tableName = "catalog_tracks",
    indices = [
        Index(value = ["title"]),
        Index(value = ["albumId"])
    ]
)
data class CatalogTrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val albumId: String? = null,
    val releaseTitle: String? = null,
    val releaseYear: Int? = null,
    val durationMs: Long? = null,
    val versionQualifier: String? = null,
    val artworkUrl: String? = null,
    val artworkWidthPx: Int? = null,
    val artworkHeightPx: Int? = null,
    val artworkMimeType: String? = null,
    val artworkExpiresAtEpochMs: Long? = null
)

@Entity(
    tableName = "catalog_artists",
    indices = [Index(value = ["name"])]
)
data class CatalogArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val artworkWidthPx: Int? = null,
    val artworkHeightPx: Int? = null,
    val artworkMimeType: String? = null,
    val artworkExpiresAtEpochMs: Long? = null
)

@Entity(
    tableName = "catalog_albums",
    indices = [
        Index(value = ["title"]),
        Index(value = ["releaseYear"])
    ]
)
data class CatalogAlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val releaseDate: String? = null,
    val releaseYear: Int? = null,
    val releaseType: String = CatalogReleaseType.UNKNOWN.name,
    val trackCount: Int? = null,
    val versionQualifier: String? = null,
    val artworkUrl: String? = null,
    val artworkWidthPx: Int? = null,
    val artworkHeightPx: Int? = null,
    val artworkMimeType: String? = null,
    val artworkExpiresAtEpochMs: Long? = null
)

@Entity(
    tableName = "catalog_playlists",
    indices = [Index(value = ["title"])]
)
data class CatalogPlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val ownerName: String? = null,
    val description: String? = null,
    val isPublic: Boolean = false,
    val isSearchablePublicly: Boolean = false,
    val entryCount: Int? = null,
    val artworkUrl: String? = null,
    val artworkWidthPx: Int? = null,
    val artworkHeightPx: Int? = null,
    val artworkMimeType: String? = null,
    val artworkExpiresAtEpochMs: Long? = null
)

@Entity(
    tableName = "catalog_playlist_entries",
    indices = [
        Index(value = ["playlistId", "position"], unique = true),
        Index(value = ["trackId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = CatalogPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CatalogTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class CatalogPlaylistEntryEntity(
    @PrimaryKey val entryId: String,
    val playlistId: String,
    val position: Int,
    val trackId: String? = null,
    val titleSnapshot: String? = null
)

@Entity(
    tableName = "catalog_track_artists",
    primaryKeys = ["trackId", "position"],
    indices = [Index(value = ["artistId"])],
    foreignKeys = [
        ForeignKey(
            entity = CatalogTrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CatalogTrackArtistEntity(
    val trackId: String,
    val position: Int,
    val artistId: String? = null,
    val nameSnapshot: String = ""
)

@Entity(
    tableName = "catalog_album_artists",
    primaryKeys = ["albumId", "position"],
    indices = [Index(value = ["artistId"])],
    foreignKeys = [
        ForeignKey(
            entity = CatalogAlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CatalogAlbumArtistEntity(
    val albumId: String,
    val position: Int,
    val artistId: String? = null,
    val nameSnapshot: String = ""
)

@Entity(
    tableName = "catalog_playlist_entry_artists",
    primaryKeys = ["entryId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogPlaylistEntryEntity::class,
            parentColumns = ["entryId"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CatalogPlaylistEntryArtistEntity(
    val entryId: String,
    val position: Int,
    val artistName: String
)

/** Provider IDs are namespaced; nullable version qualifiers use "" in storage. */
@Entity(
    tableName = "catalog_provider_identities",
    primaryKeys = [
        "ownerType",
        "ownerId",
        "providerId",
        "providerItemId",
        "versionQualifier"
    ],
    indices = [
        Index(value = ["providerId", "providerItemId"])
    ]
)
data class CatalogProviderIdentityEntity(
    val ownerType: String,
    val ownerId: String,
    val providerId: String,
    val providerItemId: String,
    val versionQualifier: String = ""
)

@Entity(
    tableName = "catalog_source_descriptors",
    primaryKeys = ["ownerType", "ownerId", "id"],
    indices = [
        Index(value = ["ownerType", "ownerId"]),
        Index(value = ["providerId", "providerItemId"])
    ]
)
data class CatalogSourceDescriptorEntity(
    val ownerType: String,
    val ownerId: String,
    val id: String,
    val providerId: String,
    val providerItemId: String,
    val kind: String,
    val catalogUri: String? = null,
    val expiresAtEpochMs: Long? = null
)

@Entity(
    tableName = "catalog_source_eligibility",
    primaryKeys = [
        "ownerType",
        "ownerId",
        "providerId",
        "providerItemId"
    ],
    indices = [Index(value = ["status"])]
)
data class CatalogSourceEligibilityEntity(
    val ownerType: String,
    val ownerId: String,
    val providerId: String,
    val providerItemId: String,
    val status: String,
    val checkedAtEpochMs: Long? = null,
    val expiresAtEpochMs: Long? = null,
    val reason: String? = null
)

@Entity(
    tableName = "catalog_search_cache",
    indices = [
        Index(value = ["normalizedQuery", "searchType", "providerId"]),
        Index(value = ["expiresAtEpochMs"])
    ]
)
data class CatalogSearchCacheEntity(
    @PrimaryKey val cacheKey: String,
    val queryText: String,
    val normalizedQuery: String,
    val searchType: String,
    val providerId: String,
    val cachedAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val continuationProviderId: String? = null,
    val continuationToken: String? = null,
    val continuationExpiresAtEpochMs: Long? = null,
    val providerErrorCode: String? = null,
    val providerErrorMessage: String? = null,
    val retryAfterMs: Long? = null
)

@Entity(
    tableName = "catalog_search_cache_results",
    primaryKeys = ["cacheKey", "position"],
    indices = [Index(value = ["catalogItemId"])],
    foreignKeys = [
        ForeignKey(
            entity = CatalogSearchCacheEntity::class,
            parentColumns = ["cacheKey"],
            childColumns = ["cacheKey"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CatalogSearchCacheResultEntity(
    val cacheKey: String,
    val position: Int,
    val itemType: String,
    val catalogItemId: String,
    val titleSnapshot: String,
    val subtitleSnapshot: String? = null,
    val artworkUrlSnapshot: String? = null
)

@Entity(
    tableName = "catalog_recent_searches",
    primaryKeys = ["normalizedQuery", "searchType"],
    indices = [Index(value = ["lastSearchedAtEpochMs"])]
)
data class CatalogRecentSearchEntity(
    val normalizedQuery: String,
    val searchType: String,
    val queryText: String,
    val lastSearchedAtEpochMs: Long,
    val useCount: Int = 1
)

private fun artworkOrNull(
    url: String?,
    widthPx: Int?,
    heightPx: Int?,
    mimeType: String?,
    expiresAtEpochMs: Long?
): CatalogArtwork? {
    val validUrl = url?.takeIf { it.isNotBlank() } ?: return null
    return CatalogArtwork(
        url = validUrl,
        widthPx = widthPx,
        heightPx = heightPx,
        mimeType = mimeType,
        expiresAtEpochMs = expiresAtEpochMs
    )
}

fun CatalogTrack.toEntity() = CatalogTrackEntity(
    id = id,
    title = title,
    albumId = albumId,
    releaseTitle = releaseTitle,
    releaseYear = releaseYear,
    durationMs = durationMs,
    versionQualifier = versionQualifier,
    artworkUrl = artwork?.url,
    artworkWidthPx = artwork?.widthPx,
    artworkHeightPx = artwork?.heightPx,
    artworkMimeType = artwork?.mimeType,
    artworkExpiresAtEpochMs = artwork?.expiresAtEpochMs
)

fun CatalogTrackEntity.toDomain(
    providerIds: Collection<ProviderItemId> = emptyList(),
    artistLinks: List<CatalogTrackArtistEntity> = emptyList(),
    sources: List<CatalogSourceDescriptor> = emptyList(),
    sourceEligibility: List<SourceEligibilityReport> = emptyList()
) = CatalogTrack(
    id = id,
    title = title,
    providerIds = providerIds.toSet(),
    artistIds = artistLinks.mapNotNull { it.artistId },
    artistNames = artistLinks
        .sortedBy { it.position }
        .map { it.nameSnapshot }
        .filter { it.isNotBlank() },
    albumId = albumId,
    releaseTitle = releaseTitle,
    releaseYear = releaseYear,
    durationMs = durationMs,
    versionQualifier = versionQualifier,
    artwork = artworkOrNull(
        artworkUrl,
        artworkWidthPx,
        artworkHeightPx,
        artworkMimeType,
        artworkExpiresAtEpochMs
    ),
    sources = sources,
    sourceEligibility = sourceEligibility
)

fun CatalogTrack.toArtistLinks(): List<CatalogTrackArtistEntity> {
    val count = maxOf(artistIds.size, artistNames.size)
    return (0 until count).map { position ->
        CatalogTrackArtistEntity(
            trackId = id,
            position = position,
            artistId = artistIds.getOrNull(position),
            nameSnapshot = artistNames.getOrNull(position).orEmpty()
        )
    }
}

fun CatalogTrack.toProviderIdentityEntities() =
    providerIds.map { it.toEntity(CatalogOwnerType.TRACK, id) }

fun CatalogTrack.toSourceEntities() =
    sources.map { it.toEntity(CatalogOwnerType.TRACK, id) }

fun CatalogTrack.toEligibilityEntities() =
    sourceEligibility.map { it.toEntity(CatalogOwnerType.TRACK, id) }

fun CatalogArtist.toEntity() = CatalogArtistEntity(
    id = id,
    name = name,
    description = description,
    artworkUrl = artwork?.url,
    artworkWidthPx = artwork?.widthPx,
    artworkHeightPx = artwork?.heightPx,
    artworkMimeType = artwork?.mimeType,
    artworkExpiresAtEpochMs = artwork?.expiresAtEpochMs
)

fun CatalogArtistEntity.toDomain(
    providerIds: Collection<ProviderItemId> = emptyList(),
    sources: List<CatalogSourceDescriptor> = emptyList()
) = CatalogArtist(
    id = id,
    name = name,
    providerIds = providerIds.toSet(),
    description = description,
    artwork = artworkOrNull(
        artworkUrl,
        artworkWidthPx,
        artworkHeightPx,
        artworkMimeType,
        artworkExpiresAtEpochMs
    ),
    sources = sources
)

fun CatalogArtist.toProviderIdentityEntities() =
    providerIds.map { it.toEntity(CatalogOwnerType.ARTIST, id) }

fun CatalogArtist.toSourceEntities() =
    sources.map { it.toEntity(CatalogOwnerType.ARTIST, id) }

fun CatalogAlbum.toEntity() = CatalogAlbumEntity(
    id = id,
    title = title,
    releaseDate = releaseDate,
    releaseYear = releaseYear,
    releaseType = releaseType.name,
    trackCount = trackCount,
    versionQualifier = versionQualifier,
    artworkUrl = artwork?.url,
    artworkWidthPx = artwork?.widthPx,
    artworkHeightPx = artwork?.heightPx,
    artworkMimeType = artwork?.mimeType,
    artworkExpiresAtEpochMs = artwork?.expiresAtEpochMs
)

fun CatalogAlbumEntity.toDomain(
    providerIds: Collection<ProviderItemId> = emptyList(),
    artistLinks: List<CatalogAlbumArtistEntity> = emptyList(),
    sources: List<CatalogSourceDescriptor> = emptyList()
) = CatalogAlbum(
    id = id,
    title = title,
    providerIds = providerIds.toSet(),
    artistIds = artistLinks.mapNotNull { it.artistId },
    artistNames = artistLinks
        .sortedBy { it.position }
        .map { it.nameSnapshot }
        .filter { it.isNotBlank() },
    releaseDate = releaseDate,
    releaseYear = releaseYear,
    releaseType = runCatching {
        CatalogReleaseType.valueOf(releaseType)
    }.getOrDefault(CatalogReleaseType.UNKNOWN),
    trackCount = trackCount,
    versionQualifier = versionQualifier,
    artwork = artworkOrNull(
        artworkUrl,
        artworkWidthPx,
        artworkHeightPx,
        artworkMimeType,
        artworkExpiresAtEpochMs
    ),
    sources = sources
)

fun CatalogAlbum.toArtistLinks(): List<CatalogAlbumArtistEntity> {
    val count = maxOf(artistIds.size, artistNames.size)
    return (0 until count).map { position ->
        CatalogAlbumArtistEntity(
            albumId = id,
            position = position,
            artistId = artistIds.getOrNull(position),
            nameSnapshot = artistNames.getOrNull(position).orEmpty()
        )
    }
}

fun CatalogAlbum.toProviderIdentityEntities() =
    providerIds.map { it.toEntity(CatalogOwnerType.ALBUM, id) }

fun CatalogAlbum.toSourceEntities() =
    sources.map { it.toEntity(CatalogOwnerType.ALBUM, id) }

fun CatalogPlaylist.toEntity() = CatalogPlaylistEntity(
    id = id,
    title = title,
    ownerName = ownerName,
    description = description,
    isPublic = isPublic,
    isSearchablePublicly = isSearchablePublicly,
    entryCount = entryCount,
    artworkUrl = artwork?.url,
    artworkWidthPx = artwork?.widthPx,
    artworkHeightPx = artwork?.heightPx,
    artworkMimeType = artwork?.mimeType,
    artworkExpiresAtEpochMs = artwork?.expiresAtEpochMs
)

fun CatalogPlaylistEntity.toDomain(
    providerIds: Collection<ProviderItemId> = emptyList(),
    sources: List<CatalogSourceDescriptor> = emptyList()
) = CatalogPlaylist(
    id = id,
    title = title,
    providerIds = providerIds.toSet(),
    ownerName = ownerName,
    description = description,
    isPublic = isPublic,
    isSearchablePublicly = isSearchablePublicly,
    entryCount = entryCount,
    artwork = artworkOrNull(
        artworkUrl,
        artworkWidthPx,
        artworkHeightPx,
        artworkMimeType,
        artworkExpiresAtEpochMs
    ),
    sources = sources
)

fun CatalogPlaylist.toProviderIdentityEntities() =
    providerIds.map { it.toEntity(CatalogOwnerType.PLAYLIST, id) }

fun CatalogPlaylist.toSourceEntities() =
    sources.map { it.toEntity(CatalogOwnerType.PLAYLIST, id) }

fun CatalogPlaylistEntry.toEntity() = CatalogPlaylistEntryEntity(
    entryId = entryId,
    playlistId = playlistId,
    position = position,
    trackId = trackId,
    titleSnapshot = titleSnapshot
)

fun CatalogPlaylistEntry.toArtistSnapshotEntities() =
    artistNamesSnapshot.mapIndexed { position, name ->
        CatalogPlaylistEntryArtistEntity(
            entryId = entryId,
            position = position,
            artistName = name
        )
    }

fun CatalogPlaylistEntry.toProviderIdentityEntities() =
    providerTrackIds.map {
        it.toEntity(CatalogOwnerType.PLAYLIST_ENTRY, entryId)
    }

fun CatalogPlaylistEntryEntity.toDomain(
    providerTrackIds: Collection<ProviderItemId> = emptyList(),
    artistLinks: List<CatalogPlaylistEntryArtistEntity> = emptyList()
) = CatalogPlaylistEntry(
    entryId = entryId,
    playlistId = playlistId,
    position = position,
    trackId = trackId,
    providerTrackIds = providerTrackIds.toSet(),
    titleSnapshot = titleSnapshot,
    artistNamesSnapshot = artistLinks
        .sortedBy { it.position }
        .map { it.artistName }
)

private fun ProviderItemId.toEntity(
    ownerType: String,
    ownerId: String
) = CatalogProviderIdentityEntity(
    ownerType = ownerType,
    ownerId = ownerId,
    providerId = providerId,
    providerItemId = itemId,
    versionQualifier = versionQualifier.orEmpty()
)

fun CatalogProviderIdentityEntity.toDomain() = ProviderItemId(
    providerId = providerId,
    itemId = providerItemId,
    versionQualifier = versionQualifier.takeIf { it.isNotEmpty() }
)

private fun CatalogSourceDescriptor.toEntity(
    ownerType: String,
    ownerId: String
) = CatalogSourceDescriptorEntity(
    ownerType = ownerType,
    ownerId = ownerId,
    id = id,
    providerId = providerId,
    providerItemId = providerItemId,
    kind = kind.name,
    catalogUri = catalogUri,
    expiresAtEpochMs = expiresAtEpochMs
)

fun CatalogSourceDescriptorEntity.toDomain() = CatalogSourceDescriptor(
    id = id,
    providerId = providerId,
    providerItemId = providerItemId,
    kind = runCatching {
        CatalogSourceKind.valueOf(kind)
    }.getOrDefault(CatalogSourceKind.TRACK),
    catalogUri = catalogUri,
    expiresAtEpochMs = expiresAtEpochMs
)

private fun SourceEligibilityReport.toEntity(
    ownerType: String,
    ownerId: String
) = CatalogSourceEligibilityEntity(
    ownerType = ownerType,
    ownerId = ownerId,
    providerId = providerId,
    providerItemId = providerItemId,
    status = status.name,
    checkedAtEpochMs = checkedAtEpochMs,
    expiresAtEpochMs = expiresAtEpochMs,
    reason = reason
)

fun CatalogSourceEligibilityEntity.toDomain() = SourceEligibilityReport(
    providerId = providerId,
    providerItemId = providerItemId,
    status = runCatching {
        SourceEligibilityStatus.valueOf(status)
    }.getOrDefault(SourceEligibilityStatus.UNAVAILABLE),
    checkedAtEpochMs = checkedAtEpochMs,
    expiresAtEpochMs = expiresAtEpochMs,
    reason = reason
)