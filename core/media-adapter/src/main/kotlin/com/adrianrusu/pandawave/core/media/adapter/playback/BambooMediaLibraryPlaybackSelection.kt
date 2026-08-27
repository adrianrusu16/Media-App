package com.adrianrusu.pandawave.core.media.adapter.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.PandaPlaybackContext

/**
 * Maps Media3 queue selection onto PandaEngine play commands.
 *
 * Returned items never include [MediaItem.localConfiguration] so Media3 cannot
 * resolve capability URLs; PREPARE_PLAYBACK_SOURCE remains the sole source-load owner.
 */
internal object BambooMediaLibraryPlaybackSelection {
    fun withoutLocalConfiguration(item: MediaItem): MediaItem = MediaItem.Builder()
        .setMediaId(item.mediaId)
        .setMediaMetadata(item.mediaMetadata)
        .setRequestMetadata(item.requestMetadata)
        .build()

    fun withoutLocalConfiguration(items: List<MediaItem>): List<MediaItem> = items.map(::withoutLocalConfiguration)

    fun playableMetadataItem(mediaId: String, title: String? = null): MediaItem = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title?.takeIf(String::isNotBlank) ?: mediaId)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build()
        )
        .build()

    fun playbackIntent(
        mediaIds: List<String>,
        startIndex: Int,
        context: PandaPlaybackContext? = null
    ): BambooPlaybackIntent.PlayFromContext? {
        val normalized = mediaIds.map(String::trim).filter(String::isNotBlank)
        if (normalized.isEmpty()) return null
        val index = startIndex.coerceIn(0, normalized.lastIndex)
        val engineIds = normalized.map(PandaMediaSelectionId::engineMediaId)
        val selected = PandaMediaSelectionId.parse(normalized[index])
        return BambooPlaybackIntent.PlayFromContext(
            context = context ?: selected?.context ?: PandaPlaybackContext.Browse(normalized[index]),
            selectedMediaId = engineIds[index],
            occurrenceId = selected?.occurrenceId,
            mediaIds = engineIds
        )
    }

    fun mediaIds(items: List<MediaItem>): List<String> = items.map { item -> item.mediaId.trim() }
        .filter(String::isNotBlank)

    fun classify(items: List<MediaItem>, startIndex: Int): Media3PlaybackRequest {
        if (items.isEmpty()) return Media3PlaybackRequest.EmptyVoice
        val item = items.getOrElse(startIndex.coerceAtLeast(0)) { items.first() }
        val searchQuery = item.requestMetadata.searchQuery?.trim().orEmpty()
        val requestUri = item.requestMetadata.mediaUri
        val mediaId = item.mediaId.trim().takeIf { value ->
            value.isNotEmpty() && value != MediaItem.DEFAULT_MEDIA_ID
        }
        return when {
            mediaId != null -> Media3PlaybackRequest.Selection(items, startIndex)
            searchQuery.isNotEmpty() -> Media3PlaybackRequest.Search(searchQuery)
            requestUri != null -> Media3PlaybackRequest.Uri(requestUri)
            else -> Media3PlaybackRequest.EmptyVoice
        }
    }

    fun fromUri(uri: Uri): BambooPlaybackIntent.PlayFromContext {
        val mediaId = uri.lastPathSegment?.takeIf(String::isNotBlank) ?: uri.toString()
        val parsed = PandaMediaSelectionId.parse(mediaId)
        return BambooPlaybackIntent.PlayFromContext(
            context = parsed?.context ?: PandaPlaybackContext.Browse(uri.toString()),
            selectedMediaId = parsed?.mediaId ?: mediaId,
            occurrenceId = parsed?.occurrenceId,
            mediaIds = listOf(parsed?.mediaId ?: mediaId)
        )
    }
}

internal sealed interface Media3PlaybackRequest {
    data class Selection(val items: List<MediaItem>, val startIndex: Int) : Media3PlaybackRequest
    data class Search(val query: String) : Media3PlaybackRequest
    data class Uri(val uri: android.net.Uri) : Media3PlaybackRequest
    data object EmptyVoice : Media3PlaybackRequest
}
