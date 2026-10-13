package com.wavymusic.app.catalog

import android.content.Context
import com.wavymusic.app.catalog.provider.MusicCatalogProvider
import com.wavymusic.app.catalog.storage.WavyCatalogDatabase

/**
 * Application-level access point for catalog search.
 *
 * Add a reviewed, permitted MusicCatalogProvider to registeredProviders
 * only when a real transport implementation is available.
 */
object CatalogGraph {

    @Volatile
    private var cachedDataSource: CatalogSearchDataSource? = null

    fun searchDataSource(context: Context): CatalogSearchDataSource {
        return cachedDataSource ?: synchronized(this) {
            cachedDataSource ?: createDataSource(context).also {
                cachedDataSource = it
            }
        }
    }

    private fun createDataSource(context: Context): CatalogSearchDataSource {
        // The repository currently has an adapter and parser, but no
        // concrete, permitted network transport is registered.
        val registeredProviders: List<MusicCatalogProvider> = emptyList()

        val repository = CatalogRepository(
            providers = registeredProviders,
            dao = WavyCatalogDatabase
                .getInstance(context.applicationContext)
                .catalogDao()
        )

        return RepositoryCatalogSearchDataSource(
            repository = repository,
            isConfigured = registeredProviders.isNotEmpty()
        )
    }
}