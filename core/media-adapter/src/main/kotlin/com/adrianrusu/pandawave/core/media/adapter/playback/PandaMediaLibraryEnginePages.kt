package com.adrianrusu.pandawave.core.media.adapter.playback

import com.adrianrusu.pandawave.core.model.catalog.BambooCatalogNode
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway

internal class EnginePagedCatalogLoader(private val engineGateway: EngineGateway) {
    fun load(
        state: CatalogQueryState,
        cacheKey: String,
        offset: Int,
        limit: Int,
        initialCommand: EngineCommand,
        countOf: (EngineSnapshot) -> Int,
        pageAt: (Int, Int) -> List<EngineCatalogItem>,
        toNode: (EngineCatalogItem, Int) -> BambooCatalogNode = { item, _ -> item.toCatalogNode() }
    ): CatalogPage {
        val requestSize = catalogRequestSize(limit)
        val requiredCount = offset + limit
        if (state.key != cacheKey || offset == 0) {
            state.reset(cacheKey)
            replaceAccumulated(state, engineGateway.dispatch(initialCommand), requestSize, countOf, pageAt, toNode)
        }
        while (state.items.size < requiredCount && state.hasNextPage) {
            val operationId = state.operationId ?: break
            val previousCount = state.items.size
            replaceAccumulated(
                state = state,
                outcome = engineGateway.dispatch(
                    EngineCommand(
                        EngineCommand.TYPE_LOAD_NEXT_CATALOG_PAGE,
                        EngineCommandPayloads.loadNextCatalogPage(operationId)
                    )
                ),
                requestSize = requestSize,
                countOf = countOf,
                pageAt = pageAt,
                toNode = toNode
            )
            if (state.items.size <= previousCount) {
                state.hasNextPage = false
            }
        }
        return state.toPage(offset, limit)
    }

    private fun replaceAccumulated(
        state: CatalogQueryState,
        outcome: EngineDispatchResult,
        requestSize: Int,
        countOf: (EngineSnapshot) -> Int,
        pageAt: (Int, Int) -> List<EngineCatalogItem>,
        toNode: (EngineCatalogItem, Int) -> BambooCatalogNode
    ) {
        val snapshot = outcome.snapshot
        val total = countOf(snapshot).coerceAtLeast(0)
        state.operationId = outcome.event.message?.takeIf(String::isNotBlank)
        state.generation += 1L
        state.items.clear()
        state.items += engineGateway.catalogPages(total, pageAt).mapIndexed { index, item ->
            toNode(item, index)
        }
        state.hasNextPage = !snapshot.hasError && state.items.size >= requestSize && total >= requestSize
    }
}

internal class EngineHistoryLoader(private val engineGateway: EngineGateway) {
    private var cacheKey: HistoryCacheKey? = null
    private val cache = mutableListOf<BambooCatalogNode>()
    private var hasNextPage = false

    fun page(offset: Int, limit: Int): CatalogPage {
        val requestSize = limit.coerceIn(1, MAX_HISTORY_PAGE_SIZE)
        val requiredCount = offset + limit
        val currentKey = engineGateway.snapshot().historyCacheKey()
        if (cacheKey != currentKey || offset == 0) {
            cacheKey = currentKey
            cache.clear()
            hasNextPage = false
            append(
                EngineCommand(
                    EngineCommand.TYPE_LIST_HISTORY,
                    EngineCommandPayloads.historyPage(requestSize)
                )
            )
        }
        while (cache.size < requiredCount && hasNextPage) {
            append(EngineCommand(EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE, null))
        }
        return CatalogPage(
            operationId = null,
            generation = currentKey.generation,
            totalCount = cache.size,
            items = cache.paged(offset = offset, limit = limit),
            hasNextPage = hasNextPage || offset + limit < cache.size
        )
    }

    private fun append(command: EngineCommand) {
        val outcome = engineGateway.dispatch(command)
        val snapshot = outcome.snapshot
        cacheKey = snapshot.historyCacheKey()
        cache += engineGateway.historyPage(
            offset = 0,
            limit = snapshot.historyEntriesCount.coerceIn(0, MAX_HISTORY_PAGE_SIZE),
            generation = snapshot.historyGeneration
        ).items.mapNotNull { item -> item.toCatalogNode() }
        hasNextPage = snapshot.hasHistoryNextPage
    }
}

internal class EngineSavedTracksLoader(private val engineGateway: EngineGateway) {
    private var generation: Long = -1L
    private val cache = mutableListOf<BambooCatalogNode>()
    private var hasNextPage = false

