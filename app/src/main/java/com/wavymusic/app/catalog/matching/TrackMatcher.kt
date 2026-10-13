package com.wavymusic.app.catalog.matching

import com.wavymusic.app.catalog.model.CatalogTrack
import com.wavymusic.app.catalog.model.ProviderItemId
import java.text.Normalizer
import java.util.Locale

enum class TrackMatchConfidence {
    EXACT_PROVIDER_ID,
    HIGH,
    AMBIGUOUS,
    NONE
}

enum class TrackMatchReason {
    EXACT_PROVIDER_ID,
    METADATA_MATCH,
    TITLE_MISMATCH,
    ARTIST_MISMATCH,
    VERSION_MISMATCH,
    PROVIDER_ID_VERSION_CONFLICT,
    DURATION_MISMATCH,
    DURATION_CLOSE_BUT_AMBIGUOUS,
    MISSING_DURATION,
    RELEASE_MISMATCH,
    INSUFFICIENT_METADATA
}

data class TrackMatchDecision(
    val confidence: TrackMatchConfidence,
    val reason: TrackMatchReason,
    val durationDifferenceMs: Long? = null
) {
    /** Only exact IDs and high-confidence metadata matches may be accepted. */
    val isMatch: Boolean
        get() = confidence == TrackMatchConfidence.EXACT_PROVIDER_ID ||
            confidence == TrackMatchConfidence.HIGH
}

/**
 * Conservative, deterministic matching. Title equality alone is never
 * sufficient. Ambiguous decisions must not be used for automatic merging.
 */
object TrackMatcher {
    private const val HIGH_CONFIDENCE_DURATION_DELTA_MS = 1_500L
    private const val AMBIGUOUS_DURATION_DELTA_MS = 5_000L

    /** Accent-, case-, and punctuation-insensitive normalization. */
    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""

        val decomposed = Normalizer.normalize(
            value,
            Normalizer.Form.NFD
        )

        val withoutMarks = decomposed.replace(Regex("\\p{Mn}+"), "")

        return withoutMarks
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    fun match(
        left: CatalogTrack,
        right: CatalogTrack
    ): TrackMatchDecision {
        val exactIdentity = findEquivalentProviderIdentity(
            left.providerIds,
            right.providerIds
        )

        if (exactIdentity) {
            return TrackMatchDecision(
                confidence = TrackMatchConfidence.EXACT_PROVIDER_ID,
                reason = TrackMatchReason.EXACT_PROVIDER_ID
            )
        }

        if (hasProviderIdentityVersionConflict(
                left.providerIds,
                right.providerIds
            )
        ) {
            return decision(
                TrackMatchConfidence.NONE,
                TrackMatchReason.PROVIDER_ID_VERSION_CONFLICT
            )
        }

        val leftTitle = normalize(left.title)
        val rightTitle = normalize(right.title)

        if (leftTitle.isEmpty() || rightTitle.isEmpty()) {
            return decision(
                TrackMatchConfidence.AMBIGUOUS,
                TrackMatchReason.INSUFFICIENT_METADATA
            )
        }

        if (leftTitle != rightTitle) {
            return decision(
                TrackMatchConfidence.NONE,
                TrackMatchReason.TITLE_MISMATCH
            )
        }

        val leftArtists = normalizedArtistNames(left.artistNames)
        val rightArtists = normalizedArtistNames(right.artistNames)

        if (leftArtists.isEmpty() || rightArtists.isEmpty()) {
            return decision(
                TrackMatchConfidence.AMBIGUOUS,
                TrackMatchReason.INSUFFICIENT_METADATA
            )
        }

        if (leftArtists != rightArtists) {
            return decision(
                TrackMatchConfidence.NONE,
                TrackMatchReason.ARTIST_MISMATCH
            )
        }

        val leftVersion = normalize(left.versionQualifier)
        val rightVersion = normalize(right.versionQualifier)

        if (leftVersion != rightVersion) {
            return decision(
                TrackMatchConfidence.NONE,
                TrackMatchReason.VERSION_MISMATCH
            )
        }

        val leftDuration = left.durationMs
        val rightDuration = right.durationMs

        if (leftDuration == null || rightDuration == null) {
            return decision(
                TrackMatchConfidence.AMBIGUOUS,
                TrackMatchReason.MISSING_DURATION
            )
        }

        // Subtract the smaller non-negative value from the larger one.
        // This avoids overflow from taking abs(leftDuration - rightDuration).
        val durationDelta = maxOf(leftDuration, rightDuration) -
            minOf(leftDuration, rightDuration)

        if (durationDelta > AMBIGUOUS_DURATION_DELTA_MS) {
            return decision(
                TrackMatchConfidence.NONE,
                TrackMatchReason.DURATION_MISMATCH,
                durationDelta
            )
        }

        if (durationDelta > HIGH_CONFIDENCE_DURATION_DELTA_MS) {
            return decision(
                TrackMatchConfidence.AMBIGUOUS,
                TrackMatchReason.DURATION_CLOSE_BUT_AMBIGUOUS,
                durationDelta
            )
        }

        val leftRelease = normalize(left.releaseTitle)
        val rightRelease = normalize(right.releaseTitle)

        if (
            leftRelease.isNotEmpty() &&
            rightRelease.isNotEmpty() &&
            leftRelease != rightRelease
        ) {
            return decision(
                TrackMatchConfidence.AMBIGUOUS,
                TrackMatchReason.RELEASE_MISMATCH,
                durationDelta
            )
        }

        if (
            left.releaseYear != null &&
            right.releaseYear != null &&
            (
                maxOf(left.releaseYear, right.releaseYear).toLong() -
                    minOf(left.releaseYear, right.releaseYear).toLong()
                ) > 1L
        ) {
            return decision(
                TrackMatchConfidence.AMBIGUOUS,
                TrackMatchReason.RELEASE_MISMATCH,
                durationDelta
            )
        }

        return decision(
            TrackMatchConfidence.HIGH,
            TrackMatchReason.METADATA_MATCH,
            durationDelta
        )
    }

    private fun normalizedArtistNames(
        names: List<String>
    ): Set<String> =
        names.map(::normalize)
            .filter(String::isNotEmpty)
            .toSet()

    private fun findEquivalentProviderIdentity(
        left: Set<ProviderItemId>,
        right: Set<ProviderItemId>
    ): Boolean =
        left.any { leftId ->
            right.any { rightId ->
                leftId.providerId == rightId.providerId &&
                    leftId.itemId == rightId.itemId &&
                    normalize(leftId.versionQualifier) ==
                    normalize(rightId.versionQualifier)
            }
        }

    private fun hasProviderIdentityVersionConflict(
        left: Set<ProviderItemId>,
        right: Set<ProviderItemId>
    ): Boolean =
        left.any { leftId ->
            right.any { rightId ->
                leftId.providerId == rightId.providerId &&
                    leftId.itemId == rightId.itemId &&
                    normalize(leftId.versionQualifier) !=
                    normalize(rightId.versionQualifier)
            }
        }

    private fun decision(
        confidence: TrackMatchConfidence,
        reason: TrackMatchReason,
        durationDifferenceMs: Long? = null
    ) = TrackMatchDecision(
        confidence = confidence,
        reason = reason,
        durationDifferenceMs = durationDifferenceMs
    )
}