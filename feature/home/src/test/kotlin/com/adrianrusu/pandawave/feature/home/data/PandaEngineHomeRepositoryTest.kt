package com.adrianrusu.pandawave.feature.home.data

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAccount
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthSession
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PandaEngineHomeRepositoryTest {
    @Test
    fun `start hydrates for you recommendations and discovery from snapshot pages`() {
        val gateway = RecordingHomeGateway(
            snapshot = authenticatedSnapshot(),
            forYou = listOf(catalogItem("for-you-1", "Forest Morning")),
            recommendations = listOf(catalogItem("rec-1", "Night Drive")),
            discovery = listOf(catalogItem("discover-1", "Canopy Mix")),
        )
        val repository = testHomeRepository(gateway)

        repository.start()

        assertEquals(
            listOf(
                EngineCommand.TYPE_LOAD_FOR_YOU_FEED,
                EngineCommand.TYPE_LOAD_RECOMMENDATIONS,
                EngineCommand.TYPE_LOAD_DISCOVERY_FEED,
            ),
            gateway.commands.map(EngineCommand::type),
        )
        assertEquals(listOf("for-you-1"), repository.state.value.forYou.map { it.id })
        assertEquals(listOf("rec-1"), repository.state.value.recommendations.map { it.id })
        assertEquals(listOf("discover-1"), repository.state.value.discovery.map { it.id })
        assertEquals(listOf("Forest Morning"), repository.state.value.forYou.map { it.title })
    }

    @Test
    fun `empty intermediate snapshots keep previously shown feed items`() {
        val gateway = RecordingHomeGateway(
            snapshot = authenticatedSnapshot(),
            forYou = listOf(catalogItem("for-you-1", "Forest Morning")),
            recommendations = listOf(catalogItem("rec-1", "Night Drive")),
            discovery = listOf(catalogItem("discover-1", "Canopy Mix")),
        )
        val repository = testHomeRepository(gateway)
        repository.start()
        gateway.commands.clear()

        gateway.emit(
            authenticatedSnapshot(
                forYouResultsCount = 0,
                recommendationsResultsCount = 0,
                discoveryResultsCount = 0,
                isBusy = true,
            ),
        )

        assertTrue(gateway.commands.isEmpty())
        assertEquals(listOf("for-you-1"), repository.state.value.forYou.map { it.id })
        assertEquals(listOf("rec-1"), repository.state.value.recommendations.map { it.id })
        assertEquals(listOf("discover-1"), repository.state.value.discovery.map { it.id })
    }

    @Test
    fun `completed empty feed command replaces that section`() {
        val gateway = RecordingHomeGateway(
            snapshot = authenticatedSnapshot(),
            forYou = listOf(catalogItem("for-you-1", "Forest Morning")),
            recommendations = listOf(catalogItem("rec-1", "Night Drive")),
        )
        val repository = testHomeRepository(gateway)
        repository.start()
        gateway.forYou = emptyList()
        gateway.commands.clear()

        repository.refresh()

        assertTrue(repository.state.value.forYou.isEmpty())
        assertEquals(listOf("rec-1"), repository.state.value.recommendations.map { it.id })
    }

    @Test
    fun `signed out snapshots clear home feeds`() {
        val gateway = RecordingHomeGateway(
            snapshot = authenticatedSnapshot(),
            forYou = listOf(catalogItem("for-you-1", "Forest Morning")),
        )
        val repository = testHomeRepository(gateway)
        repository.start()

        gateway.emit(EngineSnapshot.idle(2L))

        assertTrue(repository.state.value.forYou.isEmpty())
        assertTrue(repository.state.value.recommendations.isEmpty())
        assertTrue(repository.state.value.discovery.isEmpty())
    }

    @Test
    fun `authenticated snapshots without account keep previously shown feeds`() {
        val gateway = RecordingHomeGateway(
            snapshot = authenticatedSnapshot(),
            forYou = listOf(catalogItem("for-you-1", "Forest Morning")),
        )
        val repository = testHomeRepository(gateway)
        repository.start()

        gateway.emit(
            EngineSnapshot.idle(2L).copy(
                authState = EngineAuthState(EngineAuthState.AUTHENTICATED),
                forYouResultsCount = 0,
            ),
        )

        assertEquals(listOf("for-you-1"), repository.state.value.forYou.map { it.id })
    }
}

