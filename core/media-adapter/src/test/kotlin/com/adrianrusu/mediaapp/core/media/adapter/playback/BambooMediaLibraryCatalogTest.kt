package com.adrianrusu.mediaapp.core.media.adapter.playback

import com.adrianrusu.mediaapp.core.model.catalog.BambooCatalogNode
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackIntent
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackState
import com.adrianrusu.mediaapp.core.telemetry.TelemetryLogger
import com.adrianrusu.mediaapp.core.telemetry.TelemetrySink
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
                override fun children(parentId: String): List<BambooCatalogNode> = emptyList()
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
        val bridge = Media3PlaybackEngineBridge(
            playbackRepository = repository,
            telemetryLogger = testTelemetryLogger()
        )
        val catalog = BambooMediaLibraryCatalog(source = EngineBambooCatalogSource(bridge))

        catalog.children(LibraryItems.ROOT_MEDIA_ID, page = 0, pageSize = 10)
        catalog.search("Rust", page = 0, pageSize = 10)

        assertEquals(
            listOf<BambooPlaybackIntent>(
                BambooPlaybackIntent.BrowseCatalog(parentId = LibraryItems.ENGINE_ROOT_PARENT_ID),
                BambooPlaybackIntent.SearchCatalog(query = "Rust")
            ),
            repository.intents
        )
    }
}

private object EmptyCatalogSource : BambooCatalogSource {
    override fun children(parentId: String): List<BambooCatalogNode> = emptyList()
    override fun search(query: String): List<BambooCatalogNode> = emptyList()
}

private class FixedCatalogSource(private val parentId: String, private val children: List<BambooCatalogNode>) :
    BambooCatalogSource {
    override fun children(parentId: String): List<BambooCatalogNode> = when (parentId) {
        this.parentId -> children
        else -> emptyList()
    }

    override fun search(query: String): List<BambooCatalogNode> = emptyList()
}

private object PlaceholderBambooCatalogSource : BambooCatalogSource {
    override fun children(parentId: String): List<BambooCatalogNode> = if (parentId == LibraryItems.ROOT_MEDIA_ID) {
        listOf(
            BambooCatalogNode("pandawave.library.saved", "Saved music", isBrowsable = true, isPlayable = false),
            BambooCatalogNode("pandawave.library.downloads", "Downloads", isBrowsable = true, isPlayable = false),
            BambooCatalogNode("pandawave.library.recent", "Recently played", isBrowsable = true, isPlayable = false)
        )
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

    override fun close() = Unit
}

private fun testTelemetryLogger(): TelemetryLogger = TelemetryLogger(
    sink = TelemetrySink { },
    clock = { 42L }
)
