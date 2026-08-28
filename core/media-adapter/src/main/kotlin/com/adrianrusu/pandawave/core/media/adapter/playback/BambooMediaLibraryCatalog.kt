package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.adrianrusu.pandawave.core.common.trace.PandaTrace
import com.adrianrusu.pandawave.core.model.catalog.BambooCatalogNode
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
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
    fun generations(): CatalogGenerations = CatalogGenerations()

    fun children(parentId: String, page: Int, pageSize: Int): List<BambooCatalogNode> = browse(
        parentId = parentId,
        offset = pageOffset(page, pageSize),
        limit = pageSize
    ).items

    fun materialize(parentId: String, limit: Int): CatalogPage {
        val cap = limit.coerceIn(1, AAOS_MATERIALIZED_LIMIT)
        val items = mutableListOf<BambooCatalogNode>()
        var offset = 0
        var generation = 0L
        var operationId: String? = null
        while (items.size < cap) {
            val page = browse(parentId, offset, cap - items.size)
            if (page.items.isEmpty()) break
            items += page.items
            generation = page.generation
            operationId = page.operationId
            if (!page.hasNextPage) break
            offset += page.items.size
        }
        return CatalogPage(operationId, generation, items.size, items, hasNextPage = false)
    }
}

internal class EngineBambooCatalogSource(private val engineGateway: EngineGateway) : BambooCatalogSource {
    private val catalogLock = Any()
    private val itemCache = LinkedHashMap<String, BambooCatalogNode>()
    private val browseState = CatalogQueryState()
    private val searchState = CatalogQueryState()
    private val paged = EnginePagedCatalogLoader(engineGateway)
    private val history = EngineHistoryLoader(engineGateway)
    private val saved = EngineSavedTracksLoader(engineGateway)
    private val forYou = EngineForYouLoader(engineGateway)
    private val playlists = EnginePlaylistLoader(engineGateway)

    override fun browse(parentId: String, offset: Int, limit: Int): CatalogPage =
        PandaTrace.section("PW.Media3.Catalog.sourceBrowse") {
            synchronized(catalogLock) {
                if (offset < 0 || limit < 1) {
                    CatalogPage.empty()
                } else {
                    remember(routeBrowse(parentId, offset, limit))
                }
            }
        }

    override fun search(query: String, offset: Int, limit: Int): CatalogPage =
        PandaTrace.section("PW.Media3.Catalog.sourceSearch") {
            synchronized(catalogLock) {
                if (offset < 0 || limit < 1 || query.isBlank()) {
                    CatalogPage.empty()
                } else {
                    remember(
                        paged.load(
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
                            pageAt = engineGateway::searchResultsPage,
                            toNode = { item, index -> item.toCatalogNode("pw.search", index) }
                        )
                    )
                }
            }
        }

    override fun item(mediaId: String): BambooCatalogNode? = synchronized(catalogLock) {
        when (val canonical = PandaMediaLibraryIds.canonicalize(mediaId)) {
            PandaMediaLibraryIds.ROOT -> rootNode

            in syntheticNodes -> syntheticNodes.getValue(canonical)

            else -> itemCache[mediaId] ?: itemCache.values.firstOrNull { node ->
                node.engineMediaId == mediaId || node.mediaId == mediaId
            }
        }
    }

    override fun generations(): CatalogGenerations = engineGateway.snapshot().toCatalogGenerations()

    override fun children(parentId: String, page: Int, pageSize: Int): List<BambooCatalogNode> =
        PandaTrace.section("PW.Media3.Catalog.sourceChildren") {
            browse(
                parentId = parentId,
                offset = pageOffset(page, pageSize),
                limit = pageSize
            ).items
        }

    private fun routeBrowse(parentId: String, offset: Int, limit: Int): CatalogPage {
        val canonical = PandaMediaLibraryIds.canonicalize(parentId)
        val selection = PandaMediaSelectionId.parse(parentId)
        return when {
            canonical == PandaMediaLibraryIds.ROOT -> syntheticPage(rootChildren, offset, limit)

            canonical == PandaMediaLibraryIds.LIBRARY -> syntheticPage(libraryChildren, offset, limit)

            canonical == PandaMediaLibraryIds.SAVED ||
                canonical == PandaMediaLibraryIds.DOWNLOADS ||
                canonical == PandaMediaLibraryIds.PLATFORM_OFFLINE -> saved.page(offset, limit)

            canonical == PandaMediaLibraryIds.HISTORY ||
                canonical == PandaMediaLibraryIds.PLATFORM_RECENT -> history.page(offset, limit)

            canonical == PandaMediaLibraryIds.PLATFORM_SUGGESTED ||
                canonical == PandaMediaLibraryIds.PLATFORM_OFFLINE_SUGGESTED ->
                forYou.page(offset, limit, canonical)

            canonical == PandaMediaLibraryIds.PLAYLISTS -> playlists.playlists(offset, limit)

            canonical == PandaMediaLibraryIds.ALBUMS || canonical == PandaMediaLibraryIds.ARTISTS ->
                engineBrowse(PandaMediaLibraryIds.engineParentId(canonical) ?: canonical, offset, limit)

            selection?.context is com.adrianrusu.pandawave.core.playback.PandaPlaybackContext.Playlist &&
                parentId.contains(":playlist-folder:") ->
                playlists.tracks(selection.mediaId, offset, limit)

            else -> engineBrowse(parentId, offset, limit)
        }
    }

