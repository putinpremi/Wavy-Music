
package com.wavymusic.app.catalog

import com.wavymusic.app.catalog.model.CatalogAlbum
import com.wavymusic.app.catalog.model.CatalogArtist
import com.wavymusic.app.catalog.storage.CatalogDao
import com.wavymusic.app.catalog.storage.CatalogOwnerType
import com.wavymusic.app.catalog.storage.toDomain

class CatalogDetailRepository(
    private val dao: CatalogDao
) {

    suspend fun artist(id: String): CatalogArtist? {
        if (id.isBlank()) return null

        val entity = dao.getArtist(id) ?: return null

        val providerIds = dao.getProviderIdentities(
            ownerType = CatalogOwnerType.ARTIST,
            ownerId = id
        ).map { it.toDomain() }

        val sources = dao.getSourceDescriptors(
            ownerType = CatalogOwnerType.ARTIST,
            ownerId = id
        ).map { it.toDomain() }

        return entity.toDomain(
            providerIds = providerIds,
            sources = sources
        )
    }

    suspend fun album(id: String): CatalogAlbum? {
        if (id.isBlank()) return null

        val entity = dao.getAlbum(id) ?: return null

        val providerIds = dao.getProviderIdentities(
            ownerType = CatalogOwnerType.ALBUM,
            ownerId = id
        ).map { it.toDomain() }

        val artistLinks = dao.getAlbumArtists(id)

        val sources = dao.getSourceDescriptors(
            ownerType = CatalogOwnerType.ALBUM,
            ownerId = id
        ).map { it.toDomain() }

        return entity.toDomain(
            providerIds = providerIds,
            artistLinks = artistLinks,
            sources = sources
        )
    }
}
