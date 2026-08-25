package com.adrianrusu.pandawave.core.media.adapter.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.adrianrusu.pandawave.core.common.trace.PandaTrace
import com.adrianrusu.pandawave.core.model.catalog.BambooCatalogNode
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway

internal interface BambooCatalogSource {
    fun children(parentId: String, page: Int, pageSize: Int): List<BambooCatalogNode>
    fun search(query: String): List<BambooCatalogNode>
}

internal class EngineBambooCatalogSource(
    private val playbackBridge: Media3PlaybackEngineBridge,
    private val engineGateway: EngineGateway
) : BambooCatalogSource {
    private var historyCacheKey: HistoryCacheKey? = null
    private val historyCache = mutableListOf<BambooCatalogNode>()
    private var hasHistoryNextPage = false

    override fun children(parentId: String, page: Int, pageSize: Int): List<BambooCatalogNode> =
        PandaTrace.section("PW.Media3.Catalog.sourceChildren") {
            if (parentId == LibraryItems.HISTORY_MEDIA_ID) {
                historyChildren(page = page, pageSize = pageSize)
            } else {
                playbackBridge.dispatchCatalogBrowse(parentId.toEngineParentId())
                val engineResults = engineGateway.browseResults()
                when {
                    parentId == LibraryItems.ROOT_MEDIA_ID && engineResults.isEmpty() ->
                        rootChildren.paged(page = page, pageSize = pageSize)

                    engineResults.isEmpty() -> emptyList()
                    else -> engineResults.paged(page = page, pageSize = pageSize)
                }
            }
        }

    override fun search(query: String): List<BambooCatalogNode> =
        PandaTrace.section("PW.Media3.Catalog.sourceSearch") {
            playbackBridge.dispatchCatalogSearch(query)
            engineGateway.searchResults()
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
            mediaId = LibraryItems.HISTORY_MEDIA_ID,
            title = "Recently played",
            subtitle = "Listening history from PandaEngine",
            isBrowsable = true,
            isPlayable = false
        )
    )

    private fun historyChildren(page: Int, pageSize: Int): List<BambooCatalogNode> =
        PandaTrace.section("PW.Media3.Catalog.historyChildren") {
            if (page < 0 || pageSize < 1) {
                emptyList()
            } else {
                val requestSize = pageSize.coerceIn(1, MAX_HISTORY_PAGE_SIZE)
                val requiredCount = (page + 1) * pageSize
                val currentKey = engineGateway.snapshot().historyCacheKey()
                if (historyCacheKey != currentKey || page == 0) {
                    historyCacheKey = currentKey
                    historyCache.clear()
                    hasHistoryNextPage = false
                    if (currentKey != null) {
                        appendHistoryPage(
                            EngineCommand(
                                EngineCommand.TYPE_LIST_HISTORY,
                                EngineCommandPayloads.historyPage(requestSize),
                            ),
                        )
                    }
                }
                while (historyCache.size < requiredCount && hasHistoryNextPage) {
                    appendHistoryPage(EngineCommand(EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE, null))
                }
                historyCache.paged(page = page, pageSize = pageSize)
            }
        }

    private fun appendHistoryPage(command: EngineCommand) {
        val outcome = engineGateway.dispatch(command)
        val snapshot = outcome.snapshot
        historyCacheKey = snapshot.historyCacheKey()
        historyCache += List(snapshot.historyEntriesCount.coerceAtLeast(0), engineGateway::historyEntry)
            .filterNotNull()
            .mapNotNull(EngineHistoryItem::toCatalogNode)
        hasHistoryNextPage = snapshot.hasHistoryNextPage
    }
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

private fun EngineHistoryItem.toCatalogNode(): BambooCatalogNode? {
    if (!playable) return null
    val mediaId = mediaId?.takeIf(String::isNotBlank) ?: return null
    return BambooCatalogNode(
        mediaId = mediaId,
        title = title,
        subtitle = listOfNotNull(
            artist.takeUnless { value -> value.isNullOrBlank() },
            album.takeUnless { value -> value.isNullOrBlank() }
        ).joinToString(separator = " - ").takeUnless { value -> value.isBlank() },
        artworkUri = artworkUri,
        isBrowsable = false,
        isPlayable = playable,
    )
}

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

    fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> =
        PandaTrace.section("PW.Media3.Catalog.children") {
            source.children(
                parentId = parentId,
                page = page,
                pageSize = pageSize,
            )
                .map { node -> node.toMediaItem() }
        }

    fun search(query: String, page: Int, pageSize: Int): List<MediaItem> =
        PandaTrace.section("PW.Media3.Catalog.search") {
            source.search(query)
                .paged(page = page, pageSize = pageSize)
                .map { node -> node.toMediaItem() }
        }
}

internal object LibraryItems {
    const val ROOT_MEDIA_ID = "pandawave.library.root"
    const val ENGINE_ROOT_PARENT_ID = "root"
    const val HISTORY_MEDIA_ID = "pandawave.library.recent"

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

private data class HistoryCacheKey(
    val accountId: String,
    val sessionId: String,
    val generation: Long,
)

private fun com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot.historyCacheKey(): HistoryCacheKey? {
    val accountId = authState.account?.id?.takeIf(String::isNotBlank) ?: return null
    val sessionId = authState.session?.id?.takeIf(String::isNotBlank) ?: return null
    return HistoryCacheKey(accountId, sessionId, historyGeneration)
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

private const val MAX_HISTORY_PAGE_SIZE = 50
