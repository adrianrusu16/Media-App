package com.adrianrusu.pandawave.core.media.adapter.playback

import com.adrianrusu.pandawave.core.playback.PandaPlaybackContext
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Opaque Media3 media IDs that preserve occurrence and context.
 *
 * Android treats these as tokens. PandaWave decodes them into an engine media
 * ID plus the collection the user selected from.
 */
internal data class PandaMediaSelection(
    val platformId: String,
    val mediaId: String,
    val context: PandaPlaybackContext,
    val occurrenceId: String? = null
)

internal object PandaMediaSelectionId {
    const val PREFIX = "pw:v1:"

    fun track(mediaId: String): String = encode("track", listOf(mediaId))

    fun history(historyId: String, mediaId: String): String = encode("history", listOf(historyId, mediaId))

    fun saved(relationshipId: String, mediaId: String): String = encode("saved", listOf(relationshipId, mediaId))

    fun playlistItem(playlistId: String, membershipId: String, mediaId: String): String =
        encode("playlist-item", listOf(playlistId, membershipId, mediaId))

    fun albumTrack(albumId: String, mediaId: String): String = encode("album-track", listOf(albumId, mediaId))

    fun occurrence(parentId: String, index: Int, mediaId: String): String =
        encode("occurrence", listOf(parentId, index.toString(), mediaId))

    fun playlistFolder(playlistId: String): String = encode("playlist-folder", listOf(playlistId))

    fun parse(platformId: String): PandaMediaSelection? {
        val raw = platformId.trim()
        if (raw.isEmpty()) return null
        if (!raw.startsWith(PREFIX)) {
            return PandaMediaSelection(
                platformId = raw,
                mediaId = raw,
                context = PandaPlaybackContext.Browse(raw)
            )
        }
        val body = raw.removePrefix(PREFIX)
        val separator = body.indexOf(':')
        if (separator <= 0) return null
        val kind = body.substring(0, separator)
        val parts = body.substring(separator + 1).split(':').map(::decodePart)
        return when (kind) {
            "track" -> parts.singleOrNull()?.let { mediaId ->
                PandaMediaSelection(raw, mediaId, PandaPlaybackContext.Browse(mediaId))
            }

            "history" -> parts.getOrNull(1)?.let { mediaId ->
                PandaMediaSelection(raw, mediaId, PandaPlaybackContext.History, parts[0])
            }

            "saved" -> parts.getOrNull(1)?.let { mediaId ->
                PandaMediaSelection(raw, mediaId, PandaPlaybackContext.Saved, parts[0])
            }

            "playlist-item" -> {
                val playlistId = parts.getOrNull(0) ?: return null
                val mediaId = parts.getOrNull(2) ?: return null
                PandaMediaSelection(raw, mediaId, PandaPlaybackContext.Playlist(playlistId), parts[1])
            }

            "album-track" -> {
                val albumId = parts.getOrNull(0) ?: return null
                val mediaId = parts.getOrNull(1) ?: return null
                PandaMediaSelection(raw, mediaId, PandaPlaybackContext.Album(albumId))
            }

            "occurrence" -> {
                val parentId = parts.getOrNull(0) ?: return null
                val mediaId = parts.getOrNull(2) ?: return null
                PandaMediaSelection(
                    platformId = raw,
                    mediaId = mediaId,
                    context = contextForParent(parentId),
                    occurrenceId = parts.getOrNull(1)
                )
            }

            "playlist-folder" -> parts.singleOrNull()?.let { playlistId ->
                PandaMediaSelection(raw, playlistId, PandaPlaybackContext.Playlist(playlistId))
            }

            "playlist" -> parts.singleOrNull()?.let { playlistId ->
                PandaMediaSelection(raw, playlistId, PandaPlaybackContext.Playlist(playlistId))
            }

            else -> null
        }
    }

    fun engineMediaId(platformId: String): String =
        parse(platformId)?.mediaId ?: platformId.trim()

    private fun encode(kind: String, parts: List<String>): String {
        val encoded = parts.joinToString(separator = ":") { part -> encodePart(part) }
        return "$PREFIX$kind:$encoded"
    }

    private fun contextForParent(parentId: String): PandaPlaybackContext = when (PandaMediaLibraryIds.canonicalize(parentId)) {
        PandaMediaLibraryIds.HISTORY, PandaMediaLibraryIds.PLATFORM_RECENT -> PandaPlaybackContext.History
        PandaMediaLibraryIds.SAVED -> PandaPlaybackContext.Saved
        PandaMediaLibraryIds.DOWNLOADS, PandaMediaLibraryIds.PLATFORM_OFFLINE -> PandaPlaybackContext.Downloads
        PandaMediaLibraryIds.PLATFORM_SUGGESTED,
        PandaMediaLibraryIds.PLATFORM_OFFLINE_SUGGESTED -> PandaPlaybackContext.ForYou
        else -> PandaPlaybackContext.Browse(parentId)
    }

    private fun encodePart(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun decodePart(value: String): String =
        URLDecoder.decode(value.replace("+", "%20"), StandardCharsets.UTF_8)
}