    private fun engineBrowse(parentId: String, offset: Int, limit: Int): CatalogPage = paged.load(
        state = browseState,
        cacheKey = parentId,
        offset = offset,
        limit = limit,
        initialCommand = EngineCommand(
            EngineCommand.TYPE_BROWSE,
            EngineCommandPayloads.browseCatalog(
                parentId = PandaMediaLibraryIds.engineParentId(parentId) ?: parentId,
                pageSize = catalogRequestSize(limit)
            )
        ),
        countOf = EngineSnapshot::browseResultsCount,
        pageAt = engineGateway::browseResultsPage,
        toNode = { item, index -> item.toCatalogNode(parentId, index) }
    )

    private fun remember(page: CatalogPage): CatalogPage {
        page.items.forEach { node ->
            itemCache[node.mediaId] = node
            node.engineMediaId?.let { engineId -> itemCache.putIfAbsent(engineId, node) }
        }
        return page
    }
}

internal class BambooMediaLibraryCatalog(
    private val source: BambooCatalogSource,
    private val artworkUris: ArtworkUriProjector = PassthroughArtworkUriProjector
) {
    fun root(hints: PandaLibraryBrowseHints = PandaLibraryBrowseHints()): MediaItem {
        val rootId = hints.rootId()
        return source.item(rootId)?.toMediaItem(artworkUris) ?: LibraryItems.item(rootId)
    }

    fun children(
        parentId: String,
        page: Int,
        pageSize: Int,
        hints: PandaLibraryBrowseHints = PandaLibraryBrowseHints()
    ): List<MediaItem> = PandaTrace.section("PW.Media3.Catalog.children") {
        val nodes = if (hints.ignoreHostPagination) {
            source.materialize(parentId, hints.pageLimit(pageSize)).items
        } else {
            source.browse(
                parentId = parentId,
                offset = hints.offsetFor(page, pageSize),
                limit = hints.pageLimit(pageSize)
            ).items
        }
        nodes.filter { node -> !hints.browsableOnly || node.isBrowsable }
            .map { node -> node.toMediaItem(artworkUris) }
    }

    fun browse(parentId: String, page: Int, pageSize: Int): CatalogPage = source.browse(
        parentId = parentId,
        offset = pageOffset(page, pageSize),
        limit = pageSize
    )

    fun search(
        query: String,
        page: Int,
        pageSize: Int,
        hints: PandaLibraryBrowseHints = PandaLibraryBrowseHints()
    ): List<MediaItem> = PandaTrace.section("PW.Media3.Catalog.search") {
        val pageResult = if (hints.ignoreHostPagination) {
            materializeSearch(query, hints.pageLimit(pageSize))
        } else {
            source.search(query, hints.offsetFor(page, pageSize), hints.pageLimit(pageSize))
        }
        pageResult.items.map { node -> node.toMediaItem(artworkUris) }
    }

    fun searchPage(
        query: String,
        page: Int,
        pageSize: Int,
        hints: PandaLibraryBrowseHints = PandaLibraryBrowseHints()
    ): CatalogPage = if (hints.ignoreHostPagination) {
        materializeSearch(query, hints.pageLimit(pageSize))
    } else {
        source.search(query, hints.offsetFor(page, pageSize), hints.pageLimit(pageSize))
    }

    fun item(mediaId: String): MediaItem? = when (PandaMediaLibraryIds.canonicalize(mediaId)) {
        PandaMediaLibraryIds.ROOT -> LibraryItems.Root
        else -> source.item(mediaId)?.toMediaItem(artworkUris)
    }

    fun generations(): CatalogGenerations = source.generations()

    private fun materializeSearch(query: String, limit: Int): CatalogPage {
        val cap = limit.coerceIn(1, AAOS_MATERIALIZED_LIMIT)
        val items = mutableListOf<BambooCatalogNode>()
        var offset = 0
        var generation = 0L
        var operationId: String? = null
        while (items.size < cap) {
            val page = source.search(query, offset, cap - items.size)
            if (page.items.isEmpty()) break
            items += page.items
            generation = page.generation
            operationId = page.operationId
            if (!page.hasNextPage) break
            offset += page.items.size
        }
        return CatalogPage(operationId, generation, items.size, items, hasNextPage = false)
    }
}

