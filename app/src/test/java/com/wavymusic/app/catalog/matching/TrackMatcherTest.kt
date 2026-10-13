package com.wavymusic.app.catalog.matching

import com.wavymusic.app.catalog.model.CatalogTrack
import com.wavymusic.app.catalog.model.ProviderItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatcherTest {

    @Test
    fun normalizationIgnoresAccentsCaseAndPunctuation() {
        assertEquals(
            TrackMatcher.normalize("Beyoncé — Halo!"),
            TrackMatcher.normalize("beyonce halo")
        )
    }

    @Test
    fun matchingTitleArtistAndCloseDurationCanProduceHighConfidence() {
        val first = track(
            id = "result-a",
            title = "Beyoncé — Halo!",
            artistNames = listOf("Beyoncé"),
            durationMs = 240_000L
        )

        val second = track(
            id = "result-b",
            title = "beyonce halo",
            artistNames = listOf("beyonce"),
            durationMs = 240_700L
        )

        val decision = TrackMatcher.match(first, second)

        assertEquals(TrackMatchConfidence.HIGH, decision.confidence)
        assertTrue(decision.isMatch)
    }

    @Test
    fun sameTitleFromDifferentArtistsIsNotAMatch() {
        val first = track(
            id = "a",
            title = "Home",
            artistNames = listOf("Artist One")
        )

        val second = track(
            id = "b",
            title = "Home",
            artistNames = listOf("Artist Two")
        )

        val decision = TrackMatcher.match(first, second)

        assertEquals(TrackMatchReason.ARTIST_MISMATCH, decision.reason)
        assertFalse(decision.isMatch)
    }

    @Test
    fun sameTitleAndArtistWithDifferentVersionQualifiersIsRejected() {
        val original = track(id = "a", versionQualifier = "Original")
        val live = track(id = "b", versionQualifier = "Live")

        val decision = TrackMatcher.match(original, live)

        assertEquals(TrackMatchReason.VERSION_MISMATCH, decision.reason)
        assertFalse(decision.isMatch)
    }

    @Test
    fun identicalProviderItemWithConflictingVersionQualifiersIsRejected() {
        val original = track(
            id = "a",
            providerIds = setOf(
                ProviderItemId("provider-a", "track-1", "Original")
            )
        )

        val live = track(
            id = "b",
            providerIds = setOf(
                ProviderItemId("provider-a", "track-1", "Live")
            )
        )

        val decision = TrackMatcher.match(original, live)

        assertEquals(
            TrackMatchReason.PROVIDER_ID_VERSION_CONFLICT,
            decision.reason
        )
        assertFalse(decision.isMatch)
    }

    @Test
    fun exactProviderIdentityWinsOverMetadataFormattingDifferences() {
        val identity = ProviderItemId(
            "provider-a",
            "track-123",
            "Album Version"
        )

        val first = track(
            id = "a",
            providerIds = setOf(identity)
        )

        val second = track(
            id = "b",
            title = "A slightly changed display title",
            providerIds = setOf(identity)
        )

        val decision = TrackMatcher.match(first, second)

        assertEquals(
            TrackMatchConfidence.EXACT_PROVIDER_ID,
            decision.confidence
        )
        assertTrue(decision.isMatch)
    }

    @Test
    fun missingDurationIsAmbiguousAndNeverAutoMatches() {
        val first = track(id = "a", durationMs = null)
        val second = track(id = "b", durationMs = 240_000L)

        val decision = TrackMatcher.match(first, second)

        assertEquals(TrackMatchConfidence.AMBIGUOUS, decision.confidence)
        assertEquals(TrackMatchReason.MISSING_DURATION, decision.reason)
        assertFalse(decision.isMatch)
    }

    @Test
    fun largeDurationDifferenceRejectsOtherwiseSimilarTracks() {
        val first = track(id = "a", durationMs = 240_000L)
        val second = track(id = "b", durationMs = 280_000L)

        val decision = TrackMatcher.match(first, second)

        assertEquals(TrackMatchReason.DURATION_MISMATCH, decision.reason)
        assertFalse(decision.isMatch)
    }

    @Test
    fun differentKnownReleasesRemainAmbiguous() {
        val first = track(
            id = "a",
            releaseTitle = "First Album"
        )

        val second = track(
            id = "b",
            releaseTitle = "Second Album"
        )

        val decision = TrackMatcher.match(first, second)

        assertEquals(TrackMatchConfidence.AMBIGUOUS, decision.confidence)
        assertEquals(TrackMatchReason.RELEASE_MISMATCH, decision.reason)
        assertFalse(decision.isMatch)
    }

    private fun track(
        id: String,
        title: String = "Song Title",
        artistNames: List<String> = listOf("Artist Name"),
        durationMs: Long? = 240_000L,
        versionQualifier: String? = null,
        releaseTitle: String? = "Album Name",
        providerIds: Set<ProviderItemId> = emptySet()
    ) = CatalogTrack(
        id = id,
        title = title,
        providerIds = providerIds,
        artistNames = artistNames,
        durationMs = durationMs,
        versionQualifier = versionQualifier,
        releaseTitle = releaseTitle,
        releaseYear = 2024
    )
}