package com.wavymusic.app.catalog.provider.innertube

import com.wavymusic.app.catalog.model.CatalogAlbum
import com.wavymusic.app.catalog.model.CatalogArtist
import com.wavymusic.app.catalog.model.CatalogArtwork
import com.wavymusic.app.catalog.model.CatalogPlaylist
import com.wavymusic.app.catalog.model.CatalogReleaseType
import com.wavymusic.app.catalog.model.CatalogTrack
import com.wavymusic.app.catalog.model.ProviderItemId
import com.wavymusic.app.catalog.provider.CatalogContinuation
import com.wavymusic.app.catalog.provider.CatalogProviderError
import com.wavymusic.app.catalog.provider.CatalogProviderErrorCode
import com.wavymusic.app.catalog.provider.CatalogProviderResult
import com.wavymusic.app.catalog.provider.CatalogSearchType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class InnerTubeParseWarningCode {
    MISSING_REQUIRED_FIELD,
    UNKNOWN_ITEM_TYPE,
    MALFORMED_ITEM,
    DUPLICATE_ITEM
}

data class InnerTubeParseWarning(
    val code: InnerTubeParseWarningCode,
    val detail: String
)

sealed interface InnerTubeCatalogItem {
    data class Track(val value: CatalogTrack) : InnerTubeCatalogItem
    data class Artist(val value: CatalogArtist) : InnerTubeCatalogItem
    data class Album(val value: CatalogAlbum) : InnerTubeCatalogItem
    data class Playlist(val value: CatalogPlaylist) : InnerTubeCatalogItem
}

data class InnerTubeParsedPage(
    val items: List<InnerTubeCatalogItem>,
    val continuation: CatalogContinuation?,
    val warnings: List<InnerTubeParseWarning>
)

