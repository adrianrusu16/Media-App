package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.adrianrusu.pandawave.core.model.catalog.BambooCatalogNode
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAccount
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthSession
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(UnstableApi::class)
class BambooMediaLibraryCatalogTest {
    @Test
    fun `root returns browsable panda wave item`() {
        val catalog = BambooMediaLibraryCatalog(source = EmptyCatalogSource)

        val root = catalog.root()

        assertEquals(LibraryItems.ROOT_MEDIA_ID, root.mediaId)
        assertEquals("PandaWave", root.mediaMetadata.title.toString())
        assertTrue(root.mediaMetadata.isBrowsable == true)
        assertFalse(root.mediaMetadata.isPlayable == true)
    }

    @Test
    fun `root children are projected from catalog source`() {
        val catalog = BambooMediaLibraryCatalog(
            source = FixedCatalogSource(
                parentId = LibraryItems.ROOT_MEDIA_ID,
                children = listOf(
                    BambooCatalogNode(
                        mediaId = "catalog.albums",
                        title = "Albums",
                        subtitle = "Saved albums",
                        isBrowsable = true,
                        isPlayable = false
                    ),
                    BambooCatalogNode(
                        mediaId = "catalog.mix",
                        title = "Daily drive",
                        subtitle = "Ready to play",
                        isBrowsable = false,
                        isPlayable = true
                    )
                )
            )
        )

        val children = catalog.children(
            parentId = LibraryItems.ROOT_MEDIA_ID,
            page = 0,
            pageSize = 10
        )

        assertEquals(listOf("catalog.albums", "catalog.mix"), children.map { it.mediaId })
        assertEquals("Albums", children[0].mediaMetadata.title.toString())
        assertEquals("Saved albums", children[0].mediaMetadata.subtitle.toString())
        assertTrue(children[0].mediaMetadata.isBrowsable == true)
        assertFalse(children[0].mediaMetadata.isPlayable == true)
        assertFalse(children[1].mediaMetadata.isBrowsable == true)
        assertTrue(children[1].mediaMetadata.isPlayable == true)
    }

    @Test
    fun `children are paged`() {
        val catalog = BambooMediaLibraryCatalog(
            source = FixedCatalogSource(
                parentId = LibraryItems.ROOT_MEDIA_ID,
                children = listOf(
                    node("one"),
                    node("two"),
                    node("three")
                )
            )
        )

        val children = catalog.children(
            parentId = LibraryItems.ROOT_MEDIA_ID,
            page = 1,
            pageSize = 2
        )

        assertEquals(listOf("three"), children.map { it.mediaId })
    }

    @Test
    fun `invalid page or unknown parent returns no children`() {
        val catalog = BambooMediaLibraryCatalog(
            source = FixedCatalogSource(
                parentId = LibraryItems.ROOT_MEDIA_ID,
                children = listOf(node("one"))
            )
        )

        assertEquals(emptyList<String>(), catalog.children(LibraryItems.ROOT_MEDIA_ID, -1, 10).map { it.mediaId })
        assertEquals(emptyList<String>(), catalog.children(LibraryItems.ROOT_MEDIA_ID, 0, 0).map { it.mediaId })
        assertEquals(emptyList<String>(), catalog.children("unknown", 0, 10).map { it.mediaId })
    }

    @Test
    fun `placeholder source exposes stable root categories`() {
        val catalog = BambooMediaLibraryCatalog(source = PlaceholderBambooCatalogSource)

        val children = catalog.children(
            parentId = LibraryItems.ROOT_MEDIA_ID,
            page = 0,
            pageSize = 10
        )

        assertEquals(
            listOf(
                "pandawave.library.saved",
                "pandawave.library.downloads",
                "pandawave.library.recent"
            ),
            children.map { it.mediaId }
        )
        assertTrue(children.all { item -> item.mediaMetadata.isBrowsable == true })
        assertTrue(children.none { item -> item.mediaMetadata.isPlayable == true })
    }

