package com.adrianrusu.pandawave.core.media.adapter.playback

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
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
                override fun children(parentId: String, page: Int, pageSize: Int): List<BambooCatalogNode> =
                    emptyList()

                override fun search(query: String): List<BambooCatalogNode> = if (query == "test") {
                    listOf(node("result"))
                } else {
                    emptyList()
                }
            }
        )

        val results = catalog.search("test", 0, 10)

        assertEquals(1, results.size)
        assertEquals("result", results[0].mediaId)
    }

    @Test
    fun `engine source dispatches browse and search commands`() {
        val repository = CatalogRecordingPlaybackRepository()
        val engineGateway = CatalogRecordingEngineGateway()
        val bridge = Media3PlaybackEngineBridge(
            playbackRepository = repository,
            telemetryLogger = testTelemetryLogger()
        )
        val catalog = BambooMediaLibraryCatalog(
            source = EngineBambooCatalogSource(
                playbackBridge = bridge,
                engineGateway = engineGateway
            )
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
            listOf<BambooPlaybackIntent>(
                BambooPlaybackIntent.BrowseCatalog(parentId = LibraryItems.ENGINE_ROOT_PARENT_ID),
                BambooPlaybackIntent.SearchCatalog(query = "Rust")
            ),
            repository.intents
        )
    }

    @Test
    fun `engine source projects non root browse and search results from gateway`() {
        val repository = CatalogRecordingPlaybackRepository()
        val engineGateway = CatalogRecordingEngineGateway(
            snapshot = EngineSnapshot.idle(nowMillis = 1L).copy(
                browseResultsCount = 1,
                searchResultsCount = 1
            ),
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
        val bridge = Media3PlaybackEngineBridge(
            playbackRepository = repository,
            telemetryLogger = testTelemetryLogger()
        )
        val catalog = BambooMediaLibraryCatalog(
            source = EngineBambooCatalogSource(
                playbackBridge = bridge,
                engineGateway = engineGateway
            )
        )

        val browseChildren = catalog.children("engine.parent", page = 0, pageSize = 10)
        val searchResults = catalog.search("Bamboo", page = 0, pageSize = 10)

        assertEquals(listOf("album-1"), browseChildren.map { item -> item.mediaId })
        assertEquals("Forest Drive", browseChildren.single().mediaMetadata.title.toString())
        assertEquals("PandaWave", browseChildren.single().mediaMetadata.subtitle.toString())
        assertFalse(browseChildren.single().mediaMetadata.isPlayable == true)
        assertTrue(browseChildren.single().mediaMetadata.isBrowsable == true)
        assertEquals(listOf("track-1"), searchResults.map { item -> item.mediaId })
        assertEquals("Bamboo Radio", searchResults.single().mediaMetadata.title.toString())
        assertEquals("PandaWave - Canopy Sessions", searchResults.single().mediaMetadata.subtitle.toString())
        assertTrue(searchResults.single().mediaMetadata.isPlayable == true)
        assertFalse(searchResults.single().mediaMetadata.isBrowsable == true)
    }

    @Test
    fun `engine source appends bounded history pages without exposing page keys`() {
        val repository = CatalogRecordingPlaybackRepository()
        val engineGateway = CatalogRecordingEngineGateway(
            snapshot = EngineSnapshot.idle(nowMillis = 1L).copy(
                authState = authenticatedAuthState(),
                historyGeneration = 5L,
                historyEntriesCount = 1,
                hasHistoryNextPage = true,
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
                    playable = true,
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
                    playable = true,
                ),
            ),
        )
        val catalog = BambooMediaLibraryCatalog(
            source = EngineBambooCatalogSource(
                playbackBridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger()),
                engineGateway = engineGateway,
            ),
        )

        val first = catalog.children(LibraryItems.HISTORY_MEDIA_ID, page = 0, pageSize = 1)
        val second = catalog.children(LibraryItems.HISTORY_MEDIA_ID, page = 1, pageSize = 1)

        assertEquals(listOf("track-1"), first.map { it.mediaId })
        assertEquals(listOf("track-2"), second.map { it.mediaId })
        assertEquals(
            listOf(EngineCommand.TYPE_LIST_HISTORY, EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE),
            engineGateway.commands.map(EngineCommand::type),
        )
        assertEquals(null, engineGateway.commands.last().payload)
    }

    @Test
    fun `engine source filters unavailable history rows before exposing media items`() {
        val repository = CatalogRecordingPlaybackRepository()
        val engineGateway = CatalogRecordingEngineGateway(
            snapshot = EngineSnapshot.idle(nowMillis = 1L).copy(
                authState = authenticatedAuthState(),
                historyGeneration = 5L,
                historyEntriesCount = 1,
                hasHistoryNextPage = true,
            ),
            historyResults = listOf(
                historyItem(historyId = "history-unplayable", mediaId = "track-unplayable", playable = false),
                historyItem(historyId = "history-missing-id", mediaId = null),
                historyItem(historyId = "history-blank-id", mediaId = " "),
                historyItem(historyId = "history-playable", mediaId = "track-playable"),
            ),
        )
        val catalog = BambooMediaLibraryCatalog(
            source = EngineBambooCatalogSource(
                playbackBridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger()),
                engineGateway = engineGateway,
            ),
        )

        val children = catalog.children(LibraryItems.HISTORY_MEDIA_ID, page = 0, pageSize = 10)

        assertEquals(listOf("track-playable"), children.map { it.mediaId })
        assertTrue(children.single().mediaMetadata.isPlayable == true)
    }

    @Test
    fun `engine source refreshes history cache when generation changes`() {
        val repository = CatalogRecordingPlaybackRepository()
        val engineGateway = CatalogRecordingEngineGateway(
            snapshot = EngineSnapshot.idle(nowMillis = 1L).copy(
                authState = authenticatedAuthState(),
                historyGeneration = 1L,
                historyEntriesCount = 1,
            ),
            historyResults = listOf(historyItem(historyId = "history-old", mediaId = "track-old")),
        )
        val catalog = BambooMediaLibraryCatalog(
            source = EngineBambooCatalogSource(
                playbackBridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger()),
                engineGateway = engineGateway,
            ),
        )

        val firstPage = catalog.children(LibraryItems.HISTORY_MEDIA_ID, page = 0, pageSize = 1)
        engineGateway.replaceHistory(
            snapshot = EngineSnapshot.idle(nowMillis = 2L).copy(
                authState = authenticatedAuthState(),
                historyGeneration = 2L,
                historyEntriesCount = 1,
                hasHistoryNextPage = true,
            ),
            historyResults = listOf(
                historyItem(historyId = "history-new-1", mediaId = "track-new-1"),
                historyItem(historyId = "history-new-2", mediaId = "track-new-2"),
            ),
        )
        val secondPage = catalog.children(LibraryItems.HISTORY_MEDIA_ID, page = 1, pageSize = 1)

        assertEquals(listOf("track-old"), firstPage.map { it.mediaId })
        assertEquals(listOf("track-new-2"), secondPage.map { it.mediaId })
        assertEquals(
            listOf(
                EngineCommand.TYPE_LIST_HISTORY,
                EngineCommand.TYPE_LIST_HISTORY,
                EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE,
            ),
            engineGateway.commands.map(EngineCommand::type),
        )
    }
}

