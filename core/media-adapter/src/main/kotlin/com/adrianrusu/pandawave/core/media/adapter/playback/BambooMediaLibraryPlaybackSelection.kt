package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent

/**
 * Maps Media3 queue selection onto PandaEngine play commands.
 *
 * Returned items never include [MediaItem.localConfiguration] so Media3 cannot
 * resolve capability URLs; [EngineEffect.TYPE_PREPARE_PLAYBACK_SOURCE] remains
 * the sole source-load owner.
 */
internal object BambooMediaLibraryPlaybackSelection {
    fun withoutLocalConfiguration(item: MediaItem): MediaItem = MediaItem.Builder()
        .setMediaId(item.mediaId)
        .setMediaMetadata(item.mediaMetadata)
        .build()

    fun withoutLocalConfiguration(items: List<MediaItem>): List<MediaItem> =
        items.map(::withoutLocalConfiguration)

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

    fun playbackIntent(mediaIds: List<String>, startIndex: Int): BambooPlaybackIntent? {
        val normalized = mediaIds.map(String::trim).filter(String::isNotBlank)
        if (normalized.isEmpty()) return null
        val index = startIndex.coerceIn(0, normalized.lastIndex)
        return if (normalized.size == 1) {
            BambooPlaybackIntent.PlayMedia(mediaId = normalized[index])
        } else {
            BambooPlaybackIntent.PlayQueue(mediaIds = normalized, startIndex = index)
        }
    }

    fun mediaIds(items: List<MediaItem>): List<String> = items.map { item -> item.mediaId.trim() }
        .filter(String::isNotBlank)
}
