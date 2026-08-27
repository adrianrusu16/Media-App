package com.adrianrusu.pandawave.feature.search.data

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PandaEngineSearchRepositoryTest {
    @Test
    fun `search dispatches the engine catalog command and projects generation scoped results`() {
        val gateway = RecordingSearchGateway(
            results = listOf(item("track-1"), item("track-2"))
        )
        val repository = PandaEngineSearchRepository(gateway)
        repository.start()

        repository.search("rust")

        assertEquals(listOf(EngineCommand.TYPE_SEARCH), gateway.commands.map(EngineCommand::type))
        assertEquals(
            EngineCommandPayloads.searchCatalog("rust", 20),
            gateway.commands.single().payload
        )
        assertEquals(1L, repository.state.value.generation)
        assertEquals(listOf("track-1", "track-2"), repository.state.value.results.map { it.mediaId })
        assertFalse(repository.state.value.hasNextPage)
    }

    @Test
    fun `a newer search advances generation and replaces prior results`() {
        val gateway = RecordingSearchGateway(
            results = listOf(item("old"))
        )
        val repository = PandaEngineSearchRepository(gateway)
        repository.start()
        repository.search("ab")
        gateway.results = listOf(item("new-1"), item("new-2"))

        repository.search("abc")

        assertEquals(
            listOf(EngineCommand.TYPE_SEARCH, EngineCommand.TYPE_SEARCH),
            gateway.commands.map(EngineCommand::type)
        )
        assertEquals(2L, repository.state.value.generation)
        assertEquals(listOf("new-1", "new-2"), repository.state.value.results.map { it.mediaId })
    }

    @Test
    fun `load next uses the catalog page command for the current generation`() {
        val gateway = RecordingSearchGateway(
            results = List(20) { index -> item("track-$index") },
            nextPageResults = listOf(item("track-20")),
            operationId = "search-op-1"
        )
        val repository = PandaEngineSearchRepository(gateway)
        repository.start()
        repository.search("rust")
        assertTrue(repository.state.value.hasNextPage)
        gateway.commands.clear()

        repository.loadNext()

        assertEquals(
            listOf(EngineCommand.TYPE_LOAD_NEXT_CATALOG_PAGE),
            gateway.commands.map(EngineCommand::type)
        )
        assertEquals(
            EngineCommandPayloads.loadNextCatalogPage("search-op-1"),
            gateway.commands.single().payload
        )
        assertEquals(1L, repository.state.value.generation)
        assertEquals("track-20", repository.state.value.results.last().mediaId)
    }

    @Test
    fun `clear results advances generation so stale pages are ignored`() {
        val gateway = RecordingSearchGateway(results = listOf(item("track-1")))
        val repository = PandaEngineSearchRepository(gateway)
        repository.start()
        repository.search("ab")
        val searchedGeneration = repository.state.value.generation

        repository.clearResults()

        assertTrue(repository.state.value.generation > searchedGeneration)
        assertEquals(emptyList(), repository.state.value.results)
        gateway.commands.clear()
        repository.loadNext()
        assertEquals(emptyList(), gateway.commands)
    }
}

private fun item(mediaId: String) = EngineCatalogItem(
    mediaId = mediaId,
    title = "Title $mediaId",
    artist = "Artist",
    album = "Album",
    itemType = EngineCatalogItem.TYPE_TRACK
)

private class RecordingSearchGateway(
    var results: List<EngineCatalogItem> = emptyList(),
    private val nextPageResults: List<EngineCatalogItem> = emptyList(),
    private val operationId: String = "search-op-1"
) : EngineGateway {
    private var current = EngineSnapshot.idle(1L)
    val commands = mutableListOf<EngineCommand>()

    override fun snapshot(): EngineSnapshot = current
    override fun searchResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        results.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commands += command
        if (command.type == EngineCommand.TYPE_LOAD_NEXT_CATALOG_PAGE) {
            results = results + nextPageResults
        }
        current = current.copy(searchResultsCount = results.size)
        return EngineDispatchResult(
            snapshot = current,
            event = EngineEvent(EngineEvent.TYPE_COMMAND_APPLIED, operationId)
        )
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult = EngineDispatchResult(
        snapshot = current,
        event = EngineEvent(EngineEvent.TYPE_PLATFORM_EVENT_APPLIED, event.type)
    )

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        listener(current)
        return AutoCloseable { }
    }

    override fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable = AutoCloseable { }
}
