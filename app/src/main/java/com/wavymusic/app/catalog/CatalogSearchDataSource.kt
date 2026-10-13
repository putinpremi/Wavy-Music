package com.wavymusic.app.catalog

import com.wavymusic.app.catalog.provider.CatalogContinuation
import com.wavymusic.app.catalog.provider.CatalogSearchType
import com.wavymusic.app.catalog.storage.CatalogRecentSearchEntity
import kotlinx.coroutines.flow.Flow

interface CatalogSearchDataSource {
    /**
     * False means there is no registered provider transport capable of
     * performing live catalog requests.
     */
    val isConfigured: Boolean

    fun search(
        query: String,
        type: CatalogSearchType,
        pageSize: Int,
        continuation: CatalogContinuation?
    ): Flow<CatalogSearchState>

    suspend fun recentSearches(
        type: CatalogSearchType?,
        limit: Int
    ): List<CatalogRecentSearchEntity>

    suspend fun clearRecentSearches()
}

/**
 * Adapts the existing repository without duplicating its cache or paging logic.
 */
class RepositoryCatalogSearchDataSource(
    private val repository: CatalogRepository,
    override val isConfigured: Boolean
) : CatalogSearchDataSource {

    override fun search(
        query: String,
        type: CatalogSearchType,
        pageSize: Int,
        continuation: CatalogContinuation?
    ): Flow<CatalogSearchState> = repository.search(
        query = query,
        type = type,
        pageSize = pageSize,
        continuation = continuation
    )

    override suspend fun recentSearches(
        type: CatalogSearchType?,
        limit: Int
    ): List<CatalogRecentSearchEntity> =
        repository.recentSearches(type = type, limit = limit)

    override suspend fun clearRecentSearches() {
        repository.clearRecentSearches()
    }
}