internal object LibraryItems {
    const val ROOT_MEDIA_ID = PandaMediaLibraryIds.ROOT
    const val ENGINE_ROOT_PARENT_ID = PandaMediaLibraryIds.ENGINE_ROOT_PARENT_ID
    const val HISTORY_MEDIA_ID = PandaMediaLibraryIds.HISTORY

    val Root: MediaItem = folderItem(PandaMediaLibraryIds.ROOT, "PandaWave")

    fun item(mediaId: String): MediaItem = when (PandaMediaLibraryIds.canonicalize(mediaId)) {
        PandaMediaLibraryIds.ROOT -> Root

        PandaMediaLibraryIds.PLATFORM_RECENT -> folderItem(PandaMediaLibraryIds.PLATFORM_RECENT, "Recently played")

        PandaMediaLibraryIds.PLATFORM_SUGGESTED -> folderItem(PandaMediaLibraryIds.PLATFORM_SUGGESTED, "For You")

        PandaMediaLibraryIds.PLATFORM_OFFLINE -> folderItem(PandaMediaLibraryIds.PLATFORM_OFFLINE, "Downloads")

        PandaMediaLibraryIds.PLATFORM_OFFLINE_SUGGESTED ->
            folderItem(PandaMediaLibraryIds.PLATFORM_OFFLINE_SUGGESTED, "Offline suggestions")

        else -> folderItem(mediaId, "PandaWave")
    }
}

private val rootNode = BambooCatalogNode(
    mediaId = PandaMediaLibraryIds.ROOT,
    title = "PandaWave",
    isBrowsable = true,
    isPlayable = false,
    catalogItemType = EngineCatalogItem.TYPE_FOLDER
)

private val rootChildren = listOf(
    folderNode(PandaMediaLibraryIds.SAVED, "Saved music", "Albums, artists, and playlists"),
    folderNode(PandaMediaLibraryIds.DOWNLOADS, "Downloads", "Offline-ready music"),
    folderNode(PandaMediaLibraryIds.HISTORY, "Recently played", "Listening history from PandaEngine"),
    folderNode(PandaMediaLibraryIds.LIBRARY, "Library", "Playlists, albums, and artists")
)

private val libraryChildren = listOf(
    folderNode(PandaMediaLibraryIds.PLAYLISTS, "Playlists", "Your playlists"),
    folderNode(PandaMediaLibraryIds.ALBUMS, "Albums", "Albums from PandaEngine"),
    folderNode(PandaMediaLibraryIds.ARTISTS, "Artists", "Artists from PandaEngine")
)

private val platformRoots = listOf(
    folderNode(PandaMediaLibraryIds.PLATFORM_RECENT, "Recently played"),
    folderNode(PandaMediaLibraryIds.PLATFORM_SUGGESTED, "For You"),
    folderNode(PandaMediaLibraryIds.PLATFORM_OFFLINE, "Downloads"),
    folderNode(
        PandaMediaLibraryIds.PLATFORM_OFFLINE_SUGGESTED,
        "Offline suggestions"
    )
)

private val syntheticNodes: Map<String, BambooCatalogNode> =
    (listOf(rootNode) + rootChildren + libraryChildren + platformRoots).associateBy { node -> node.mediaId }

private fun folderNode(mediaId: String, title: String, subtitle: String? = null): BambooCatalogNode = BambooCatalogNode(
    mediaId = mediaId,
    title = title,
    subtitle = subtitle,
    isBrowsable = true,
    isPlayable = false,
    catalogItemType = EngineCatalogItem.TYPE_FOLDER
)

private fun folderItem(mediaId: String, title: String): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build()
    )
    .build()

private fun syntheticPage(nodes: List<BambooCatalogNode>, offset: Int, limit: Int): CatalogPage = CatalogPage(
    operationId = null,
    generation = 1L,
    totalCount = nodes.size,
    items = nodes.paged(offset = offset, limit = limit),
    hasNextPage = offset + limit < nodes.size
)

private fun BambooCatalogNode.toMediaItem(artworkUris: ArtworkUriProjector): MediaItem = MediaItem.Builder()
    .setMediaId(mediaId)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setDurationMs(durationMillis)
            .setArtworkUri(artworkUris.project(artworkUri, artworkId, artworkVersion))
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

internal fun pageOffset(page: Int, pageSize: Int): Int {
    if (page < 0 || pageSize < 1) return -1
    val offset = page.toLong() * pageSize.toLong()
    return if (offset > Int.MAX_VALUE) Int.MAX_VALUE else offset.toInt()
}
