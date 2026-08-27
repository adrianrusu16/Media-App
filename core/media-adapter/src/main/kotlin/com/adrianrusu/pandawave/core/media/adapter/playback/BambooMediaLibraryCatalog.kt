package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.adrianrusu.pandawave.core.common.trace.PandaTrace
import com.adrianrusu.pandawave.core.model.catalog.BambooCatalogNode
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway

internal data class CatalogPage(
    val operationId: String?,
    val generation: Long,
    val totalCount: Int,
    val items: List<BambooCatalogNode>,
    val hasNextPage: Boolean
) {
    companion object {
        fun empty(generation: Long = 0L): CatalogPage = CatalogPage(
            operationId = null,
            generation = generation,
            totalCount = 0,
            items = emptyList(),
            hasNextPage = false
        )
    }
}

internal interface BambooCatalogSource {
    fun browse(parentId: String, offset: Int, limit: Int): CatalogPage
    fun search(query: String, offset: Int, limit: Int): CatalogPage
    fun item(mediaId: String): BambooCatalogNode?

    fun children(parentId: String, page: Int, pageSize: Int): List<BambooCatalogNode> = browse(
        parentId = parentId,
        offset = pageOffset(page, pageSize),
        limit = pageSize
    ).items
}

internal class EngineBambooCatalogSource(private val engineGateway: EngineGateway) : BambooCatalogSource {
    private val catalogLock = Any()
    private val itemCache = LinkedHashMap<String, BambooCatalogNode>()
    private val browseState = CatalogQueryState()
    private val searchState = CatalogQueryState()
    private var historyCacheKey: HistoryCacheKey? = null
    private val historyCache = mutableListOf<BambooCatalogNode>()
    private var hasHistoryNextPage = false

    override fun browse(parentId: String, offset: Int, limit: Int): CatalogPage =
        PandaTrace.section("PW.Media3.Catalog.sourceBrowse") {
            synchronized(catalogLock) {
                if (offset < 0 || limit < 1) {
                    CatalogPage.empty()
                } else if (parentId == LibraryItems.HISTORY_MEDIA_ID) {
                    historyPage(offset = offset, limit = limit)
                } else {
                    engineBrowse(parentId = parentId, offset = offset, limit = limit)
                }
            }
        }

    override fun search(query: String, offset: Int, limit: Int): CatalogPage =
        PandaTrace.section("PW.Media3.Catalog.sourceSearch") {
            synchronized(catalogLock) {
                if (offset < 0 || limit < 1 || query.isBlank()) {
                    CatalogPage.empty()
                } else {
                    pagedCatalog(
                        state = searchState,
                        cacheKey = query,
                        offset = offset,
                        limit = limit,
                        initialCommand = EngineCommand(
                            EngineCommand.TYPE_SEARCH,
                            EngineCommandPayloads.searchCatalog(
                                query = query,
                                pageSize = catalogRequestSize(limit)
                            )
                        ),
                        countOf = EngineSnapshot::searchResultsCount,
                        pageAt = engineGateway::searchResultsPage
                    )
                }
            }
        }

    override fun item(mediaId: String): BambooCatalogNode? = synchronized(catalogLock) {
        when (mediaId) {
            LibraryItems.ROOT_MEDIA_ID -> rootNode
            else -> itemCache[mediaId] ?: rootChildren.firstOrNull { node -> node.mediaId == mediaId }
        }
    }

    override fun children(parentId: String, page: Int, pageSize: Int): List<BambooCatalogNode> =
        PandaTrace.section("PW.Media3.Catalog.sourceChildren") {
            browse(
                parentId = parentId,
                offset = pageOffset(page, pageSize),
                limit = pageSize
            ).items
        }

    private val rootNode = BambooCatalogNode(
        mediaId = LibraryItems.ROOT_MEDIA_ID,
        title = "PandaWave",
        isBrowsable = true,
        isPlayable = false,
        catalogItemType = EngineCatalogItem.TYPE_FOLDER
    )