    @Test
    fun `search delegates to source`() {
        val catalog = BambooMediaLibraryCatalog(
            source = object : BambooCatalogSource {
                override fun browse(parentId: String, offset: Int, limit: Int): CatalogPage = CatalogPage.empty()
                override fun search(query: String, offset: Int, limit: Int): CatalogPage = if (query == "test") {
                    CatalogPage(
                        operationId = "catalog-search-1",
                        generation = 1L,
                        totalCount = 1,
                        items = listOf(node("result")),
                        hasNextPage = false
                    )
                } else {
                    CatalogPage.empty()
                }
                override fun item(mediaId: String): BambooCatalogNode? = null
            }
        )

        val results = catalog.search("test", 0, 10)

        assertEquals(1, results.size)
        assertEquals("result", results[0].mediaId)
    }

    @Test
    fun `search page reports count without materializing an unbounded page`() {
        val catalog = BambooMediaLibraryCatalog(
            source = object : BambooCatalogSource {
                override fun browse(parentId: String, offset: Int, limit: Int): CatalogPage = CatalogPage.empty()
                override fun search(query: String, offset: Int, limit: Int): CatalogPage = CatalogPage(
                    operationId = "catalog-search-1",
                    generation = 1L,
                    totalCount = 2,
                    items = listOf(node("one"), node("two")).paged(offset = offset, limit = limit),
                    hasNextPage = offset + limit < 2
                )
                override fun item(mediaId: String): BambooCatalogNode? = null
            }
        )

        val first = catalog.searchPage("q", page = 0, pageSize = 1)
        val second = catalog.search("q", page = 1, pageSize = 1)

        assertEquals(1, first.items.size)
        assertEquals("one", first.items.single().mediaId)
        assertTrue(first.hasNextPage)
        assertEquals(listOf("two"), second.map { it.mediaId })
    }

    @Test
    fun `engine source dispatches browse and search through the gateway not the playback bridge`() {
        val repository = CatalogRecordingPlaybackRepository()
        val engineGateway = CatalogRecordingEngineGateway()
        val catalog = BambooMediaLibraryCatalog(
            source = EngineBambooCatalogSource(engineGateway = engineGateway)
        )

        val rootChildren = catalog.children(LibraryItems.ROOT_MEDIA_ID, page = 0, pageSize = 10)
        catalog.search("Rust", page = 0, pageSize = 10)

        assertEquals(
            listOf(
                "pandawave.library.saved",
                "pandawave.library.downloads",
                "pandawave.library.recent"
            ),
            rootChildren.map { item -> item.mediaId }
        )
        assertEquals(
            listOf(EngineCommand.TYPE_BROWSE, EngineCommand.TYPE_SEARCH),
            engineGateway.commands.map(EngineCommand::type)
        )
        assertEquals(emptyList<BambooPlaybackIntent>(), repository.intents)
    }

    @Test
    fun `engine source reads the dispatch snapshot not a stale predispatch buffer`() {
        val staleBrowse = EngineCatalogItem(
            mediaId = "stale-album",
            title = "Stale Album",
            itemType = EngineCatalogItem.TYPE_ALBUM
        )
        val freshBrowse = EngineCatalogItem(
            mediaId = "fresh-album",
            title = "Fresh Album",
            artist = "PandaWave",
            itemType = EngineCatalogItem.TYPE_ALBUM
        )
        val staleSearch = EngineCatalogItem(
            mediaId = "stale-track",
            title = "Stale Track",
            itemType = EngineCatalogItem.TYPE_TRACK
        )
        val freshSearch = EngineCatalogItem(
            mediaId = "fresh-track",
            title = "Fresh Track",
            itemType = EngineCatalogItem.TYPE_TRACK
        )
        val engineGateway = CatalogRecordingEngineGateway(
            snapshot = EngineSnapshot.idle(nowMillis = 1L).copy(
                browseResultsCount = 1,
                searchResultsCount = 1
            ),
            staleBrowseResults = listOf(staleBrowse),
            staleSearchResults = listOf(staleSearch),
            browseResults = listOf(freshBrowse),
            searchResults = listOf(freshSearch)
        )
        val catalog = BambooMediaLibraryCatalog(
            source = EngineBambooCatalogSource(engineGateway = engineGateway)
        )

        val browseChildren = catalog.children("engine.parent", page = 0, pageSize = 10)
        val searchResults = catalog.search("Bamboo", page = 0, pageSize = 10)

        assertEquals(listOf("fresh-album"), browseChildren.map { item -> item.mediaId })
        assertEquals(listOf("fresh-track"), searchResults.map { item -> item.mediaId })
        assertEquals(
            listOf(EngineCommand.TYPE_BROWSE, EngineCommand.TYPE_SEARCH),
            engineGateway.commands.map(EngineCommand::type)
        )
    }

