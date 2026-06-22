package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
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

    @Test
    fun `seek position maps to seek intent`() {
        assertEquals(
            BambooPlaybackIntent.SeekTo(positionMillis = 12_345L),
            PlaybackEngineCommandMapper.fromSeekPosition(12_345L)
        )
    }

    @Test
    fun `negative seek position is clamped`() {
        assertEquals(
            BambooPlaybackIntent.SeekTo(positionMillis = 0L),
            PlaybackEngineCommandMapper.fromSeekPosition(-1L)
        )
    }

    @Test
    fun `playback speed maps to speed intent`() {
        assertEquals(
            BambooPlaybackIntent.SetSpeed(speed = 1.25F),
            PlaybackEngineCommandMapper.fromPlaybackSpeed(1.25F)
        )
    }

    @Test
    fun `negative playback speed is clamped`() {
        assertEquals(
            BambooPlaybackIntent.SetSpeed(speed = 0F),
            PlaybackEngineCommandMapper.fromPlaybackSpeed(-1F)
        )
    }
}