    private val rootChildren = listOf(
        BambooCatalogNode(
            mediaId = "pandawave.library.saved",
            title = "Saved music",
            subtitle = "Albums, artists, and playlists",
            isBrowsable = true,
            isPlayable = false,
            catalogItemType = EngineCatalogItem.TYPE_FOLDER
        ),
        BambooCatalogNode(
            mediaId = "pandawave.library.downloads",
            title = "Downloads",
            subtitle = "Offline-ready music",
            isBrowsable = true,
            isPlayable = false,
            catalogItemType = EngineCatalogItem.TYPE_FOLDER
        ),
        BambooCatalogNode(
            mediaId = LibraryItems.HISTORY_MEDIA_ID,
            title = "Recently played",
            subtitle = "Listening history from PandaEngine",
            isBrowsable = true,
            isPlayable = false,
            catalogItemType = EngineCatalogItem.TYPE_FOLDER
        )
    )

    private fun engineBrowse(parentId: String, offset: Int, limit: Int): CatalogPage {
        val page = pagedCatalog(
            state = browseState,
            cacheKey = parentId,
            offset = offset,
            limit = limit,
            initialCommand = EngineCommand(
                EngineCommand.TYPE_BROWSE,
                EngineCommandPayloads.browseCatalog(
                    parentId = parentId.toEngineParentId(),
                    pageSize = catalogRequestSize(limit)
                )
            ),
            countOf = EngineSnapshot::browseResultsCount,
            pageAt = engineGateway::browseResultsPage
        )
        return if (parentId == LibraryItems.ROOT_MEDIA_ID && page.items.isEmpty()) {
            rememberItems(rootChildren)
            CatalogPage(
                operationId = page.operationId,
                generation = page.generation,
                totalCount = rootChildren.size,
                items = rootChildren.paged(offset = offset, limit = limit),
                hasNextPage = offset + limit < rootChildren.size
            )
        } else {
            page
        }
    }

    private fun pagedCatalog(
        state: CatalogQueryState,
        cacheKey: String,
        offset: Int,
        limit: Int,
        initialCommand: EngineCommand,
        countOf: (EngineSnapshot) -> Int,
        pageAt: (Int, Int) -> List<EngineCatalogItem>
    ): CatalogPage {
        val requestSize = catalogRequestSize(limit)
        val requiredCount = offset + limit
        if (state.key != cacheKey || offset == 0) {
            state.reset(cacheKey)
            val outcome = engineGateway.dispatch(initialCommand)
            replaceAccumulated(state, outcome, requestSize, countOf, pageAt)
        }
        while (state.items.size < requiredCount && state.hasNextPage) {
            val operationId = state.operationId ?: break
            val outcome = engineGateway.dispatch(
                EngineCommand(
                    EngineCommand.TYPE_LOAD_NEXT_CATALOG_PAGE,
                    EngineCommandPayloads.loadNextCatalogPage(operationId)
                )
            )
            val previousCount = state.items.size
            replaceAccumulated(state, outcome, requestSize, countOf, pageAt)
            if (state.items.size <= previousCount) {
                state.hasNextPage = false
            }
        }
        rememberItems(state.items)
        return CatalogPage(
            operationId = state.operationId,
            generation = state.generation,
            totalCount = state.items.size,
            items = state.items.paged(offset = offset, limit = limit),
            hasNextPage = state.hasNextPage || offset + limit < state.items.size
        )
    }

    private fun replaceAccumulated(
        state: CatalogQueryState,
        outcome: EngineDispatchResult,
        requestSize: Int,
        countOf: (EngineSnapshot) -> Int,
        pageAt: (Int, Int) -> List<EngineCatalogItem>
    ) {
        val snapshot = outcome.snapshot
        val total = countOf(snapshot).coerceAtLeast(0)
        state.operationId = outcome.event.message?.takeIf(String::isNotBlank)
        state.generation += 1L
        state.items.clear()
        state.items += engineGateway.catalogPages(total, pageAt).map(EngineCatalogItem::toCatalogNode)
        state.hasNextPage = !snapshot.hasError && state.items.size >= requestSize && total >= requestSize
    }