    @Test
    fun `engine source projects non root browse and search results from gateway`() {
        val engineGateway = CatalogRecordingEngineGateway(
            snapshot = EngineSnapshot.idle(nowMillis = 1L),
            browseResults = listOf(
                EngineCatalogItem(
                    mediaId = "album-1",
                    title = "Forest Drive",
                    artist = "PandaWave",
                    artworkUri = "content://pandawave/art/album-1",
                    itemType = EngineCatalogItem.TYPE_ALBUM
                )
            ),
            searchResults = listOf(
                EngineCatalogItem(
                    mediaId = "track-1",
                    title = "Bamboo Radio",
                    artist = "PandaWave",
                    album = "Canopy Sessions",
                    artworkUri = "content://pandawave/art/track-1",
                    itemType = EngineCatalogItem.TYPE_TRACK
                )
            )
        )
        val catalog = BambooMediaLibraryCatalog(
            source = EngineBambooCatalogSource(engineGateway = engineGateway)
        )

        val browseChildren = catalog.children("engine.parent", page = 0, pageSize = 10)
        val searchResults = catalog.search("Bamboo", page = 0, pageSize = 10)

        assertEquals(listOf("album-1"), browseChildren.map { item -> item.mediaId })
        assertEquals("Forest Drive", browseChildren.single().mediaMetadata.title.toString())
        assertEquals("PandaWave", browseChildren.single().mediaMetadata.subtitle.toString())
        assertEquals("PandaWave", browseChildren.single().mediaMetadata.artist)
        assertEquals(
            androidx.media3.common.MediaMetadata.MEDIA_TYPE_ALBUM,
            browseChildren.single().mediaMetadata.mediaType
        )
        assertFalse(browseChildren.single().mediaMetadata.isPlayable == true)
        assertTrue(browseChildren.single().mediaMetadata.isBrowsable == true)
        assertEquals(listOf("track-1"), searchResults.map { item -> item.mediaId })
        assertEquals("Bamboo Radio", searchResults.single().mediaMetadata.title.toString())
        assertEquals("PandaWave - Canopy Sessions", searchResults.single().mediaMetadata.subtitle.toString())
        assertEquals("PandaWave", searchResults.single().mediaMetadata.artist)
        assertEquals("Canopy Sessions", searchResults.single().mediaMetadata.albumTitle)
        assertEquals(
            androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC,
            searchResults.single().mediaMetadata.mediaType
        )
        assertTrue(searchResults.single().mediaMetadata.isPlayable == true)
        assertFalse(searchResults.single().mediaMetadata.isBrowsable == true)
    }

