package com.adrianrusu.mediaapp.core.playback

import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.mediaapp.core.telemetry.TelemetryEvent
import com.adrianrusu.mediaapp.core.telemetry.TelemetryLogger
import com.adrianrusu.mediaapp.core.telemetry.TelemetrySink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultBambooPlaybackRepositoryTest {
    @Test
    fun startBootstrapsEngineSnapshotAndRestrictionState() {
        val repository = DefaultBambooPlaybackRepository(
            engine = RecordingEngineGateway(
                initialSnapshot = EngineSnapshot(
                    playbackState = EngineSnapshot.PLAYBACK_PAUSED,
                    mediaId = "track-1",
                    title = "Quiet Cabin",
                    artist = "PandaWave",
                    userId = null,
                    restrictionState = EngineSnapshot.RESTRICTION_UNKNOWN,
                    updatedAtEpochMillis = 100L
                )
            ),
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()

        assertEquals("Quiet Cabin", repository.state.value.title)
        assertEquals("PandaWave", repository.state.value.artist)
        assertEquals(BambooPlaybackStatus.Paused, repository.state.value.playbackStatus)
        assertEquals(BambooEngineConnectionUiState.Ready, repository.state.value.engineConnection)
        assertEquals("Standard device", repository.state.value.restriction.label)
    }

    @Test
    fun queuedBootstrapKeepsEngineCommandsDisabled() {
        val engine = RecordingEngineGateway(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 1L),
            dispatchEventType = EngineEvent.TYPE_COMMAND_QUEUED
        )
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        repository.dispatch(BambooPlaybackIntent.TogglePlayback)

        assertFalse(repository.state.value.canDispatchEngineCommands)
        assertEquals(
            listOf(EngineCommand.TYPE_BOOTSTRAP),
            engine.commands.map { it.type }
        )
    }

    @Test
    fun readyEngineAllowsPlaybackAndSkipCommands() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        repository.dispatch(BambooPlaybackIntent.TogglePlayback)
        repository.dispatch(BambooPlaybackIntent.SkipNext)

        assertTrue(repository.state.value.canDispatchEngineCommands)
        assertEquals(
            listOf(
                EngineCommand.TYPE_BOOTSTRAP,
                EngineCommand.TYPE_PLAY,
                EngineCommand.TYPE_SKIP_NEXT
            ),
            engine.commands.map { it.type }
        )
        assertEquals(BambooPlaybackStatus.Playing, repository.state.value.playbackStatus)
    }

    @Test
    fun readyEngineAllowsExplicitPlayPauseCommands() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        repository.dispatch(BambooPlaybackIntent.Play)
        repository.dispatch(BambooPlaybackIntent.Pause)

        assertEquals(
            listOf(
                EngineCommand.TYPE_BOOTSTRAP,
                EngineCommand.TYPE_PLAY,
                EngineCommand.TYPE_PAUSE
            ),
            engine.commands.map { it.type }
        )
        assertEquals(BambooPlaybackStatus.Paused, repository.state.value.playbackStatus)
    }

    @Test
    fun engineEventsUpdateConnectionStateAndGateCommands() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        engine.pushEvent(EngineEvent(type = EngineEvent.TYPE_SERVICE_BINDING_DIED, message = null))
        repository.dispatch(BambooPlaybackIntent.TogglePlayback)

        assertEquals(BambooEngineConnectionUiState.Reconnecting, repository.state.value.engineConnection)
        assertEquals(
            listOf(EngineCommand.TYPE_BOOTSTRAP),
            engine.commands.map { it.type }
        )

        engine.pushEvent(EngineEvent(type = EngineEvent.TYPE_SERVICE_CONNECTED, message = null))
        repository.dispatch(BambooPlaybackIntent.TogglePlayback)

        assertEquals(BambooEngineConnectionUiState.Ready, repository.state.value.engineConnection)
        assertEquals(
            listOf(
                EngineCommand.TYPE_BOOTSTRAP,
                EngineCommand.TYPE_PLAY
            ),
            engine.commands.map { it.type }
        )
    }

    @Test
    fun startAndCloseAreReferenceCounted() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val observer = FakeUxRestrictionObserver(
            restrictions = AutomotiveUxRestrictions.unrestricted(
                AutomotiveUxRestrictions.Source.NotAutomotive
            )
        )
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = observer,
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        repository.start()
        repository.close()
        engine.pushSnapshot(
            EngineSnapshot(
                playbackState = EngineSnapshot.PLAYBACK_PLAYING,
                mediaId = "track-1",
                title = "Still observed",
                artist = "PandaWave",
                userId = null,
                restrictionState = EngineSnapshot.RESTRICTION_UNKNOWN,
                updatedAtEpochMillis = 2L
            )
        )

        assertEquals("Still observed", repository.state.value.title)
        assertEquals(0, observer.closeCount)

        repository.close()
        engine.pushSnapshot(
            EngineSnapshot(
                playbackState = EngineSnapshot.PLAYBACK_PAUSED,
                mediaId = "track-2",
                title = "Not observed",
                artist = "PandaWave",
                userId = null,
                restrictionState = EngineSnapshot.RESTRICTION_UNKNOWN,
                updatedAtEpochMillis = 3L
            )
        )

        assertEquals("Still observed", repository.state.value.title)
        assertEquals(1, observer.closeCount)
    }

    @Test
    fun telemetryRecordsReceivedBlockedAndDispatchedPlaybackIntents() {
        val telemetrySink = RecordingTelemetrySink()
        val engine = RecordingEngineGateway(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 1L),
            dispatchEventType = EngineEvent.TYPE_COMMAND_QUEUED
        )
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger(telemetrySink)
        )

        repository.start()
        repository.dispatch(BambooPlaybackIntent.Play)
        engine.pushEvent(EngineEvent(type = EngineEvent.TYPE_SERVICE_CONNECTED, message = null))
        repository.dispatch(BambooPlaybackIntent.SkipNext)

        assertEquals(
            listOf(
                PlaybackTelemetryEvents.INTENT_RECEIVED,
                PlaybackTelemetryEvents.INTENT_BLOCKED,
                PlaybackTelemetryEvents.INTENT_RECEIVED,
                PlaybackTelemetryEvents.ENGINE_COMMAND_DISPATCHED
            ),
            telemetrySink.events.map { it.name }
        )
        assertEquals("play", telemetrySink.events[0].attributes["intent"])
        assertEquals("Connecting", telemetrySink.events[1].attributes["engine_status"])
        assertEquals("skip_next", telemetrySink.events[3].attributes["intent"])
        assertEquals(EngineCommand.TYPE_SKIP_NEXT, telemetrySink.events[3].attributes["command_type"])
    }
}

