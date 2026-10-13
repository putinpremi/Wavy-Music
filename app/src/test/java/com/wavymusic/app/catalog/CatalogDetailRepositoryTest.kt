
package com.wavymusic.app.catalog

import androidx.room.Room
import com.wavymusic.app.catalog.model.CatalogReleaseType
import com.wavymusic.app.catalog.storage.CatalogAlbumArtistEntity
import com.wavymusic.app.catalog.storage.CatalogAlbumEntity
import com.wavymusic.app.catalog.storage.CatalogArtistEntity
import com.wavymusic.app.catalog.storage.CatalogOwnerType
import com.wavymusic.app.catalog.storage.CatalogProviderIdentityEntity
import com.wavymusic.app.catalog.storage.CatalogSourceDescriptorEntity
import com.wavymusic.app.catalog.storage.WavyCatalogDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.wavymusic.app.catalog.storage.CatalogPlaylistEntity

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogDetailRepositoryTest {

    private lateinit var database: WavyCatalogDatabase
    private lateinit var repository: CatalogDetailRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            WavyCatalogDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        repository = CatalogDetailRepository(database.catalogDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun loadsArtistWithProviderIdentityAndCatalogLink() = runBlocking {
        val dao = database.catalogDao()
        val id = "itunes-search:artist:456"

        dao.upsertArtist(
            CatalogArtistEntity(
                id = id,
                name = "Nova Artist",
                description = "Fixture artist"
            )
        )

        dao.upsertProviderIdentities(
            listOf(
                CatalogProviderIdentityEntity(
                    ownerType = CatalogOwnerType.ARTIST,
                    ownerId = id,
                    providerId = "itunes-search",
                    providerItemId = "456"
                )
            )
        )

        dao.upsertSourceDescriptors(
            listOf(
                CatalogSourceDescriptorEntity(
                    ownerType = CatalogOwnerType.ARTIST,
                    ownerId = id,
                    id = "artist-source",
                    providerId = "itunes-search",
                    providerItemId = "456",
                    kind = "ARTIST",
                    catalogUri =
                        "https://itunes.apple.com/us/artist/nova-artist/456"
                )
            )
        )

        val artist = repository.artist(id)

        assertNotNull(artist)
        assertEquals("Nova Artist", artist?.name)
        assertEquals("Fixture artist", artist?.description)
        assertEquals("456", artist?.providerIds?.single()?.itemId)
        assertEquals(
            "https://itunes.apple.com/us/artist/nova-artist/456",
            artist?.sources?.single()?.catalogUri
        )
    }

    @Test
    fun loadsAlbumWithItsArtistLinkAndCatalogUrl() = runBlocking {
        val dao = database.catalogDao()
        val id = "itunes-search:album:789"

        dao.upsertAlbum(
            CatalogAlbumEntity(
                id = id,
                title = "Nova Album",
                releaseYear = 2023,
                releaseType = CatalogReleaseType.ALBUM.name,
                trackCount = 11
            )
        )

        dao.upsertAlbumArtists(
            listOf(
                CatalogAlbumArtistEntity(
                    albumId = id,
                    position = 0,
                    artistId = "itunes-search:artist:456",
                    nameSnapshot = "Nova Artist"
                )
            )
        )

        dao.upsertProviderIdentities(
            listOf(
                CatalogProviderIdentityEntity(
                    ownerType = CatalogOwnerType.ALBUM,
                    ownerId = id,
                    providerId = "itunes-search",
                    providerItemId = "789"
                )
            )
        )

        dao.upsertSourceDescriptors(
            listOf(
                CatalogSourceDescriptorEntity(
                    ownerType = CatalogOwnerType.ALBUM,
                    ownerId = id,
                    id = "album-source",
                    providerId = "itunes-search",
                    providerItemId = "789",
                    kind = "ALBUM",
                    catalogUri =
                        "https://itunes.apple.com/us/album/nova-album/789"
                )
            )
        )

        val album = repository.album(id)

        assertNotNull(album)
        assertEquals("Nova Album", album?.title)
        assertEquals(2023, album?.releaseYear)
        assertEquals(11, album?.trackCount)
        assertEquals(listOf("Nova Artist"), album?.artistNames)
        assertEquals("789", album?.providerIds?.single()?.itemId)
        assertEquals(
            "https://itunes.apple.com/us/album/nova-album/789",
            album?.sources?.single()?.catalogUri
        )
    }

    @Test
    fun loadsPublicPlaylistWithProviderIdentityAndCatalogLink() = runBlocking {
        val dao = database.catalogDao()
        val id = "deezer-public:playlist:999"

        dao.upsertPlaylist(
            CatalogPlaylistEntity(
                id = id,
                title = "Nova Mix",
                ownerName = "Curator",
                description = "A fixture playlist",
                isPublic = true,
                isSearchablePublicly = true,
                entryCount = 30
            )
        )

        dao.upsertProviderIdentities(
            listOf(
                CatalogProviderIdentityEntity(
                    ownerType = CatalogOwnerType.PLAYLIST,
                    ownerId = id,
                    providerId = "deezer-public",
                    providerItemId = "999"
                )
            )
        )

        dao.upsertSourceDescriptors(
            listOf(
                CatalogSourceDescriptorEntity(
                    ownerType = CatalogOwnerType.PLAYLIST,
                    ownerId = id,
                    id = "playlist-source",
                    providerId = "deezer-public",
                    providerItemId = "999",
                    kind = "PLAYLIST",
                    catalogUri = "https://www.deezer.com/playlist/999"
                )
            )
        )

        val playlist = repository.playlist(id)

        assertNotNull(playlist)
        assertEquals("Nova Mix", playlist?.title)
        assertEquals("Curator", playlist?.ownerName)
        assertEquals("A fixture playlist", playlist?.description)
        assertEquals(30, playlist?.entryCount)
        assertEquals(true, playlist?.isPublic)
        assertEquals(true, playlist?.isSearchablePublicly)
        assertEquals("999", playlist?.providerIds?.single()?.itemId)
        assertEquals(
            "https://www.deezer.com/playlist/999",
            playlist?.sources?.single()?.catalogUri
        )
    }

    @Test
    fun unknownCatalogItemsReturnNull() = runBlocking {
        assertNull(repository.artist("missing-artist"))
        assertNull(repository.album("missing-album"))
    }
}