    @Test
    fun `engine source appends bounded history pages without exposing page keys`() {
        val engineGateway = CatalogRecordingEngineGateway(
            snapshot = EngineSnapshot.idle(nowMillis = 1L).copy(
                authState = authenticatedAuthState(),
                historyGeneration = 5L,
                historyEntriesCount = 1,
                hasHistoryNextPage = true
            ),
            historyResults = listOf(
                EngineHistoryItem(
                    historyId = "history-1",
                    mediaId = "track-1",
                    title = "First play",
                    artist = "PandaWave",
                    album = "Road",
                    artworkUri = "content://pandawave/art/track-1",
                    playedAtEpochMillis = 1_000L,
                    listenedDurationMillis = 90_000L,
                    completionRatio = 0.8F,
                    playable = true
                ),
                EngineHistoryItem(
                    historyId = "history-2",
                    mediaId = "track-2",
                    title = "Second play",
                    artist = "PandaWave",
                    album = null,
                    artworkUri = null,
                    playedAtEpochMillis = 2_000L,
                    listenedDurationMillis = 120_000L,
                    completionRatio = 1F,
                    playable = true
                )
            )
        )
        val catalog = BambooMediaLibraryCatalog(
            source = EngineBambooCatalogSource(engineGateway = engineGateway)
        )

        val first = catalog.children(LibraryItems.HISTORY_MEDIA_ID, page = 0, pageSize = 1)
        val second = catalog.children(LibraryItems.HISTORY_MEDIA_ID, page = 1, pageSize = 1)

        assertEquals(listOf("track-1"), first.map { it.mediaId })
        assertEquals(listOf("track-2"), second.map { it.mediaId })
        assertEquals(
            listOf(EngineCommand.TYPE_LIST_HISTORY, EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE),
            engineGateway.commands.map(EngineCommand::type)
        )
        assertEquals(null, engineGateway.commands.last().payload)
    }

    @Test
    fun `engine source filters unavailable history rows before exposing media items`() {
        val engineGateway = CatalogRecordingEngineGateway(
            snapshot = EngineSnapshot.idle(nowMillis = 1L).copy(
                authState = authenticatedAuthState(),
                historyGeneration = 5L,
                historyEntriesCount = 1,
                hasHistoryNextPage = true
            ),
            historyResults = listOf(
                historyItem(historyId = "history-unplayable", mediaId = "track-unplayable", playable = false),
                historyItem(historyId = "history-missing-id", mediaId = null),
                historyItem(historyId = "history-blank-id", mediaId = " "),
                historyItem(historyId = "history-playable", mediaId = "track-playable")
            )
        )
        val catalog = BambooMediaLibraryCatalog(
            source = EngineBambooCatalogSource(engineGateway = engineGateway)
        )

        val children = catalog.children(LibraryItems.HISTORY_MEDIA_ID, page = 0, pageSize = 10)

        assertEquals(listOf("track-playable"), children.map { it.mediaId })
        assertTrue(children.single().mediaMetadata.isPlayable == true)
    }

    @Test
    fun `engine source refreshes history cache when generation changes`() {
        val engineGateway = CatalogRecordingEngineGateway(
            snapshot = EngineSnapshot.idle(nowMillis = 1L).copy(
                authState = authenticatedAuthState(),
                historyGeneration = 1L,
                historyEntriesCount = 1
            ),
            historyResults = listOf(historyItem(historyId = "history-old", mediaId = "track-old"))
        )
        val catalog = BambooMediaLibraryCatalog(
            source = EngineBambooCatalogSource(engineGateway = engineGateway)
        )

        val firstPage = catalog.children(LibraryItems.HISTORY_MEDIA_ID, page = 0, pageSize = 1)
        engineGateway.replaceHistory(
            snapshot = EngineSnapshot.idle(nowMillis = 2L).copy(
                authState = authenticatedAuthState(),
                historyGeneration = 2L,
                historyEntriesCount = 1,
                hasHistoryNextPage = true
            ),
            historyResults = listOf(
                historyItem(historyId = "history-new-1", mediaId = "track-new-1"),
                historyItem(historyId = "history-new-2", mediaId = "track-new-2")
            )
        )
        val secondPage = catalog.children(LibraryItems.HISTORY_MEDIA_ID, page = 1, pageSize = 1)

        assertEquals(listOf("track-old"), firstPage.map { it.mediaId })
        assertEquals(listOf("track-new-2"), secondPage.map { it.mediaId })
        assertEquals(
            listOf(
                EngineCommand.TYPE_LIST_HISTORY,
                EngineCommand.TYPE_LIST_HISTORY,
                EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE
            ),
            engineGateway.commands.map(EngineCommand::type)
        )
    }
}

