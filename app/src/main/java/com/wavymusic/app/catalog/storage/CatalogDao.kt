package com.wavymusic.app.catalog.storage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class CatalogDao {

    @Upsert
    abstract suspend fun upsertTrack(entity: CatalogTrackEntity)

    @Upsert
    abstract suspend fun upsertTracks(entities: List<CatalogTrackEntity>)

    @Query("SELECT * FROM catalog_tracks WHERE id = :id LIMIT 1")
    abstract suspend fun getTrack(id: String): CatalogTrackEntity?

    @Upsert
    abstract suspend fun upsertArtist(entity: CatalogArtistEntity)

    @Upsert
    abstract suspend fun upsertArtists(entities: List<CatalogArtistEntity>)

    @Query("SELECT * FROM catalog_artists WHERE id = :id LIMIT 1")
    abstract suspend fun getArtist(id: String): CatalogArtistEntity?

    @Upsert
    abstract suspend fun upsertAlbum(entity: CatalogAlbumEntity)

    @Upsert
    abstract suspend fun upsertAlbums(entities: List<CatalogAlbumEntity>)

    @Query("SELECT * FROM catalog_albums WHERE id = :id LIMIT 1")
    abstract suspend fun getAlbum(id: String): CatalogAlbumEntity?

    @Upsert
    abstract suspend fun upsertPlaylist(entity: CatalogPlaylistEntity)

    @Upsert
    abstract suspend fun upsertPlaylists(entities: List<CatalogPlaylistEntity>)

    @Query("SELECT * FROM catalog_playlists WHERE id = :id LIMIT 1")
    abstract suspend fun getPlaylist(id: String): CatalogPlaylistEntity?

    @Upsert
    abstract suspend fun upsertPlaylistEntry(entity: CatalogPlaylistEntryEntity)

    @Upsert
    abstract suspend fun upsertPlaylistEntries(
        entities: List<CatalogPlaylistEntryEntity>
    )

    @Query(
        """
        SELECT * FROM catalog_playlist_entries
        WHERE playlistId = :playlistId
        ORDER BY position ASC
        """
    )
    abstract suspend fun getPlaylistEntries(
        playlistId: String
    ): List<CatalogPlaylistEntryEntity>

    @Upsert
    abstract suspend fun upsertTrackArtists(
        entities: List<CatalogTrackArtistEntity>
    )

    @Query(
        """
        SELECT * FROM catalog_track_artists
        WHERE trackId = :trackId
        ORDER BY position ASC
        """
    )
    abstract suspend fun getTrackArtists(
        trackId: String
    ): List<CatalogTrackArtistEntity>

    @Query("DELETE FROM catalog_track_artists WHERE trackId = :trackId")
    abstract suspend fun deleteTrackArtists(trackId: String)

    @Upsert
    abstract suspend fun upsertAlbumArtists(
        entities: List<CatalogAlbumArtistEntity>
    )

    @Query(
        """
        SELECT * FROM catalog_album_artists
        WHERE albumId = :albumId
        ORDER BY position ASC
        """
    )
    abstract suspend fun getAlbumArtists(
        albumId: String
    ): List<CatalogAlbumArtistEntity>

    @Query("DELETE FROM catalog_album_artists WHERE albumId = :albumId")
    abstract suspend fun deleteAlbumArtists(albumId: String)

    @Upsert
    abstract suspend fun upsertPlaylistEntryArtists(
        entities: List<CatalogPlaylistEntryArtistEntity>
    )

    @Query(
        """
        SELECT * FROM catalog_playlist_entry_artists
        WHERE entryId = :entryId
        ORDER BY position ASC
        """
    )
    abstract suspend fun getPlaylistEntryArtists(
        entryId: String
    ): List<CatalogPlaylistEntryArtistEntity>

    @Query(
        "DELETE FROM catalog_playlist_entry_artists WHERE entryId = :entryId"
    )
    abstract suspend fun deletePlaylistEntryArtists(entryId: String)

    @Upsert
    abstract suspend fun upsertProviderIdentities(
        entities: List<CatalogProviderIdentityEntity>
    )

    @Query(
        """
        SELECT * FROM catalog_provider_identities
        WHERE ownerType = :ownerType AND ownerId = :ownerId
        """
    )
    abstract suspend fun getProviderIdentities(
        ownerType: String,
        ownerId: String
    ): List<CatalogProviderIdentityEntity>

    @Query(
        """
        DELETE FROM catalog_provider_identities
        WHERE ownerType = :ownerType AND ownerId = :ownerId
        """
    )
    abstract suspend fun deleteProviderIdentities(
        ownerType: String,
        ownerId: String
    )

    @Upsert
    abstract suspend fun upsertSourceDescriptors(
        entities: List<CatalogSourceDescriptorEntity>
    )

    @Query(
        """
        SELECT * FROM catalog_source_descriptors
        WHERE ownerType = :ownerType AND ownerId = :ownerId
        """
    )
    abstract suspend fun getSourceDescriptors(
        ownerType: String,
        ownerId: String
    ): List<CatalogSourceDescriptorEntity>

    @Query(
        """
        DELETE FROM catalog_source_descriptors
        WHERE ownerType = :ownerType AND ownerId = :ownerId
        """
    )
    abstract suspend fun deleteSourceDescriptors(
        ownerType: String,
        ownerId: String
    )

    @Upsert
    abstract suspend fun upsertSourceEligibility(
        entities: List<CatalogSourceEligibilityEntity>
    )

    @Query(
        """
        SELECT * FROM catalog_source_eligibility
        WHERE ownerType = :ownerType AND ownerId = :ownerId
        """
    )
    abstract suspend fun getSourceEligibility(
        ownerType: String,
        ownerId: String
    ): List<CatalogSourceEligibilityEntity>

    @Query(
        """
        DELETE FROM catalog_source_eligibility
        WHERE ownerType = :ownerType AND ownerId = :ownerId
        """
    )
    abstract suspend fun deleteSourceEligibility(
        ownerType: String,
        ownerId: String
    )

    @Upsert
    abstract suspend fun upsertSearchCache(
        entity: CatalogSearchCacheEntity
    )

    @Upsert
    abstract suspend fun upsertSearchCacheResults(
        entities: List<CatalogSearchCacheResultEntity>
    )

    @Query(
        "SELECT * FROM catalog_search_cache WHERE cacheKey = :cacheKey LIMIT 1"
    )
    abstract suspend fun getSearchCache(
        cacheKey: String
    ): CatalogSearchCacheEntity?

    @Query(
        """
        SELECT * FROM catalog_search_cache_results
        WHERE cacheKey = :cacheKey
        ORDER BY position ASC
        """
    )
    abstract suspend fun getSearchCacheResults(
        cacheKey: String
    ): List<CatalogSearchCacheResultEntity>

    @Query(
        "DELETE FROM catalog_search_cache_results WHERE cacheKey = :cacheKey"
    )
    abstract suspend fun deleteSearchCacheResults(cacheKey: String)

    @Query(
        """
        DELETE FROM catalog_search_cache
        WHERE expiresAtEpochMs IS NOT NULL
          AND expiresAtEpochMs <= :nowEpochMs
        """
    )
    abstract suspend fun deleteExpiredSearchCaches(
        nowEpochMs: Long
    ): Int

    @Transaction
    open suspend fun replaceSearchCache(
        cache: CatalogSearchCacheEntity,
        results: List<CatalogSearchCacheResultEntity>
    ) {
        require(results.all { it.cacheKey == cache.cacheKey }) {
            "Every cached result must use the cache entry's key"
        }

        deleteSearchCacheResults(cache.cacheKey)
        upsertSearchCache(cache)

        if (results.isNotEmpty()) {
            upsertSearchCacheResults(results)
        }
    }

    @Upsert
    abstract suspend fun upsertRecentSearch(
        entity: CatalogRecentSearchEntity
    )

    @Query(
        """
        SELECT * FROM catalog_recent_searches
        WHERE normalizedQuery = :normalizedQuery
          AND searchType = :searchType
        LIMIT 1
        """
    )
    abstract suspend fun getRecentSearch(
        normalizedQuery: String,
        searchType: String
    ): CatalogRecentSearchEntity?

    @Query(
        """
        SELECT * FROM catalog_recent_searches
        WHERE (:searchType IS NULL OR searchType = :searchType)
        ORDER BY lastSearchedAtEpochMs DESC
        LIMIT :limit
        """
    )
    abstract suspend fun getRecentSearches(
        searchType: String?,
        limit: Int
    ): List<CatalogRecentSearchEntity>

    @Transaction
    open suspend fun recordRecentSearch(
        normalizedQuery: String,
        queryText: String,
        searchType: String,
        lastSearchedAtEpochMs: Long
    ) {
        require(normalizedQuery.isNotBlank()) {
            "normalizedQuery must not be blank"
        }
        require(queryText.isNotBlank()) { "queryText must not be blank" }
        require(searchType.isNotBlank()) { "searchType must not be blank" }

        val existing = getRecentSearch(normalizedQuery, searchType)

        upsertRecentSearch(
            CatalogRecentSearchEntity(
                normalizedQuery = normalizedQuery,
                searchType = searchType,
                queryText = queryText,
                lastSearchedAtEpochMs = lastSearchedAtEpochMs,
                useCount = (existing?.useCount ?: 0) + 1
            )
        )
    }

    @Query(
        """
        DELETE FROM catalog_recent_searches
        WHERE normalizedQuery = :normalizedQuery
          AND searchType = :searchType
        """
    )
    abstract suspend fun deleteRecentSearch(
        normalizedQuery: String,
        searchType: String
    )

    @Query("DELETE FROM catalog_recent_searches")
    abstract suspend fun clearRecentSearches()
}