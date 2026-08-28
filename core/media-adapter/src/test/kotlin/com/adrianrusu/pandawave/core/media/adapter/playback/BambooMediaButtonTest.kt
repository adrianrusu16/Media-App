package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(UnstableApi::class)
class BambooMediaButtonTest {
    @Test
    fun `media button has the name required by legacy media actions`() {
        val button = bambooMediaButton(
            icon = CommandButton.ICON_PLAY,
            command = Player.COMMAND_PLAY_PAUSE,
            displayName = "Play or pause"
        )

        assertEquals("Play or pause", button.displayName.toString())
        assertEquals(Player.COMMAND_PLAY_PAUSE, button.playerCommand)
    }
}
