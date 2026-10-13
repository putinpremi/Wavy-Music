package com.wavymusic.app.catalog.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CatalogTrackEntity::class,
        CatalogArtistEntity::class,
        CatalogAlbumEntity::class,
        CatalogPlaylistEntity::class,
        CatalogPlaylistEntryEntity::class,
        CatalogTrackArtistEntity::class,
        CatalogAlbumArtistEntity::class,
        CatalogPlaylistEntryArtistEntity::class,
        CatalogProviderIdentityEntity::class,
        CatalogSourceDescriptorEntity::class,
        CatalogSourceEligibilityEntity::class,
        CatalogSearchCacheEntity::class,
        CatalogSearchCacheResultEntity::class,
        CatalogRecentSearchEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class WavyCatalogDatabase : RoomDatabase() {

    abstract fun catalogDao(): CatalogDao

    companion object {
        private const val DATABASE_NAME = "wavy_catalog.db"

        @Volatile
        private var instance: WavyCatalogDatabase? = null

        fun getInstance(context: Context): WavyCatalogDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WavyCatalogDatabase::class.java,
                    DATABASE_NAME
                ).build().also { database ->
                    instance = database
                }
            }
        }
    }
}