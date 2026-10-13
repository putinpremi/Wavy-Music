
package com.wavymusic.app.catalog

import android.content.Context
import com.wavymusic.app.catalog.provider.MusicCatalogProvider
import com.wavymusic.app.catalog.provider.deezer.DeezerCatalogProvider
import com.wavymusic.app.catalog.provider.deezer.HttpDeezerCatalogTransport
import com.wavymusic.app.catalog.provider.itunes.HttpITunesCatalogTransport
import com.wavymusic.app.catalog.provider.itunes.ITunesCatalogProvider
import com.wavymusic.app.catalog.storage.WavyCatalogDatabase

/**
 * Application-level access point for catalog search.
 *
 * Both providers return catalog metadata. Search results do not
 * automatically become playable sources.
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

    private fun createDataSource(
        context: Context
    ): CatalogSearchDataSource {
        val registeredProviders: List<MusicCatalogProvider> = listOf(
            ITunesCatalogProvider(
                transport = HttpITunesCatalogTransport()
            ),
            DeezerCatalogProvider(
                transport = HttpDeezerCatalogTransport()
            )
        )

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