    fun page(offset: Int, limit: Int): CatalogPage {
        val snapshot = engineGateway.snapshot()
        if (generation != snapshot.savedTracksCount.toLong() || offset == 0) {
            generation = snapshot.savedTracksCount.toLong()
            cache.clear()
            val outcome = engineGateway.dispatch(
                EngineCommand(
                    EngineCommand.TYPE_LIST_SAVED_TRACKS,
                    EngineCommandPayloads.libraryPage(limit.coerceAtLeast(1))
                )
            )
            replace(outcome.snapshot)
        }
        while (cache.size < offset + limit && hasNextPage) {
            val outcome = engineGateway.dispatch(EngineCommand(EngineCommand.TYPE_LOAD_NEXT_SAVED_TRACKS_PAGE, null))
            val previous = cache.size
            append(outcome.snapshot)
            if (cache.size <= previous) hasNextPage = false
        }
        return CatalogPage(
            operationId = null,
            generation = generation,
            totalCount = cache.size,
            items = cache.paged(offset, limit),
            hasNextPage = hasNextPage || offset + limit < cache.size
        )
    }

    private fun replace(snapshot: EngineSnapshot) {
        cache.clear()
        append(snapshot)
    }

    private fun append(snapshot: EngineSnapshot) {
        cache += engineGateway.savedTracksPage(0, snapshot.savedTracksCount.coerceAtLeast(0))
            .map { item -> item.toCatalogNode() }
        hasNextPage = snapshot.hasSavedTracksNextPage
        generation = snapshot.savedTracksCount.toLong()
    }
}

internal class EngineForYouLoader(private val engineGateway: EngineGateway) {
    fun page(offset: Int, limit: Int, parentId: String): CatalogPage {
        val outcome = engineGateway.dispatch(
            EngineCommand(
                EngineCommand.TYPE_LOAD_FOR_YOU_FEED,
                EngineCommandPayloads.forYouFeed(pageSize = catalogRequestSize(limit))
            )
        )
        val items = engineGateway.forYouResultsPage(0, outcome.snapshot.forYouResultsCount.coerceAtLeast(0))
            .mapIndexed { index, item -> item.toCatalogNode(parentId, index) }
        return CatalogPage(
            operationId = outcome.event.message,
            generation = outcome.snapshot.forYouResultsCount.toLong(),
            totalCount = items.size,
            items = items.paged(offset, limit),
            hasNextPage = false
        )
    }
}

internal class EnginePlaylistLoader(private val engineGateway: EngineGateway) {
    fun playlists(offset: Int, limit: Int): CatalogPage {
        engineGateway.dispatch(
            EngineCommand(
                EngineCommand.TYPE_LIST_PLAYLISTS,
                EngineCommandPayloads.playlistPage(catalogRequestSize(limit))
            )
        )
        val snapshot = engineGateway.snapshot()
        val items = engineGateway.playlistsPage(0, snapshot.playlistsCount.coerceAtLeast(0)).map { item ->
            item.toCatalogNode()
        }
        return CatalogPage(
            operationId = null,
            generation = snapshot.playlistsCount.toLong(),
            totalCount = items.size,
            items = items.paged(offset, limit),
            hasNextPage = snapshot.hasPlaylistsNextPage
        )
    }

    fun tracks(playlistId: String, offset: Int, limit: Int): CatalogPage {
        engineGateway.dispatch(
            EngineCommand(
                EngineCommand.TYPE_LIST_PLAYLIST_TRACKS,
                EngineCommandPayloads.playlistPage(catalogRequestSize(limit), playlistId)
            )
        )
        val snapshot = engineGateway.snapshot()
        val items = engineGateway.playlistTracksPage(0, snapshot.playlistTracksCount.coerceAtLeast(0))
            .map { item -> item.toCatalogNode() }
        return CatalogPage(
            null,
            snapshot.playlistTracksCount.toLong(),
            items.size,
            items.paged(offset, limit),
            snapshot.hasPlaylistTracksNextPage
        )
    }
}

internal class CatalogQueryState {
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

    fun toPage(offset: Int, limit: Int): CatalogPage = CatalogPage(
        operationId = operationId,
        generation = generation,
        totalCount = items.size,
        items = items.paged(offset = offset, limit = limit),
        hasNextPage = hasNextPage || offset + limit < items.size
    )
}

