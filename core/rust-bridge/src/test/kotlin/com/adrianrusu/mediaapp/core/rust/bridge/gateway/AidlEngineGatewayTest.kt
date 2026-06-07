package com.adrianrusu.mediaapp.core.rust.bridge.gateway

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class AidlEngineGatewayTest {
    @Test
    fun snapshotReturnsServiceSnapshotWhenConnected() {
        val service = RecordingEngineService(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 10L)
        )
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(service = service),
            clock = { 1L }
        )

        assertEquals(EngineSnapshot.idle(nowMillis = 10L), gateway.snapshot())
    }

    @Test
    fun dispatchSendsCommandAndRefreshesSnapshotWhenConnected() {
        val service = RecordingEngineService(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 10L)
        )
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(service = service),
            clock = { 1L }
        )

        val result = gateway.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_PLAY,
                payload = null
            )
        )

        assertEquals(listOf(EngineCommand.TYPE_PLAY), service.commandTypes)
        assertEquals(EngineSnapshot.PLAYBACK_PLAYING, result.snapshot.playbackState)
        assertEquals(EngineEvent.TYPE_COMMAND_APPLIED, result.event.type)
    }

    @Test
    fun dispatchReturnsUnavailableEventWhenDisconnected() {
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(service = null),
            clock = { 25L }
        )

        val result = gateway.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_PLAY,
                payload = null
            )
        )

        assertEquals(EngineSnapshot.idle(nowMillis = 25L), result.snapshot)
        assertEquals(EngineEvent.TYPE_GATEWAY_UNAVAILABLE, result.event.type)
        assertEquals(EngineCommand.TYPE_PLAY, result.event.message)
    }

    @Test
    fun listenerSnapshotIsUsedWhileServiceIsUnavailable() {
        val connection = FakeEngineServiceConnection(service = null)
        val gateway = AidlEngineGateway(
            connection = connection,
            clock = { 1L }
        )
        val pushedSnapshot = EngineSnapshot(
            playbackState = EngineSnapshot.PLAYBACK_PAUSED,
            mediaId = "track-1",
            title = "Quiet Cabin",
            artist = "PandaWave",
            userId = null,
            restrictionState = EngineSnapshot.RESTRICTION_UNKNOWN,
            updatedAtEpochMillis = 50L
        )

        connection.pushSnapshot(pushedSnapshot)

        assertEquals(pushedSnapshot, gateway.snapshot())
    }
}

private class FakeEngineServiceConnection(override var service: EngineService?) : EngineServiceConnection {
    private var listener: EngineServiceListener? = null

    override fun connect(listener: EngineServiceListener) {
        this.listener = listener
    }

    override fun close() {
        service = null
        listener = null
    }

    fun pushSnapshot(snapshot: EngineSnapshot) {
        listener?.onSnapshotChanged(snapshot)
    }
}

private class RecordingEngineService(initialSnapshot: EngineSnapshot) : EngineService {
    private var currentSnapshot = initialSnapshot
    private val commands = mutableListOf<EngineCommand>()

    val commandTypes: List<String>
        get() = commands.map { it.type }

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun dispatch(command: EngineCommand) {
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
    }
}