    private fun historyPage(offset: Int, limit: Int): CatalogPage {
        val requestSize = limit.coerceIn(1, MAX_HISTORY_PAGE_SIZE)
        val requiredCount = offset + limit
        val currentKey = engineGateway.snapshot().historyCacheKey()
        if (historyCacheKey != currentKey || offset == 0) {
            historyCacheKey = currentKey
            historyCache.clear()
            hasHistoryNextPage = false
            if (currentKey != null) {
                appendHistoryPage(
                    EngineCommand(
                        EngineCommand.TYPE_LIST_HISTORY,
                        EngineCommandPayloads.historyPage(requestSize)
                    )
                )
            }
        }
        while (historyCache.size < requiredCount && hasHistoryNextPage) {
            appendHistoryPage(EngineCommand(EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE, null))
        }
        rememberItems(historyCache)
        return CatalogPage(
            operationId = null,
            generation = currentKey?.generation ?: 0L,
            totalCount = historyCache.size,
            items = historyCache.paged(offset = offset, limit = limit),
            hasNextPage = hasHistoryNextPage || offset + limit < historyCache.size
        )
    }

    private fun appendHistoryPage(command: EngineCommand) {
        val outcome = engineGateway.dispatch(command)
        val snapshot = outcome.snapshot
        historyCacheKey = snapshot.historyCacheKey()
        historyCache += engineGateway.historyPage(
            offset = 0,
            limit = snapshot.historyEntriesCount.coerceIn(0, MAX_HISTORY_PAGE_SIZE),
            generation = snapshot.historyGeneration
        ).items.mapNotNull(EngineHistoryItem::toCatalogNode)
        hasHistoryNextPage = snapshot.hasHistoryNextPage
    }

    private fun rememberItems(nodes: List<BambooCatalogNode>) {
        nodes.forEach { node -> itemCache[node.mediaId] = node }
    }
}

