package com.adrianrusu.mediaapp.appshell.data

import com.adrianrusu.mediaapp.appshell.domain.AppShellIntent
import com.adrianrusu.mediaapp.appshell.domain.EngineConnectionUiState
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.mediaapp.core.rust.bridge.engine.PandaEngineFactory
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.InProcessEngineGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryAppShellRepositoryTest {
    @Test
    fun playbackIntentDispatchesThroughEngineSnapshot() {
        val repository = InMemoryAppShellRepository(
            uxRestrictionObserver = FakeAutomotiveUxRestrictionObserver(),
            engine = InProcessEngineGateway(PandaEngineFactory.createFake())
        )

        repository.start()

        assertFalse(repository.state.value.miniPlayer.isPlaying)

        repository.dispatch(AppShellIntent.TogglePlayback)

        assertTrue(repository.state.value.miniPlayer.isPlaying)

        repository.dispatch(AppShellIntent.TogglePlayback)

        assertFalse(repository.state.value.miniPlayer.isPlaying)
    }

    @Test
    fun skipIntentsDispatchThroughEngineBoundary() {
        val engine = RecordingEngineGateway()
        val repository = InMemoryAppShellRepository(
            uxRestrictionObserver = FakeAutomotiveUxRestrictionObserver(),
            engine = engine
        )

        repository.start()
        repository.dispatch(AppShellIntent.SkipPrevious)
        repository.dispatch(AppShellIntent.SkipNext)

        assertEquals(
            listOf(
                EngineCommand.TYPE_BOOTSTRAP,
                EngineCommand.TYPE_SKIP_PREVIOUS,
                EngineCommand.TYPE_SKIP_NEXT
            ),
            engine.commandTypes
        )
    }

    @Test
    fun restrictionsAreProjectedIntoMiniPlayerState() {
        val observer = FakeAutomotiveUxRestrictionObserver().copy(
            restrictions = AutomotiveUxRestrictions(
                source = AutomotiveUxRestrictions.Source.AutomotivePlatform,
                requiresDistractionOptimization = true,
                activeRestrictions = 1,
                maxContentDepth = 1,
                maxCumulativeContentItems = 6,
                maxRestrictedStringLength = 24
            )
        )
        val repository = InMemoryAppShellRepository(
            uxRestrictionObserver = observer,
            engine = InProcessEngineGateway(PandaEngineFactory.createFake())
        )

        repository.start()

        assertTrue(repository.state.value.restriction.isRestricted)
        assertTrue(repository.state.value.miniPlayer.isRestricted)
    }

    @Test
    fun pushedEngineSnapshotsUpdateMiniPlayerState() {
        val engine = RecordingEngineGateway()
        val repository = InMemoryAppShellRepository(
            uxRestrictionObserver = FakeAutomotiveUxRestrictionObserver(),
            engine = engine
        )

        repository.start()
        engine.pushSnapshot(
            EngineSnapshot(
                playbackState = EngineSnapshot.PLAYBACK_PLAYING,
                mediaId = "track-1",
                title = "Quiet Cabin",
                artist = "PandaWave",
                userId = null,
                restrictionState = EngineSnapshot.RESTRICTION_UNKNOWN,
                updatedAtEpochMillis = 200
            )
        )

        assertEquals("Quiet Cabin", repository.state.value.miniPlayer.title)
        assertEquals("PandaWave", repository.state.value.miniPlayer.subtitle)
        assertTrue(repository.state.value.miniPlayer.isPlaying)
    }

    @Test
    fun queuedBootstrapProjectsConnectingEngineState() {
        val repository = InMemoryAppShellRepository(
            uxRestrictionObserver = FakeAutomotiveUxRestrictionObserver(),
            engine = RecordingEngineGateway(dispatchEventType = EngineEvent.TYPE_COMMAND_QUEUED)
        )

        repository.start()

        assertEquals(EngineConnectionUiState.Connecting, repository.state.value.engineConnection)
    }

    @Test
    fun appliedBootstrapProjectsReadyEngineState() {
        val repository = InMemoryAppShellRepository(
            uxRestrictionObserver = FakeAutomotiveUxRestrictionObserver(),
            engine = RecordingEngineGateway(dispatchEventType = EngineEvent.TYPE_COMMAND_APPLIED)
        )

        repository.start()

        assertEquals(EngineConnectionUiState.Ready, repository.state.value.engineConnection)
    }

    @Test
    fun pushedEngineEventsUpdateConnectionState() {
        val engine = RecordingEngineGateway()
        val repository = InMemoryAppShellRepository(
            uxRestrictionObserver = FakeAutomotiveUxRestrictionObserver(),
            engine = engine
        )

        repository.start()
        engine.pushEvent(EngineEvent(type = EngineEvent.TYPE_SERVICE_BINDING_DIED, message = null))

        assertEquals(EngineConnectionUiState.Reconnecting, repository.state.value.engineConnection)

        engine.pushEvent(EngineEvent(type = EngineEvent.TYPE_SERVICE_CONNECTED, message = null))

        assertEquals(EngineConnectionUiState.Ready, repository.state.value.engineConnection)

        engine.pushEvent(EngineEvent(type = EngineEvent.TYPE_SERVICE_DISCONNECTED, message = null))

        assertEquals(EngineConnectionUiState.Unavailable, repository.state.value.engineConnection)
    }

    @Test
    fun unavailableEngineStateBlocksPlaybackCommands() {
        val engine = RecordingEngineGateway()
        val repository = InMemoryAppShellRepository(
            uxRestrictionObserver = FakeAutomotiveUxRestrictionObserver(),
            engine = engine
        )

        repository.start()
        engine.pushEvent(EngineEvent(type = EngineEvent.TYPE_SERVICE_DISCONNECTED, message = null))
        repository.dispatch(AppShellIntent.TogglePlayback)
        repository.dispatch(AppShellIntent.SkipNext)

        assertEquals(
            listOf(EngineCommand.TYPE_BOOTSTRAP),
            engine.commandTypes
        )
    }

    @Test
    fun reconnectingEngineStateAllowsQueuedPlaybackCommands() {
        val engine = RecordingEngineGateway()
        val repository = InMemoryAppShellRepository(
            uxRestrictionObserver = FakeAutomotiveUxRestrictionObserver(),
            engine = engine
        )

        repository.start()
        engine.pushEvent(EngineEvent(type = EngineEvent.TYPE_SERVICE_BINDING_DIED, message = null))
        repository.dispatch(AppShellIntent.SkipNext)

        assertEquals(
            listOf(
                EngineCommand.TYPE_BOOTSTRAP,
                EngineCommand.TYPE_SKIP_NEXT
            ),
            engine.commandTypes
        )
    }
}

