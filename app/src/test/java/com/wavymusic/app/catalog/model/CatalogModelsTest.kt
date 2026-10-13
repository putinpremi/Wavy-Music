package com.wavymusic.app.catalog.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CatalogModelsTest {

    @Test
    fun providerIdentityPreservesProviderAndVersion() {
        val original = ProviderItemId(
            providerId = "provider-a",
            itemId = "track-123",
            versionQualifier = "Live at Example Hall"
        )

        val remaster = ProviderItemId(
            providerId = "provider-a",
            itemId = "track-123",
            versionQualifier = "2011 Remaster"
        )

        val otherProvider = ProviderItemId(
            providerId = "provider-b",
            itemId = "track-123",
            versionQualifier = "Live at Example Hall"
        )

        assertEquals("Live at Example Hall", original.versionQualifier)
        assertNotEquals(original, remaster)
        assertNotEquals(original, otherProvider)
    }

    @Test
    fun playlistEntriesRepresentOccurrencesAndCanRepeatSameTrack() {
        val first = CatalogPlaylistEntry(
            entryId = "entry-1",
            playlistId = "playlist-1",
            position = 0,
            trackId = "track-1"
        )

        val second = CatalogPlaylistEntry(
            entryId = "entry-2",
            playlistId = "playlist-1",
            position = 4,
            trackId = "track-1"
        )

        assertEquals(first.trackId, second.trackId)
        assertNotEquals(first.entryId, second.entryId)
        assertNotEquals(first.position, second.position)
    }

    @Test
    fun catalogSourceDoesNotImplyPlaybackAvailability() {
        val source = CatalogSourceDescriptor(
            id = "source-a-track-1",
            providerId = "provider-a",
            providerItemId = "track-1",
            kind = CatalogSourceKind.TRACK,
            catalogUri = "https://example.com/catalog/track-1"
        )

        val report = SourceEligibilityReport(
            providerId = source.providerId,
            providerItemId = source.providerItemId,
            status = SourceEligibilityStatus.NOT_ASSESSED
        )

        assertEquals(
            SourceEligibilityStatus.NOT_ASSESSED,
            report.status
        )
        assertFalse(source.catalogUri.isNullOrBlank())
    }

    @Test
    fun missingArtistAndDurationAreNotInvented() {
        val track = CatalogTrack(
            id = "provider-a:track-1",
            title = "A real title from a parser fixture"
        )

        assertEquals(emptyList<String>(), track.artistNames)
        assertEquals(null, track.durationMs)
        assertEquals(emptySet<ProviderItemId>(), track.providerIds)
    }
}