internal class BambooMediaLibraryCatalog(
    private val source: BambooCatalogSource,
    private val artworkUris: ArtworkUriProjector = PassthroughArtworkUriProjector
) {
    fun root(): MediaItem = LibraryItems.Root

    fun children(parentId: String, page: Int, pageSize: Int): List<MediaItem> =
        PandaTrace.section("PW.Media3.Catalog.children") {
            source.children(
                parentId = parentId,
                page = page,
                pageSize = pageSize
            ).map { node -> node.toMediaItem(artworkUris) }
        }

    fun browse(parentId: String, page: Int, pageSize: Int): CatalogPage = source.browse(
        parentId = parentId,
        offset = pageOffset(page, pageSize),
        limit = pageSize
    )

    fun search(query: String, page: Int, pageSize: Int): List<MediaItem> =
        PandaTrace.section("PW.Media3.Catalog.search") {
            source.search(
                query = query,
                offset = pageOffset(page, pageSize),
                limit = pageSize
            ).items.map { node -> node.toMediaItem(artworkUris) }
        }

    fun searchPage(query: String, page: Int, pageSize: Int): CatalogPage = source.search(
        query = query,
        offset = pageOffset(page, pageSize),
        limit = pageSize
    )

    fun item(mediaId: String): MediaItem? = when (mediaId) {
        LibraryItems.ROOT_MEDIA_ID -> LibraryItems.Root

        else -> source.item(mediaId)?.toMediaItem(artworkUris)
            ?: BambooMediaLibraryPlaybackSelection.playableMetadataItem(mediaId)
                .takeIf { mediaId.isNotBlank() }
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
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()
}

private class CatalogQueryState {
    var key: String? = null
    var operationId: String? = null
    var generation: Long = 0L
    val items: MutableList<BambooCatalogNode> = mutableListOf()
    var hasNextPage: Boolean = false

    fun reset(cacheKey: String) {
        key = cacheKey
        operationId = null
        items.clear()
        hasNextPage = false
    }
}

private fun EngineGateway.catalogPages(
    count: Int,
    pageAt: (Int, Int) -> List<EngineCatalogItem>
): List<EngineCatalogItem> {
    val total = count.coerceAtLeast(0)
    if (total == 0) return emptyList()
    return buildList {
        var offset = 0
        while (offset < total) {
            val page = pageAt(offset, minOf(MAX_CATALOG_PAGE_SIZE, total - offset))
            if (page.isEmpty()) break
            addAll(page)
            offset += page.size
        }
    }
}

private fun EngineCatalogItem.toCatalogNode(): BambooCatalogNode = BambooCatalogNode(
    mediaId = mediaId,
    title = title,
    subtitle = subtitle(),
    artworkUri = artworkUri,
    isBrowsable = itemType.isBrowsableCatalogType(),
    isPlayable = itemType.isPlayableCatalogType(),
    artist = artist.takeUnless { value -> value.isNullOrBlank() },
    album = album.takeUnless { value -> value.isNullOrBlank() },
    catalogItemType = itemType
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
        artist = artist.takeUnless { value -> value.isNullOrBlank() },
        album = album.takeUnless { value -> value.isNullOrBlank() },
        catalogItemType = EngineCatalogItem.TYPE_TRACK
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

private fun String.toEngineParentId(): String = when (this) {
    LibraryItems.ROOT_MEDIA_ID -> LibraryItems.ENGINE_ROOT_PARENT_ID
    else -> this
}

private data class HistoryCacheKey(val accountId: String, val sessionId: String, val generation: Long)

private fun EngineSnapshot.historyCacheKey(): HistoryCacheKey? {
    val accountId = authState.account?.id?.takeIf(String::isNotBlank) ?: return null
    val sessionId = authState.session?.id?.takeIf(String::isNotBlank) ?: return null
    return HistoryCacheKey(accountId, sessionId, historyGeneration)
}

private fun BambooCatalogNode.toMediaItem(artworkUris: ArtworkUriProjector): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUris.project(artworkUri))
            .setIsBrowsable(isBrowsable)
            .setIsPlayable(isPlayable)
            .setMediaType(catalogItemType.toMediaMetadataType(isBrowsable = isBrowsable, isPlayable = isPlayable))
            .build()
    )
    .build()

private fun Int?.toMediaMetadataType(isBrowsable: Boolean, isPlayable: Boolean): Int = when (this) {
    EngineCatalogItem.TYPE_TRACK -> MediaMetadata.MEDIA_TYPE_MUSIC

    EngineCatalogItem.TYPE_ARTIST -> MediaMetadata.MEDIA_TYPE_ARTIST

    EngineCatalogItem.TYPE_ALBUM -> MediaMetadata.MEDIA_TYPE_ALBUM

    EngineCatalogItem.TYPE_FOLDER -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED

    EngineCatalogItem.TYPE_PLAYLIST -> MediaMetadata.MEDIA_TYPE_PLAYLIST

    EngineCatalogItem.TYPE_RADIO_STATION -> MediaMetadata.MEDIA_TYPE_RADIO_STATION

    else -> when {
        isBrowsable -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        isPlayable -> MediaMetadata.MEDIA_TYPE_MUSIC
        else -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
    }
}

private fun catalogRequestSize(limit: Int): Int = limit.coerceIn(1, MAX_CATALOG_PAGE_SIZE)

internal fun pageOffset(page: Int, pageSize: Int): Int {
    if (page < 0 || pageSize < 1) return -1
    val offset = page.toLong() * pageSize.toLong()
    return if (offset > Int.MAX_VALUE) Int.MAX_VALUE else offset.toInt()
}

private fun <T> List<T>.paged(offset: Int, limit: Int): List<T> = when {
    offset < 0 || limit < 1 || offset >= size -> emptyList()
    else -> subList(offset, minOf(offset + limit, size))
}

private const val MAX_HISTORY_PAGE_SIZE = 50
private const val MAX_CATALOG_PAGE_SIZE = 50
