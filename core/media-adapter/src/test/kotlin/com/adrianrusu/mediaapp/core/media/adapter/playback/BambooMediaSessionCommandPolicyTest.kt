package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BambooMediaSessionCommandPolicyTest {
    @Test
    fun `disabled controls keep metadata commands only`() {
        val commands = BambooMediaSessionCommandPolicy.availableCommandTypes(
            supportedCommandTypes = allSupportedCommandTypes(),
            controlsEnabled = false
        )

        assertTrue(Player.COMMAND_GET_CURRENT_MEDIA_ITEM in commands)
        assertTrue(Player.COMMAND_GET_METADATA in commands)
        assertFalse(Player.COMMAND_PLAY_PAUSE in commands)
        assertFalse(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM in commands)
        assertFalse(Player.COMMAND_SEEK_TO_MEDIA_ITEM in commands)
        assertFalse(Player.COMMAND_SEEK_TO_PREVIOUS in commands)
        assertFalse(Player.COMMAND_SEEK_TO_NEXT in commands)
        assertFalse(Player.COMMAND_SET_SPEED_AND_PITCH in commands)
    }

    @Test
    fun `enabled controls expose playback commands`() {
        val commands = BambooMediaSessionCommandPolicy.availableCommandTypes(
            supportedCommandTypes = allSupportedCommandTypes(),
            controlsEnabled = true
        )

        assertTrue(Player.COMMAND_GET_CURRENT_MEDIA_ITEM in commands)
        assertTrue(Player.COMMAND_GET_METADATA in commands)
        assertTrue(Player.COMMAND_PLAY_PAUSE in commands)
        assertTrue(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM in commands)
        assertTrue(Player.COMMAND_SEEK_TO_MEDIA_ITEM in commands)
        assertTrue(Player.COMMAND_SEEK_TO_PREVIOUS in commands)
        assertTrue(Player.COMMAND_SEEK_TO_NEXT in commands)
        assertTrue(Player.COMMAND_SET_SPEED_AND_PITCH in commands)
    }

    @Test
    fun `enabled controls cannot expose unsupported player commands`() {
        val commands = BambooMediaSessionCommandPolicy.availableCommandTypes(
            supportedCommandTypes = setOf(
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_PLAY_PAUSE
            ),
            controlsEnabled = true
        )

        assertTrue(Player.COMMAND_PLAY_PAUSE in commands)
        assertFalse(Player.COMMAND_SEEK_TO_PREVIOUS in commands)
        assertFalse(Player.COMMAND_SEEK_TO_NEXT in commands)
    }
}

private fun allSupportedCommandTypes(): Set<Int> = setOf(
    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
    Player.COMMAND_GET_METADATA,
    Player.COMMAND_PLAY_PAUSE,
    Player.COMMAND_PREPARE,
    Player.COMMAND_STOP,
    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_PREVIOUS,
    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    Player.COMMAND_SEEK_TO_NEXT,
    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    Player.COMMAND_SET_SPEED_AND_PITCH
)
