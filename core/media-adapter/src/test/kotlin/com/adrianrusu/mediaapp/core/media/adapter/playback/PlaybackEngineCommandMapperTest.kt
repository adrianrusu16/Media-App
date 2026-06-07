package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackEngineCommandMapperTest {
    @Test
    fun playWhenReadyMapsToPlayCommand() {
        val command = PlaybackEngineCommandMapper.fromPlayWhenReady(true)

        assertEquals(EngineCommand.TYPE_PLAY, command.type)
        assertNull(command.payload)
    }

    @Test
    fun notPlayWhenReadyMapsToPauseCommand() {
        val command = PlaybackEngineCommandMapper.fromPlayWhenReady(false)

        assertEquals(EngineCommand.TYPE_PAUSE, command.type)
        assertNull(command.payload)
    }

    @Test
    fun previousPlayerCommandsMapToSkipPreviousCommand() {
        val commands = listOf(
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        )

        commands.forEach { playerCommand ->
            val command = PlaybackEngineCommandMapper.fromPlayerCommand(playerCommand)

            assertEquals(EngineCommand.TYPE_SKIP_PREVIOUS, command?.type)
            assertNull(command?.payload)
        }
    }

    @Test
    fun nextPlayerCommandsMapToSkipNextCommand() {
        val commands = listOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
        )

        commands.forEach { playerCommand ->
            val command = PlaybackEngineCommandMapper.fromPlayerCommand(playerCommand)

            assertEquals(EngineCommand.TYPE_SKIP_NEXT, command?.type)
            assertNull(command?.payload)
        }
    }

    @Test
    fun unrelatedPlayerCommandDoesNotMapToEngineCommand() {
        assertNull(
            PlaybackEngineCommandMapper.fromPlayerCommand(
                Player.COMMAND_SEEK_FORWARD
            )
        )
    }
}