private class FakeUxRestrictionObserver(private val restrictions: AutomotiveUxRestrictions) :
    AutomotiveUxRestrictionObserver {
    var closeCount = 0

    override fun current(): AutomotiveUxRestrictions = restrictions

    override fun start(onChanged: (AutomotiveUxRestrictions) -> Unit) {
        onChanged(restrictions)
    }

    override fun close() {
        closeCount += 1
    }
}

private class RecordingEngineGateway(
    initialSnapshot: EngineSnapshot,
    private val dispatchEventType: String = EngineEvent.TYPE_COMMAND_APPLIED
) : EngineGateway {
    private var currentSnapshot = initialSnapshot
    private val snapshotListeners = mutableSetOf<(EngineSnapshot) -> Unit>()
    private val eventListeners = mutableSetOf<(EngineEvent) -> Unit>()

    val commands = mutableListOf<EngineCommand>()

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commands += command
        currentSnapshot = when (command.type) {
            EngineCommand.TYPE_PLAY -> currentSnapshot.copy(
                playbackState = EngineSnapshot.PLAYBACK_PLAYING,
                updatedAtEpochMillis = currentSnapshot.updatedAtEpochMillis + 1
            )

            EngineCommand.TYPE_PAUSE -> currentSnapshot.copy(
                playbackState = EngineSnapshot.PLAYBACK_PAUSED,
                updatedAtEpochMillis = currentSnapshot.updatedAtEpochMillis + 1
            )

            else -> currentSnapshot
        }

        return EngineDispatchResult(
            snapshot = currentSnapshot,
            event = EngineEvent(
                type = dispatchEventType,
                message = command.type
            )
        )
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult = EngineDispatchResult(
        snapshot = currentSnapshot,
        event = EngineEvent(
            type = EngineEvent.TYPE_PLATFORM_EVENT_APPLIED,
            message = event.type
        )
    )

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

private fun testTelemetryLogger(sink: TelemetrySink = TelemetrySink { }): TelemetryLogger = TelemetryLogger(
    sink = sink,
    clock = { 42L }
)

private class RecordingTelemetrySink : TelemetrySink {
    val events = mutableListOf<TelemetryEvent>()

    override fun record(event: TelemetryEvent) {
        events += event
    }
}
