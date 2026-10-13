package com.wavymusic.app.catalog.storage

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogRoomDatabaseTest {

    private lateinit var database: WavyCatalogDatabase
    private lateinit var dao: CatalogDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            WavyCatalogDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        dao = database.catalogDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun trackMetadataCanBeStoredAndReadBack() = runBlocking {
        val track = CatalogTrackEntity(
            id = "provider-a:track-123",
            title = "Example Song",
            releaseTitle = "Example Album",
            releaseYear = 2025,
            durationMs = 184_000L,
            versionQualifier = "Album Version"
        )

        dao.upsertTrack(track)

        assertEquals(track, dao.getTrack(track.id))
    }

    @Test
    fun cachedSearchResultsKeepTheirOrder() = runBlocking {
        val cache = CatalogSearchCacheEntity(
            cacheKey = "provider-a:tracks:example",
            queryText = "Example",
            normalizedQuery = "example",
            searchType = "TRACKS",
            providerId = "provider-a",
            cachedAtEpochMs = 1_000L,
            expiresAtEpochMs = 60_000L
        )

        val results = listOf(
            CatalogSearchCacheResultEntity(
                cacheKey = cache.cacheKey,
                position = 0,
                itemType = "TRACK",
                catalogItemId = "track-a",
                titleSnapshot = "First Song"
            ),
            CatalogSearchCacheResultEntity(
                cacheKey = cache.cacheKey,
                position = 1,
                itemType = "TRACK",
                catalogItemId = "track-b",
                titleSnapshot = "Second Song"
            )
        )

        dao.replaceSearchCache(cache, results)

        assertNotNull(dao.getSearchCache(cache.cacheKey))
        assertEquals(
            listOf("First Song", "Second Song"),
            dao.getSearchCacheResults(cache.cacheKey).map {
                it.titleSnapshot
            }
        )
    }

    @Test
    fun replacingSearchCacheReplacesOldPageResults() = runBlocking {
        val cacheKey = "provider-a:tracks:replace"

        val firstCache = CatalogSearchCacheEntity(
            cacheKey = cacheKey,
            queryText = "Replace",
            normalizedQuery = "replace",
            searchType = "TRACKS",
            providerId = "provider-a",
            cachedAtEpochMs = 1_000L
        )

        dao.replaceSearchCache(
            firstCache,
            listOf(
                CatalogSearchCacheResultEntity(
                    cacheKey = cacheKey,
                    position = 0,
                    itemType = "TRACK",
                    catalogItemId = "old-track",
                    titleSnapshot = "Old Result"
                )
            )
        )

        val refreshedCache = firstCache.copy(cachedAtEpochMs = 2_000L)

        dao.replaceSearchCache(
            refreshedCache,
            listOf(
                CatalogSearchCacheResultEntity(
                    cacheKey = cacheKey,
                    position = 0,
                    itemType = "TRACK",
                    catalogItemId = "new-track",
                    titleSnapshot = "New Result"
                )
            )
        )

        assertEquals(
            listOf("New Result"),
            dao.getSearchCacheResults(cacheKey).map { it.titleSnapshot }
        )
        assertEquals(
            2_000L,
            dao.getSearchCache(cacheKey)?.cachedAtEpochMs
        )
    }

    @Test
    fun expiredCacheDeletionAlsoDeletesItsResults() = runBlocking {
        val cache = CatalogSearchCacheEntity(
            cacheKey = "expired-cache",
            queryText = "Old query",
            normalizedQuery = "old query",
            searchType = "TRACKS",
            providerId = "provider-a",
            cachedAtEpochMs = 10L,
            expiresAtEpochMs = 100L
        )

        dao.replaceSearchCache(
            cache,
            listOf(
                CatalogSearchCacheResultEntity(
                    cacheKey = cache.cacheKey,
                    position = 0,
                    itemType = "TRACK",
                    catalogItemId = "track-old",
                    titleSnapshot = "Expired Result"
                )
            )
        )

        assertEquals(1, dao.deleteExpiredSearchCaches(nowEpochMs = 100L))
        assertNull(dao.getSearchCache(cache.cacheKey))
        assertEquals(emptyList<CatalogSearchCacheResultEntity>(),
            dao.getSearchCacheResults(cache.cacheKey))
    }

    @Test
    fun repeatedRecentSearchUpdatesTimestampAndUsageCount() = runBlocking {
        dao.recordRecentSearch(
            normalizedQuery = "example",
            queryText = "Example",
            searchType = "TRACKS",
            lastSearchedAtEpochMs = 1_000L
        )

        dao.recordRecentSearch(
            normalizedQuery = "example",
            queryText = "Example",
            searchType = "TRACKS",
            lastSearchedAtEpochMs = 2_000L
        )

        val recent = dao.getRecentSearch("example", "TRACKS")

        assertNotNull(recent)
        assertEquals(2, recent?.useCount)
        assertEquals(2_000L, recent?.lastSearchedAtEpochMs)
    }
}