private object EmptyCatalogSource : BambooCatalogSource {
    override fun browse(parentId: String, offset: Int, limit: Int): CatalogPage = CatalogPage.empty()
    override fun search(query: String, offset: Int, limit: Int): CatalogPage = CatalogPage.empty()
    override fun item(mediaId: String): BambooCatalogNode? = null
}

private class FixedCatalogSource(private val parentId: String, private val children: List<BambooCatalogNode>) :
    BambooCatalogSource {
    override fun browse(parentId: String, offset: Int, limit: Int): CatalogPage = when (parentId) {
        this.parentId -> CatalogPage(
            operationId = null,
            generation = 1L,
            totalCount = children.size,
            items = children.paged(offset = offset, limit = limit),
            hasNextPage = offset + limit < children.size
        )

        else -> CatalogPage.empty()
    }

    override fun search(query: String, offset: Int, limit: Int): CatalogPage = CatalogPage.empty()
    override fun item(mediaId: String): BambooCatalogNode? = children.firstOrNull { node -> node.mediaId == mediaId }
}

private object PlaceholderBambooCatalogSource : BambooCatalogSource {
    private val root = listOf(
        BambooCatalogNode("pandawave.library.saved", "Saved music", isBrowsable = true, isPlayable = false),
        BambooCatalogNode("pandawave.library.downloads", "Downloads", isBrowsable = true, isPlayable = false),
        BambooCatalogNode("pandawave.library.recent", "Recently played", isBrowsable = true, isPlayable = false)
    )

    override fun browse(parentId: String, offset: Int, limit: Int): CatalogPage = if (parentId ==
        LibraryItems.ROOT_MEDIA_ID
    ) {
        CatalogPage(
            operationId = null,
            generation = 1L,
            totalCount = root.size,
            items = root.paged(offset = offset, limit = limit),
            hasNextPage = offset + limit < root.size
        )
    } else {
        CatalogPage.empty()
    }

    override fun search(query: String, offset: Int, limit: Int): CatalogPage = CatalogPage.empty()
    override fun item(mediaId: String): BambooCatalogNode? = root.firstOrNull { node -> node.mediaId == mediaId }
}

private fun node(id: String): BambooCatalogNode = BambooCatalogNode(
    mediaId = id,
    title = id,
    isBrowsable = true,
    isPlayable = false
)

private fun authenticatedAuthState(): EngineAuthState = EngineAuthState(
    state = EngineAuthState.AUTHENTICATED,
    account = EngineAccount("account-1", "driver@example.com", "active", 1L),
    session = EngineAuthSession("session-1", "PandaWave", 1L, 1L, 10_000L, true)
)

private class CatalogRecordingPlaybackRepository : BambooPlaybackRepository {
    private val mutableState = MutableStateFlow(BambooPlaybackState())

    val intents = mutableListOf<BambooPlaybackIntent>()

    override val state: StateFlow<BambooPlaybackState> = mutableState

    override fun start() = Unit

    override fun dispatch(intent: BambooPlaybackIntent) {
        intents += intent
    }

    override fun observe(listener: (BambooPlaybackState) -> Unit): AutoCloseable {
        listener(state.value)
        return AutoCloseable { }
    }

    override fun observeEffects(listener: (List<EngineEffect>) -> Unit): AutoCloseable = AutoCloseable { }

    override fun close() = Unit
}

private fun historyItem(historyId: String, mediaId: String?, playable: Boolean = true) = EngineHistoryItem(
    historyId = historyId,
    mediaId = mediaId,
    title = "Played $historyId",
    artist = "PandaWave",
    album = "Road",
    artworkUri = "content://pandawave/art/$historyId",
    playedAtEpochMillis = 1_000L,
    listenedDurationMillis = 90_000L,
    completionRatio = 0.75F,
    playable = playable
)

