package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.EngineGateway
import org.junit.Assert.assertEquals
import org.junit.Test

class Media3PlaybackEngineBridgeTest {
    @Test
    fun bootstrapDispatchesBootstrapCommand() {
        val engine = RecordingEngineGateway()
        val bridge = Media3PlaybackEngineBridge(engine)

        bridge.bootstrap()

        assertEquals(listOf(EngineCommand.TYPE_BOOTSTRAP), engine.commandTypes)
    }

    @Test
    fun playWhenReadyChangeDispatchesPlaybackCommands() {
        val engine = RecordingEngineGateway()
        val bridge = Media3PlaybackEngineBridge(engine)

        bridge.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        bridge.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        assertEquals(
            listOf(EngineCommand.TYPE_PLAY, EngineCommand.TYPE_PAUSE),
            engine.commandTypes
        )
    }

    @Test
    fun playerSkipCommandsDispatchThroughEngineBoundary() {
        val engine = RecordingEngineGateway()
        val bridge = Media3PlaybackEngineBridge(engine)

        bridge.dispatchPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
        bridge.dispatchPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)

        assertEquals(
            listOf(
                EngineCommand.TYPE_SKIP_PREVIOUS,
                EngineCommand.TYPE_SKIP_NEXT
            ),
            engine.commandTypes
        )
    }

    @Test
    fun unrelatedPlayerCommandIsIgnoredByEngineBoundary() {
        val engine = RecordingEngineGateway()
        val bridge = Media3PlaybackEngineBridge(engine)

        bridge.dispatchPlayerCommand(Player.COMMAND_SEEK_FORWARD)

        assertEquals(emptyList<String>(), engine.commandTypes)
    }
}

private class RecordingEngineGateway : EngineGateway {
    private val commands = mutableListOf<EngineCommand>()

    val commandTypes: List<String>
        get() = commands.map { it.type }

    override fun snapshot(): EngineSnapshot = EngineSnapshot.idle(nowMillis = 0L)

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commands += command

        return EngineDispatchResult(
            snapshot = snapshot(),
            event = EngineEvent(
                type = EngineEvent.TYPE_COMMAND_APPLIED,
                message = command.type
            )
        )
    }

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable = AutoCloseable {
        // This bridge only dispatches commands; snapshot observation is covered in rust-bridge tests.
    }
}
