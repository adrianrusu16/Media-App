package com.adrianrusu.mediaapp.feature.nowplaying.data

import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingEngineConnectionUiState
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingIntent
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryNowPlayingRepositoryTest {
    @Test
    fun startProjectsCurrentEngineSnapshotAndRestrictionState() {
        val repository = InMemoryNowPlayingRepository(
            engine = RecordingEngineGateway(
                initialSnapshot = EngineSnapshot(
                    playbackState = EngineSnapshot.PLAYBACK_PAUSED,
                    mediaId = "track-1",
                    title = "Quiet Cabin",
                    artist = "Test Artist",
                    userId = null,
                    restrictionState = EngineSnapshot.RESTRICTION_UNKNOWN,
                    updatedAtEpochMillis = 100L
                )
            ),
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            )
        )

        repository.start()

        assertEquals("Quiet Cabin", repository.state.value.title)
        assertEquals(NowPlayingPlaybackState.Paused, repository.state.value.playbackState)
        assertEquals("Standard device", repository.state.value.restriction.label)
    }

    @Test
    fun togglePlaybackDispatchesPlayThenPauseThroughEngine() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = InMemoryNowPlayingRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            )
        )

        repository.start()
        repository.dispatch(NowPlayingIntent.TogglePlayback)
        repository.dispatch(NowPlayingIntent.TogglePlayback)

        assertEquals(
            listOf(
                EngineCommand.TYPE_BOOTSTRAP,
                EngineCommand.TYPE_PLAY,
                EngineCommand.TYPE_PAUSE
            ),
            engine.commands.map { it.type }
        )
        assertEquals(NowPlayingPlaybackState.Paused, repository.state.value.playbackState)
    }

    @Test
    fun pushedEngineSnapshotsUpdateNowPlayingState() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = InMemoryNowPlayingRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            )
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
                updatedAtEpochMillis = 200L
            )
        )

        assertEquals("Quiet Cabin", repository.state.value.title)
        assertEquals("PandaWave", repository.state.value.artist)
        assertEquals(NowPlayingPlaybackState.Playing, repository.state.value.playbackState)
    }

    @Test
    fun queuedBootstrapKeepsEngineCommandsDisabled() {
        val engine = RecordingEngineGateway(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 1L),
            dispatchEventType = EngineEvent.TYPE_COMMAND_QUEUED
        )
        val repository = InMemoryNowPlayingRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            )
        )

        repository.start()
        repository.dispatch(NowPlayingIntent.Refresh)
        repository.dispatch(NowPlayingIntent.TogglePlayback)

        assertEquals(NowPlayingEngineConnectionUiState.Connecting, repository.state.value.engineConnection)
        assertEquals(
            listOf(EngineCommand.TYPE_BOOTSTRAP),
            engine.commands.map { it.type }
        )
    }

    @Test
    fun pushedEngineEventsUpdateConnectionState() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = InMemoryNowPlayingRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            )
        )

        repository.start()
        engine.pushEvent(EngineEvent(type = EngineEvent.TYPE_SERVICE_BINDING_DIED, message = null))

        assertEquals(NowPlayingEngineConnectionUiState.Reconnecting, repository.state.value.engineConnection)

        repository.dispatch(NowPlayingIntent.TogglePlayback)

        assertEquals(
            listOf(EngineCommand.TYPE_BOOTSTRAP),
            engine.commands.map { it.type }
        )

        engine.pushEvent(EngineEvent(type = EngineEvent.TYPE_SERVICE_CONNECTED, message = null))

        assertEquals(NowPlayingEngineConnectionUiState.Ready, repository.state.value.engineConnection)

        repository.dispatch(NowPlayingIntent.TogglePlayback)

        assertEquals(
            listOf(
                EngineCommand.TYPE_BOOTSTRAP,
                EngineCommand.TYPE_PLAY
            ),
            engine.commands.map { it.type }
        )
    }
}

private class FakeUxRestrictionObserver(private val restrictions: AutomotiveUxRestrictions) :
    AutomotiveUxRestrictionObserver {
    override fun current(): AutomotiveUxRestrictions = restrictions

    override fun start(onChanged: (AutomotiveUxRestrictions) -> Unit) {
        onChanged(restrictions)
    }

    override fun close() = Unit
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