private object EmptyCatalogSource : BambooCatalogSource {
    override fun children(parentId: String, page: Int, pageSize: Int): List<BambooCatalogNode> = emptyList()
    override fun search(query: String): List<BambooCatalogNode> = emptyList()
}

private class FixedCatalogSource(private val parentId: String, private val children: List<BambooCatalogNode>) :
    BambooCatalogSource {
    override fun children(parentId: String, page: Int, pageSize: Int): List<BambooCatalogNode> = when (parentId) {
        this.parentId -> children.paged(page = page, pageSize = pageSize)
        else -> emptyList()
    }

    override fun search(query: String): List<BambooCatalogNode> = emptyList()
}

private object PlaceholderBambooCatalogSource : BambooCatalogSource {
    override fun children(parentId: String, page: Int, pageSize: Int): List<BambooCatalogNode> = if (parentId == LibraryItems.ROOT_MEDIA_ID) {
        listOf(
            BambooCatalogNode("pandawave.library.saved", "Saved music", isBrowsable = true, isPlayable = false),
            BambooCatalogNode("pandawave.library.downloads", "Downloads", isBrowsable = true, isPlayable = false),
            BambooCatalogNode("pandawave.library.recent", "Recently played", isBrowsable = true, isPlayable = false)
        ).paged(page = page, pageSize = pageSize)
    } else {
        emptyList()
    }