internal fun EngineGateway.catalogPages(
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

internal fun EngineCatalogItem.toCatalogNode(parentId: String? = null, index: Int = 0): BambooCatalogNode {
    val engineId = mediaId
    val platformId = when {
        itemType.isPlayableCatalogType() && parentId != null ->
            PandaMediaSelectionId.occurrence(parentId, index, engineId)

        itemType.isPlayableCatalogType() -> PandaMediaSelectionId.track(engineId)

        else -> engineId
    }
    return BambooCatalogNode(
        mediaId = platformId,
        title = title,
        subtitle = subtitle(),
        artworkUri = artworkUri,
        isBrowsable = itemType.isBrowsableCatalogType(),
        isPlayable = itemType.isPlayableCatalogType(),
        artist = artist.takeUnless { value -> value.isNullOrBlank() },
        album = album.takeUnless { value -> value.isNullOrBlank() },
        catalogItemType = itemType,
        artworkId = artworkId,
        artworkVersion = artworkVersion,
        engineMediaId = engineId
    )
}

internal fun EngineHistoryItem.toCatalogNode(): BambooCatalogNode? {
    if (!playable) return null
    val engineId = mediaId?.takeIf(String::isNotBlank) ?: return null
    return BambooCatalogNode(
        mediaId = PandaMediaSelectionId.history(historyId, engineId),
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
        catalogItemType = EngineCatalogItem.TYPE_TRACK,
        artworkId = artworkId,
        artworkVersion = artworkVersion,
        engineMediaId = engineId
    )
}

internal fun EngineLibraryItem.toCatalogNode(): BambooCatalogNode = BambooCatalogNode(
    mediaId = PandaMediaSelectionId.saved(relationshipId, mediaId),
    title = title,
    subtitle = listOfNotNull(
        artist.takeUnless(String::isBlank),
        album?.takeUnless(String::isBlank)
    ).joinToString(separator = " - ").takeUnless(String::isBlank),
    artworkUri = artworkUri,
    isBrowsable = false,
    isPlayable = true,
    artist = artist.takeUnless(String::isBlank),
    album = album?.takeUnless(String::isBlank),
    catalogItemType = EngineCatalogItem.TYPE_TRACK,
    artworkId = artworkId,
    artworkVersion = artworkVersion,
    durationMillis = durationMillis.takeIf { value -> value > 0L },
    engineMediaId = mediaId
)

internal fun EnginePlaylistItem.toCatalogNode(): BambooCatalogNode = BambooCatalogNode(
    mediaId = PandaMediaSelectionId.playlistFolder(id),
    title = name,
    subtitle = description,
    isBrowsable = true,
    isPlayable = false,
    catalogItemType = EngineCatalogItem.TYPE_PLAYLIST,
    engineMediaId = id
)

internal fun EnginePlaylistTrackItem.toCatalogNode(): BambooCatalogNode = BambooCatalogNode(
    mediaId = PandaMediaSelectionId.playlistItem(playlistId, membershipId, mediaId),
    title = title,
    subtitle = listOfNotNull(
        artist.takeUnless(String::isBlank),
        album?.takeUnless(String::isBlank)
    ).joinToString(separator = " - ").takeUnless(String::isBlank),
    artworkUri = artworkUri,
    isBrowsable = false,
    isPlayable = true,
    artist = artist.takeUnless(String::isBlank),
    album = album?.takeUnless(String::isBlank),
    catalogItemType = EngineCatalogItem.TYPE_TRACK,
    artworkId = artworkId,
    artworkVersion = artworkVersion,
    durationMillis = durationMillis.takeIf { value -> value > 0L },
    engineMediaId = mediaId
)

internal fun EngineCatalogItem.subtitle(): String? = listOfNotNull(
    artist.takeUnless { value -> value.isNullOrBlank() },
    album.takeUnless { value -> value.isNullOrBlank() }
).joinToString(separator = " - ").takeUnless { value -> value.isBlank() }

internal fun Int.isBrowsableCatalogType(): Boolean = this in setOf(
    EngineCatalogItem.TYPE_ARTIST,
    EngineCatalogItem.TYPE_ALBUM,
    EngineCatalogItem.TYPE_FOLDER,
    EngineCatalogItem.TYPE_PLAYLIST
)

internal fun Int.isPlayableCatalogType(): Boolean = this in setOf(
    EngineCatalogItem.TYPE_TRACK,
    EngineCatalogItem.TYPE_RADIO_STATION
)

internal data class HistoryCacheKey(val owner: String, val generation: Long)

internal fun EngineSnapshot.historyCacheKey(): HistoryCacheKey {
    val owner = authState.account?.id?.takeIf(String::isNotBlank)
        ?: authState.session?.id?.takeIf(String::isNotBlank)
        ?: ANONYMOUS_HISTORY_OWNER
    return HistoryCacheKey(owner, historyGeneration)
}

internal fun EngineSnapshot.toCatalogGenerations(): CatalogGenerations = CatalogGenerations(
    history = historyGeneration,
    savedCount = savedTracksCount,
    forYouCount = forYouResultsCount,
    recommendationsCount = recommendationsResultsCount,
    playlistsCount = playlistsCount
)

internal fun catalogRequestSize(limit: Int): Int = limit.coerceIn(1, MAX_CATALOG_PAGE_SIZE)

internal fun <T> List<T>.paged(offset: Int, limit: Int): List<T> = when {
    offset < 0 || limit < 1 || offset >= size -> emptyList()
    else -> subList(offset, minOf(offset + limit, size))
}

internal const val MAX_HISTORY_PAGE_SIZE = 50
internal const val MAX_CATALOG_PAGE_SIZE = 50
private const val ANONYMOUS_HISTORY_OWNER = "anonymous"
