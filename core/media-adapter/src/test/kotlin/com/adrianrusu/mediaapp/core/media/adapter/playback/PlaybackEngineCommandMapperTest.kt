package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaybackEngineCommandMapperTest {
    @Test
    fun `play when ready maps to play intent`() {
        assertEquals(
            BambooPlaybackIntent.Play,
            PlaybackEngineCommandMapper.fromPlayWhenReady(true)
        )
    }

    @Test
    fun `play when ready false maps to pause intent`() {
        assertEquals(
            BambooPlaybackIntent.Pause,
            PlaybackEngineCommandMapper.fromPlayWhenReady(false)
        )
    }

    @Test
    fun `previous player commands map to previous intent`() {
        listOf(
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
        ).forEach { playerCommand ->
            assertEquals(
                BambooPlaybackIntent.SkipPrevious,
                PlaybackEngineCommandMapper.fromPlayerCommand(playerCommand)
            )
        }
    }

    @Test
    fun `next player commands map to next intent`() {
        listOf(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
        ).forEach { playerCommand ->
            assertEquals(
                BambooPlaybackIntent.SkipNext,
                PlaybackEngineCommandMapper.fromPlayerCommand(playerCommand)
            )
        }
    }

    @Test
    fun `unrelated player command does not map to playback intent`() {
        assertNull(
            PlaybackEngineCommandMapper.fromPlayerCommand(
                Player.COMMAND_SEEK_FORWARD
            )
        )
    }
}