class InnerTubeCatalogParser(
    val providerId: String = PROVIDER_ID,
    private val continuationTtlMs: Long = DEFAULT_CONTINUATION_TTL_MS
) {
    init {
        require(providerId.isNotBlank())
        require(continuationTtlMs > 0L)
    }

    fun parse(
        payload: String,
        searchType: CatalogSearchType,
        nowEpochMs: Long
    ): CatalogProviderResult<InnerTubeParsedPage> {
        val root = try {
            JSONObject(payload)
        } catch (error: JSONException) {
            return CatalogProviderResult.Failure(
                CatalogProviderError(
                    code = CatalogProviderErrorCode.MALFORMED_RESPONSE,
                    message = "The catalog response was not valid JSON."
                )
            )
        }

        val renderers = mutableListOf<Renderer>()
        collectRenderers(root, renderers)

        val warnings = mutableListOf<InnerTubeParseWarning>()
        val items = mutableListOf<InnerTubeCatalogItem>()
        val seenItems = hashSetOf<String>()

        for (renderer in renderers) {
            val parsed = when (searchType) {
                CatalogSearchType.TRACKS ->
                    if (renderer.name == RESPONSIVE_TRACK_RENDERER) {
                        parseTrack(renderer.value, warnings)
                    } else {
                        null
                    }

                CatalogSearchType.ARTISTS,
                CatalogSearchType.ALBUMS,
                CatalogSearchType.PUBLIC_PLAYLISTS ->
                    if (renderer.name == TWO_ROW_RENDERER) {
                        parseTwoRow(renderer.value, searchType, warnings)
                    } else {
                        null
                    }
            } ?: continue

            val stableKey = itemStableKey(parsed)
            if (!seenItems.add(stableKey)) {
                warnings += InnerTubeParseWarning(
                    InnerTubeParseWarningCode.DUPLICATE_ITEM,
                    "Repeated catalog item was skipped: $stableKey"
                )
                continue
            }

            items += parsed
        }

        val continuationToken = findContinuationToken(root)
        val continuation = continuationToken
            ?.takeIf(String::isNotBlank)
            ?.let { token ->
                CatalogContinuation(
                    providerId = providerId,
                    token = token,
                    expiresAtEpochMs = safeAdd(
                        nowEpochMs,
                        continuationTtlMs
                    )
                )
            }

        return CatalogProviderResult.Success(
            InnerTubeParsedPage(
                items = items,
                continuation = continuation,
                warnings = warnings
            )
        )
    }

    private fun parseTrack(
        renderer: JSONObject,
        warnings: MutableList<InnerTubeParseWarning>
    ): InnerTubeCatalogItem.Track? {
        val title = firstFlexColumnText(renderer)
            .orEmpty()
            .trim()
            .ifBlank { return missingField(warnings, "Track title") }

        val videoId = findFirstValue(renderer, "videoId")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return missingField(warnings, "Track provider ID")

        val artistsColumn = flexColumnText(renderer, 1).orEmpty()
        val artistLinks = findArtistLinks(renderer)

        val artistNames = artistLinks.map { it.second }
            .distinctBy { it.lowercase() }
            .ifEmpty { fallbackNames(artistsColumn) }

        val releaseColumn = flexColumnText(renderer, 2).orEmpty()
        val releaseSegments = splitMetadata(releaseColumn)

        val releaseYear = YEAR_REGEX.find(releaseColumn)
            ?.value
            ?.toIntOrNull()

        val releaseTitle = releaseSegments.firstOrNull { segment ->
            segment.isNotBlank() &&
                !YEAR_REGEX.matches(segment) &&
                !isDuration(segment) &&
                !looksLikeTrackMetadata(segment)
        }

        return InnerTubeCatalogItem.Track(
            CatalogTrack(
                id = "$providerId:track:$videoId",
                title = title,
                providerIds = setOf(
                    ProviderItemId(
                        providerId = providerId,
                        itemId = videoId
                    )
                ),
                artistIds = artistLinks.map { it.first }.distinct(),
                artistNames = artistNames,
                releaseTitle = releaseTitle,
                releaseYear = releaseYear,
                durationMs = extractDurationMs(renderer),
                artwork = extractArtwork(renderer)
            )
        )
    }

    private fun parseTwoRow(
        renderer: JSONObject,
        expectedType: CatalogSearchType,
        warnings: MutableList<InnerTubeParseWarning>
    ): InnerTubeCatalogItem? {
        val title = textFromObject(renderer.optJSONObject("title"))
            .trim()
            .ifBlank { return missingField(warnings, "Two-row item title") }

        val endpoint = renderer.optJSONObject("navigationEndpoint")
            ?.optJSONObject("browseEndpoint")

        val browseId = endpoint
            ?.stringValue("browseId")
            ?.takeIf(String::isNotBlank)
            ?: return missingField(warnings, "Two-row item browse ID")

        val pageType = endpoint
            ?.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")
            ?.stringValue("pageType")
            .orEmpty()

        val inferredType = inferSearchType(pageType, browseId)

        if (inferredType == null) {
            warnings += InnerTubeParseWarning(
                InnerTubeParseWarningCode.UNKNOWN_ITEM_TYPE,
                "Unsupported or unknown browse renderer type: $pageType"
            )
            return null
        }

        if (inferredType != expectedType) return null

        val subtitle = textFromObject(renderer.optJSONObject("subtitle"))
            .trim()
            .ifBlank { null }

        val artistLinks = findArtistLinks(renderer)
        val artwork = extractArtwork(renderer)

        val identity = ProviderItemId(
            providerId = providerId,
            itemId = browseId
        )

        return when (expectedType) {
            CatalogSearchType.ARTISTS ->
                InnerTubeCatalogItem.Artist(
                    CatalogArtist(
                        id = "$providerId:artist:$browseId",
                        name = title,
                        providerIds = setOf(identity),
                        description = subtitle,
                        artwork = artwork
                    )
                )

            CatalogSearchType.ALBUMS -> {
                val segments = splitMetadata(subtitle.orEmpty())

                val year = YEAR_REGEX.find(subtitle.orEmpty())
                    ?.value
                    ?.toIntOrNull()

                val names = artistLinks.map { it.second }
                    .distinctBy { it.lowercase() }
                    .ifEmpty {
                        fallbackNames(
                            segments.filterNot {
                                YEAR_REGEX.matches(it) ||
                                    looksLikeTrackMetadata(it)
                            }.firstOrNull().orEmpty()
                        )
                    }

                InnerTubeCatalogItem.Album(
                    CatalogAlbum(
                        id = "$providerId:album:$browseId",
                        title = title,
                        providerIds = setOf(identity),
                        artistIds = artistLinks.map { it.first }.distinct(),
                        artistNames = names,
                        releaseYear = year,
                        releaseType = CatalogReleaseType.UNKNOWN,
                        artwork = artwork
                    )
                )
            }

            CatalogSearchType.PUBLIC_PLAYLISTS ->
                InnerTubeCatalogItem.Playlist(
                    CatalogPlaylist(
                        id = "$providerId:playlist:$browseId",
                        title = title,
                        providerIds = setOf(identity),
                        ownerName = subtitle,
                        isPublic = true,
                        isSearchablePublicly = true,
                        artwork = artwork
                    )
                )

            CatalogSearchType.TRACKS -> null
        }
    }

    private fun inferSearchType(
        pageType: String,
        browseId: String
    ): CatalogSearchType? {
        val page = pageType.uppercase()

        return when {
            page.contains("ARTIST") ->
                CatalogSearchType.ARTISTS

            page.contains("ALBUM") ->
                CatalogSearchType.ALBUMS

            page.contains("PLAYLIST") ->
                CatalogSearchType.PUBLIC_PLAYLISTS

            browseId.startsWith("UC") ->
                CatalogSearchType.ARTISTS

            browseId.startsWith("MPRE") ->
                CatalogSearchType.ALBUMS

            browseId.startsWith("VLPL") || browseId.startsWith("PL") ->
                CatalogSearchType.PUBLIC_PLAYLISTS

            else -> null
        }
    }

    private fun firstFlexColumnText(
        renderer: JSONObject
    ): String? = flexColumnText(renderer, 0)

    private fun flexColumnText(
        renderer: JSONObject,
        index: Int
    ): String? {
        val columns = renderer.optJSONArray("flexColumns") ?: return null
        if (index !in 0 until columns.length()) return null

        val column = columns.optJSONObject(index) ?: return null

        val flex = column.optJSONObject(
            "musicResponsiveListItemFlexColumnRenderer"
        ) ?: return null

        return textFromObject(flex.optJSONObject("text"))
            .takeIf(String::isNotBlank)
    }

    private fun textFromObject(value: JSONObject?): String {
        if (value == null) return ""

        val simpleText = value.stringValue("simpleText")
        if (!simpleText.isNullOrBlank()) return simpleText.trim()

        val runs = value.optJSONArray("runs") ?: return ""

        return buildString {
            for (index in 0 until runs.length()) {
                val run = runs.optJSONObject(index) ?: continue
                append(run.stringValue("text").orEmpty())
            }
        }.replace(WHITESPACE_REGEX, " ").trim()
    }

    private fun findArtistLinks(
        value: Any?
    ): List<Pair<String, String>> {
        // FIX: the map's values are artist identity/name pairs, not Strings.
        val results = linkedMapOf<String, Pair<String, String>>()

        fun visit(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val text = node.stringValue("text").orEmpty().trim()

                    val endpoint = node.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")

                    if (text.isNotBlank() && endpoint != null) {
                        val browseId = endpoint.stringValue("browseId")

                        val pageType = endpoint
                            .optJSONObject("browseEndpointContextSupportedConfigs")
                            ?.optJSONObject("browseEndpointContextMusicConfig")
                            ?.stringValue("pageType")
                            .orEmpty()

                        val isArtist = pageType.contains(
                            "ARTIST",
                            ignoreCase = true
                        ) || browseId?.startsWith("UC") == true

                        if (isArtist && !browseId.isNullOrBlank()) {
                            results.putIfAbsent(
                                browseId,
                                "$providerId:artist:$browseId" to text
                            )
                        }
                    }

                    val keys = node.keys()
                    while (keys.hasNext()) {
                        visit(node.opt(keys.next()))
                    }
                }

                is JSONArray -> {
                    for (index in 0 until node.length()) {
                        visit(node.opt(index))
                    }
                }
            }
        }

        visit(value)
        return results.values.toList()
    }

    private fun extractArtwork(
        renderer: JSONObject
    ): CatalogArtwork? {
        val candidates = mutableListOf<CatalogArtwork>()

        fun visit(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val thumbnails = node.optJSONArray("thumbnails")

                    if (thumbnails != null) {
                        for (index in 0 until thumbnails.length()) {
                            val thumb = thumbnails.optJSONObject(index)
                                ?: continue

                            val url = thumb.stringValue("url")
                                ?.takeIf(String::isNotBlank)
                                ?: continue

                            val width = thumb.optInt("width", 0)
                                .takeIf { it > 0 }

                            val height = thumb.optInt("height", 0)
                                .takeIf { it > 0 }

                            candidates += CatalogArtwork(
                                url = url,
                                widthPx = width,
                                heightPx = height
                            )
                        }
                    }

                    val keys = node.keys()
                    while (keys.hasNext()) {
                        visit(node.opt(keys.next()))
                    }
                }

                is JSONArray -> {
                    for (index in 0 until node.length()) {
                        visit(node.opt(index))
                    }
                }
            }
        }

        visit(renderer)

        return candidates.maxByOrNull { image ->
            (image.widthPx?.toLong() ?: 0L) *
                (image.heightPx?.toLong() ?: 0L)
        }
    }

    private fun extractDurationMs(renderer: JSONObject): Long? {
        val fixedColumns = renderer.optJSONArray("fixedColumns")
            ?: return null

        for (index in 0 until fixedColumns.length()) {
            val fixed = fixedColumns.optJSONObject(index)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                ?: continue

            val label = textFromObject(fixed.optJSONObject("text"))
            val match = DURATION_REGEX.matchEntire(label.trim()) ?: continue

            val hours = match.groups[1]?.value?.toLongOrNull() ?: 0L
            val minutes = match.groups[2]?.value?.toLongOrNull() ?: return null
            val seconds = match.groups[3]?.value?.toLongOrNull() ?: return null

            return (hours * 3_600L + minutes * 60L + seconds) * 1_000L
        }

        return null
    }

    private fun findFirstValue(
        value: Any?,
        key: String
    ): String? {
        when (value) {
            is JSONObject -> {
                val direct = value.stringValue(key)
                if (!direct.isNullOrBlank()) return direct

                val keys = value.keys()
                while (keys.hasNext()) {
                    val found = findFirstValue(value.opt(keys.next()), key)
                    if (!found.isNullOrBlank()) return found
                }
            }

            is JSONArray -> {
                for (index in 0 until value.length()) {
                    val found = findFirstValue(value.opt(index), key)
                    if (!found.isNullOrBlank()) return found
                }
            }
        }

        return null
    }

    private fun findContinuationToken(value: Any?): String? {
        when (value) {
            is JSONObject -> {
                val command = value.optJSONObject("continuationCommand")
                    ?.stringValue("token")

                if (!command.isNullOrBlank()) return command

                val nextData = value.optJSONObject("nextContinuationData")
                    ?.stringValue("continuation")

                if (!nextData.isNullOrBlank()) return nextData

                val keys = value.keys()
                while (keys.hasNext()) {
                    val found = findContinuationToken(value.opt(keys.next()))
                    if (!found.isNullOrBlank()) return found
                }
            }

            is JSONArray -> {
                for (index in 0 until value.length()) {
                    val found = findContinuationToken(value.opt(index))
                    if (!found.isNullOrBlank()) return found
                }
            }
        }

        return null
    }

    private fun collectRenderers(
        value: Any?,
        result: MutableList<Renderer>
    ) {
        when (value) {
            is JSONObject -> {
                val keys = value.keys()

                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = value.opt(key)

                    if (key in SUPPORTED_RENDERER_NAMES && child is JSONObject) {
                        result += Renderer(key, child)
                    }

                    collectRenderers(child, result)
                }
            }

            is JSONArray -> {
                for (index in 0 until value.length()) {
                    collectRenderers(value.opt(index), result)
                }
            }
        }
    }

    private fun itemStableKey(item: InnerTubeCatalogItem): String =
        when (item) {
            is InnerTubeCatalogItem.Track -> "TRACK:${item.value.id}"
            is InnerTubeCatalogItem.Artist -> "ARTIST:${item.value.id}"
            is InnerTubeCatalogItem.Album -> "ALBUM:${item.value.id}"
            is InnerTubeCatalogItem.Playlist -> "PLAYLIST:${item.value.id}"
        }

    private fun missingField(
        warnings: MutableList<InnerTubeParseWarning>,
        field: String
    ): Nothing? {
        warnings += InnerTubeParseWarning(
            InnerTubeParseWarningCode.MISSING_REQUIRED_FIELD,
            "Skipped an item because $field was missing."
        )
        return null
    }

    private fun fallbackNames(value: String): List<String> =
        splitMetadata(value)
            .filterNot(String::isBlank)
            .filterNot(::looksLikeTrackMetadata)
            .filterNot { YEAR_REGEX.matches(it) }
            .distinctBy { it.lowercase() }

    private fun splitMetadata(value: String): List<String> =
        value.split(METADATA_SEPARATOR_REGEX)
            .map(String::trim)
            .filter(String::isNotBlank)

    private fun looksLikeTrackMetadata(value: String): Boolean {
        val normalized = value.trim().lowercase()

        return normalized in setOf(
            "song",
            "video",
            "album",
            "single",
            "ep",
            "playlist",
            "official music video",
            "official audio"
        ) || normalized.contains(" views")
    }

    private fun isDuration(value: String): Boolean =
        DURATION_REGEX.matches(value.trim())

    private fun safeAdd(first: Long, second: Long): Long =
        if (first > Long.MAX_VALUE - second) Long.MAX_VALUE
        else first + second

    private fun JSONObject.stringValue(key: String): String? =
        optString(key, "")
            .takeIf { it.isNotBlank() && it != "null" }

    private data class Renderer(
        val name: String,
        val value: JSONObject
    )

    companion object {
        const val PROVIDER_ID = "youtube-music-innertube"
        const val DEFAULT_CONTINUATION_TTL_MS = 15 * 60 * 1_000L

        private const val RESPONSIVE_TRACK_RENDERER =
            "musicResponsiveListItemRenderer"

        private const val TWO_ROW_RENDERER =
            "musicTwoRowItemRenderer"

        private val SUPPORTED_RENDERER_NAMES = setOf(
            RESPONSIVE_TRACK_RENDERER,
            TWO_ROW_RENDERER
        )

        private val YEAR_REGEX = Regex("""\b(?:19|20)\d{2}\b""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
        private val METADATA_SEPARATOR_REGEX = Regex("""\s*[•·|]\s*""")

        private val DURATION_REGEX =
            Regex("""(?:(\d+):)?([0-5]?\d):([0-5]\d)""")
    }
}