private fun testHomeRepository(gateway: EngineGateway) = PandaEngineHomeRepository(
    engineGateway = gateway,
    hydrateExecutor = Executor { it.run() },
)

private fun authenticatedSnapshot(
    forYouResultsCount: Int = 0,
    recommendationsResultsCount: Int = 0,
    discoveryResultsCount: Int = 0,
    isBusy: Boolean = false,
    accountId: String = "account-1",
    sessionId: String = "session-1",
): EngineSnapshot = EngineSnapshot.idle(1L).copy(
    authState = EngineAuthState(
        state = EngineAuthState.AUTHENTICATED,
        account = EngineAccount(accountId, "$accountId@example.com", "active", 1L),
        session = EngineAuthSession(sessionId, "PandaWave", 1L, 1L, 10_000L, true),
    ),
    forYouResultsCount = forYouResultsCount,
    recommendationsResultsCount = recommendationsResultsCount,
    discoveryResultsCount = discoveryResultsCount,
    isBusy = isBusy,
)

private fun catalogItem(mediaId: String, title: String) = EngineCatalogItem(
    mediaId = mediaId,
    title = title,
    artist = "Artist",
    album = "Album",
)

private class RecordingHomeGateway(
    snapshot: EngineSnapshot,
    var forYou: List<EngineCatalogItem> = emptyList(),
    var recommendations: List<EngineCatalogItem> = emptyList(),
    var discovery: List<EngineCatalogItem> = emptyList(),
) : EngineGateway {
    private var current = snapshot
    private val listeners = mutableListOf<(EngineSnapshot) -> Unit>()
    val commands = mutableListOf<EngineCommand>()

    override fun snapshot(): EngineSnapshot = current
    override fun browseResult(index: Int): EngineCatalogItem? = null
    override fun searchResult(index: Int): EngineCatalogItem? = null
    override fun forYouResult(index: Int): EngineCatalogItem? = forYou.getOrNull(index)
    override fun recommendationResult(index: Int): EngineCatalogItem? = recommendations.getOrNull(index)
    override fun discoveryResult(index: Int): EngineCatalogItem? = discovery.getOrNull(index)
    override fun forYouResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        forYou.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))
    override fun recommendationResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        recommendations.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))
    override fun discoveryResultsPage(offset: Int, limit: Int): List<EngineCatalogItem> =
        discovery.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commands += command
        current = when (command.type) {
            EngineCommand.TYPE_LOAD_FOR_YOU_FEED -> current.copy(
                forYouResultsCount = forYou.size,
                isBusy = false,
            )
            EngineCommand.TYPE_LOAD_RECOMMENDATIONS -> current.copy(
                recommendationsResultsCount = recommendations.size,
                isBusy = false,
            )
            EngineCommand.TYPE_LOAD_DISCOVERY_FEED -> current.copy(
                discoveryResultsCount = discovery.size,
                isBusy = false,
            )
            else -> current
        }
        return EngineDispatchResult(
            snapshot = current,
            event = EngineEvent(EngineEvent.TYPE_COMMAND_APPLIED, command.type),
            effects = emptyList<EngineEffect>(),
        )
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult =
        EngineDispatchResult(
            snapshot = current,
            event = EngineEvent(EngineEvent.TYPE_PLATFORM_EVENT_APPLIED, event.type),
        )

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        listeners += listener
        listener(current)
        return AutoCloseable { listeners -= listener }
    }

    fun emit(snapshot: EngineSnapshot) {
        current = snapshot
        listeners.forEach { it(snapshot) }
    }

    override fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable = AutoCloseable { }
}