    override fun search(query: String): List<BambooCatalogNode> = emptyList()
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
    session = EngineAuthSession("session-1", "PandaWave", 1L, 1L, 10_000L, true),
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

private fun testTelemetryLogger(): TelemetryLogger = TelemetryLogger(
    sink = TelemetrySink { },
    clock = { 42L }
)

private fun historyItem(
    historyId: String,
    mediaId: String?,
    playable: Boolean = true,
) = EngineHistoryItem(
    historyId = historyId,
    mediaId = mediaId,
    title = "Played $historyId",
    artist = "PandaWave",
    album = "Road",
    artworkUri = "content://pandawave/art/$historyId",
    playedAtEpochMillis = 1_000L,
    listenedDurationMillis = 90_000L,
    completionRatio = 0.75F,
    playable = playable,
)

private class CatalogRecordingEngineGateway(
    snapshot: EngineSnapshot = EngineSnapshot.idle(nowMillis = 1L),
    private val browseResults: List<EngineCatalogItem> = emptyList(),
    private val searchResults: List<EngineCatalogItem> = emptyList(),
    private var historyResults: List<EngineHistoryItem> = emptyList(),
) : EngineGateway {
    private var currentSnapshot = snapshot
    private var historyOffset = 0
    val commands = mutableListOf<EngineCommand>()

    fun replaceHistory(snapshot: EngineSnapshot, historyResults: List<EngineHistoryItem>) {
        currentSnapshot = snapshot
        this.historyResults = historyResults
        historyOffset = 0
    }

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun browseResult(index: Int): EngineCatalogItem? = browseResults.getOrNull(index)

    override fun searchResult(index: Int): EngineCatalogItem? = searchResults.getOrNull(index)
    override fun historyEntry(index: Int): EngineHistoryItem? = historyResults.getOrNull(historyOffset + index)

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commands += command
        if (command.type == EngineCommand.TYPE_LIST_HISTORY) {
            historyOffset = 0
            currentSnapshot = currentSnapshot.copy(
                historyEntriesCount = minOf(1, historyResults.size),
                hasHistoryNextPage = historyResults.size > 1,
            )
        }
        if (command.type == EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE) {
            historyOffset = minOf(historyOffset + currentSnapshot.historyEntriesCount, historyResults.size)
            currentSnapshot = currentSnapshot.copy(
                historyEntriesCount = (historyResults.size - historyOffset).coerceIn(0, 1),
                hasHistoryNextPage = historyOffset + 1 < historyResults.size,
            )
        }
        return EngineDispatchResult(
            snapshot = currentSnapshot,
            event = EngineEvent(type = EngineEvent.TYPE_COMMAND_APPLIED, message = command.type)
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
}

private fun <T> List<T>.paged(page: Int, pageSize: Int): List<T> {
    val fromIndex = page * pageSize
    return when {
        page < 0 || pageSize < 1 -> emptyList()
        fromIndex >= size -> emptyList()
        else -> subList(fromIndex, minOf(fromIndex + pageSize, size))
    }
}