private class CatalogRecordingEngineGateway(
    snapshot: EngineSnapshot = EngineSnapshot.idle(nowMillis = 1L),
    private val browseResults: List<EngineCatalogItem> = emptyList(),
    private val searchResults: List<EngineCatalogItem> = emptyList(),
    private val staleBrowseResults: List<EngineCatalogItem> = emptyList(),
    private val staleSearchResults: List<EngineCatalogItem> = emptyList(),
    private var historyResults: List<EngineHistoryItem> = emptyList()
) : EngineGateway {
    private var currentSnapshot = snapshot
    private var historyOffset = 0
    private var liveBrowse = emptyList<EngineCatalogItem>()
    private var liveSearch = emptyList<EngineCatalogItem>()
    private var browseDispatched = false
    private var searchDispatched = false
    val commands = mutableListOf<EngineCommand>()

    fun replaceHistory(snapshot: EngineSnapshot, historyResults: List<EngineHistoryItem>) {
        currentSnapshot = snapshot
        this.historyResults = historyResults
        historyOffset = 0
    }

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun browseResult(index: Int): EngineCatalogItem? = visibleBrowse().getOrNull(index)

    override fun searchResult(index: Int): EngineCatalogItem? = visibleSearch().getOrNull(index)
    override fun historyEntry(index: Int): EngineHistoryItem? = historyResults.getOrNull(historyOffset + index)

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commands += command
        if (command.type == EngineCommand.TYPE_BROWSE) {
            browseDispatched = true
            liveBrowse = browseResults
            currentSnapshot = currentSnapshot.copy(browseResultsCount = liveBrowse.size)
        }
        if (command.type == EngineCommand.TYPE_SEARCH) {
            searchDispatched = true
            liveSearch = searchResults
            currentSnapshot = currentSnapshot.copy(searchResultsCount = liveSearch.size)
        }
        if (command.type == EngineCommand.TYPE_LOAD_NEXT_CATALOG_PAGE) {
            if (command.payload?.contains("browse") == true || liveBrowse.isNotEmpty()) {
                liveBrowse = browseResults
                currentSnapshot = currentSnapshot.copy(browseResultsCount = liveBrowse.size)
            }
            if (command.payload?.contains("search") == true || liveSearch.isNotEmpty()) {
                liveSearch = searchResults
                currentSnapshot = currentSnapshot.copy(searchResultsCount = liveSearch.size)
            }
        }
        if (command.type == EngineCommand.TYPE_LIST_HISTORY) {
            historyOffset = 0
            currentSnapshot = currentSnapshot.copy(
                historyEntriesCount = minOf(1, historyResults.size),
                hasHistoryNextPage = historyResults.size > 1
            )
        }
        if (command.type == EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE) {
            historyOffset = minOf(historyOffset + currentSnapshot.historyEntriesCount, historyResults.size)
            currentSnapshot = currentSnapshot.copy(
                historyEntriesCount = (historyResults.size - historyOffset).coerceIn(0, 1),
                hasHistoryNextPage = historyOffset + 1 < historyResults.size
            )
        }
        return EngineDispatchResult(
            snapshot = currentSnapshot,
            event = EngineEvent(type = EngineEvent.TYPE_COMMAND_APPLIED, message = "catalog-${command.type}")
        )
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult = EngineDispatchResult(
        snapshot = currentSnapshot,
        event = EngineEvent(type = EngineEvent.TYPE_PLATFORM_EVENT_APPLIED, message = event.type)
    )

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        listener(currentSnapshot)
        return AutoCloseable { }
    }

    override fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable = AutoCloseable { }

    private fun visibleBrowse(): List<EngineCatalogItem> = if (browseDispatched) liveBrowse else staleBrowseResults

    private fun visibleSearch(): List<EngineCatalogItem> = if (searchDispatched) liveSearch else staleSearchResults
}

private fun <T> List<T>.paged(offset: Int, limit: Int): List<T> = when {
    offset < 0 || limit < 1 || offset >= size -> emptyList()
    else -> subList(offset, minOf(offset + limit, size))
}
