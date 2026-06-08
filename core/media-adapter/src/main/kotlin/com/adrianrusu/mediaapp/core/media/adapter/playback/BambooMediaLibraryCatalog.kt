package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.adrianrusu.mediaapp.core.model.catalog.BambooCatalogNode

internal interface BambooCatalogSource {
    fun children(parentId: String): List<BambooCatalogNode>
}

internal object PlaceholderBambooCatalogSource : BambooCatalogSource {
    override fun children(parentId: String): List<BambooCatalogNode> = when (parentId) {
        LibraryItems.ROOT_MEDIA_ID -> rootChildren
        else -> emptyList()
    }

    private val rootChildren = listOf(
        BambooCatalogNode(
            mediaId = "pandawave.library.saved",
            title = "Saved music",
            subtitle = "Albums, artists, and playlists",
            isBrowsable = true,
            isPlayable = false
        ),
        BambooCatalogNode(
            mediaId = "pandawave.library.downloads",
            title = "Downloads",
            subtitle = "Offline-ready music",
            isBrowsable = true,
            isPlayable = false
        ),
        BambooCatalogNode(
            mediaId = "pandawave.library.recent",
            title = "Recently played",
            subtitle = "Listening history from PandaEngine",
            isBrowsable = true,
            isPlayable = false
        )
    )
}

internal class BambooMediaLibraryCatalog(private val source: BambooCatalogSource) {
    fun root(): MediaItem = LibraryItems.Root

    fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> = source.children(parentId)
        .paged(page = page, pageSize = pageSize)
        .map { node -> node.toMediaItem() }
}

internal object LibraryItems {
    const val ROOT_MEDIA_ID = "pandawave.library.root"

    val Root: MediaItem = MediaItem.Builder()
        .setMediaId(ROOT_MEDIA_ID)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("PandaWave")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        )
        .build()
}

private fun BambooCatalogNode.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(isBrowsable)
            .setIsPlayable(isPlayable)
            .build()
    )
    .build()

private fun <T> List<T>.paged(page: Int, pageSize: Int): List<T> {
    val fromIndex = page * pageSize
    return when {
        page < 0 || pageSize < 1 -> emptyList()
        fromIndex >= size -> emptyList()
        else -> subList(fromIndex, minOf(fromIndex + pageSize, size))
    }
}