private data class FakeAutomotiveUxRestrictionObserver(
    val restrictions: AutomotiveUxRestrictions =
        AutomotiveUxRestrictions.unrestricted(
            AutomotiveUxRestrictions.Source.NotAutomotive
        )
) : AutomotiveUxRestrictionObserver {
    override fun current(): AutomotiveUxRestrictions = restrictions

    override fun start(onChanged: (AutomotiveUxRestrictions) -> Unit) {
        onChanged(restrictions)
    }

    override fun close() = Unit
}

private class RecordingEngineGateway(private val dispatchEventType: String = EngineEvent.TYPE_COMMAND_APPLIED) :
    EngineGateway {
    val commandTypes = mutableListOf<String>()
    private var currentSnapshot = EngineSnapshot.idle(nowMillis = 100)
    private val snapshotListeners = mutableSetOf<(EngineSnapshot) -> Unit>()
    private val eventListeners = mutableSetOf<(EngineEvent) -> Unit>()

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commandTypes += command.type
        currentSnapshot = currentSnapshot.copy(updatedAtEpochMillis = currentSnapshot.updatedAtEpochMillis + 1)
        return EngineDispatchResult(
            snapshot = currentSnapshot,
            event = EngineEvent(
                type = dispatchEventType,
                message = command.type
            )
        )
    }

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        snapshotListeners += listener
        listener(currentSnapshot)

        return AutoCloseable {
            snapshotListeners -= listener
        }
    }

    override fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable {
        eventListeners += listener

        return AutoCloseable {
            eventListeners -= listener
        }
    }

    fun pushSnapshot(snapshot: EngineSnapshot) {
        currentSnapshot = snapshot
        snapshotListeners.toList().forEach { listener ->
            listener(snapshot)
        }
    }

    fun pushEvent(event: EngineEvent) {
        eventListeners.toList().forEach { listener ->
            listener(event)
        }
    }
}
