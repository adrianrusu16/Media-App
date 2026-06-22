package com.adrianrusu.pandawave.core.media.adapter.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.adrianrusu.pandawave.core.model.catalog.BambooCatalogNode
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway

internal interface BambooCatalogSource {
    fun children(parentId: String): List<BambooCatalogNode>
    fun search(query: String): List<BambooCatalogNode>
}

internal class EngineBambooCatalogSource(
    private val playbackBridge: Media3PlaybackEngineBridge,
    private val engineGateway: EngineGateway
) : BambooCatalogSource {
    override fun children(parentId: String): List<BambooCatalogNode> {
        playbackBridge.dispatchCatalogBrowse(parentId.toEngineParentId())
        val engineResults = engineGateway.browseResults()
        return when {
            parentId == LibraryItems.ROOT_MEDIA_ID && engineResults.isEmpty() -> rootChildren
            else -> engineResults
        }
    }

    override fun search(query: String): List<BambooCatalogNode> {
        playbackBridge.dispatchCatalogSearch(query)
        return engineGateway.searchResults()
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

private fun EngineGateway.searchResults(): List<BambooCatalogNode> = List(
    size = snapshot().searchResultsCount,
    init = ::searchResult
).filterNotNull().map { item -> item.toCatalogNode() }

private fun EngineGateway.browseResults(): List<BambooCatalogNode> = List(
    size = snapshot().browseResultsCount,
    init = ::browseResult
).filterNotNull().map { item -> item.toCatalogNode() }

private fun EngineCatalogItem.toCatalogNode(): BambooCatalogNode = BambooCatalogNode(
    mediaId = mediaId,
    title = title,
    subtitle = subtitle(),
    artworkUri = artworkUri,
    isBrowsable = itemType.isBrowsableCatalogType(),
    isPlayable = itemType.isPlayableCatalogType()
)

private fun EngineCatalogItem.subtitle(): String? = listOfNotNull(
    artist.takeUnless { value -> value.isNullOrBlank() },
    album.takeUnless { value -> value.isNullOrBlank() }
).joinToString(separator = " - ").takeUnless { value -> value.isBlank() }

private fun Int.isBrowsableCatalogType(): Boolean = this in setOf(
    EngineCatalogItem.TYPE_ARTIST,
    EngineCatalogItem.TYPE_ALBUM,
    EngineCatalogItem.TYPE_FOLDER,
    EngineCatalogItem.TYPE_PLAYLIST
)

private fun Int.isPlayableCatalogType(): Boolean = this in setOf(
    EngineCatalogItem.TYPE_TRACK,
    EngineCatalogItem.TYPE_RADIO_STATION
)

internal class BambooMediaLibraryCatalog(private val source: BambooCatalogSource) {
    fun root(): MediaItem = LibraryItems.Root

    fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> = source.children(parentId)
        .paged(page = page, pageSize = pageSize)
        .map { node -> node.toMediaItem() }

    fun search(query: String, page: Int, pageSize: Int): List<MediaItem> = source.search(query)
        .paged(page = page, pageSize = pageSize)
        .map { node -> node.toMediaItem() }
}

internal object LibraryItems {
    const val ROOT_MEDIA_ID = "pandawave.library.root"
    const val ENGINE_ROOT_PARENT_ID = "root"

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

private fun String.toEngineParentId(): String = when (this) {
    LibraryItems.ROOT_MEDIA_ID -> LibraryItems.ENGINE_ROOT_PARENT_ID
    else -> this
}

private fun BambooCatalogNode.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtworkUri(artworkUri?.toAndroidUriOrNull())
            .setIsBrowsable(isBrowsable)
            .setIsPlayable(isPlayable)
            .build()
    )
    .build()

private fun String.toAndroidUriOrNull(): Uri? = try {
    this.toUri()
} catch (_: RuntimeException) {
    null
}

private fun <T> List<T>.paged(page: Int, pageSize: Int): List<T> {
    val fromIndex = page * pageSize
    return when {
        page < 0 || pageSize < 1 -> emptyList()
        fromIndex >= size -> emptyList()
        else -> subList(fromIndex, minOf(fromIndex + pageSize, size))
    }
}
