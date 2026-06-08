package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackEngineCommandMapperTest {
    @Test
    fun playWhenReadyMapsToPlayIntent() {
        assertEquals(
            BambooPlaybackIntent.Play,
            PlaybackEngineCommandMapper.fromPlayWhenReady(true)
        )
    }

    @Test
    fun playWhenReadyFalseMapsToPauseIntent() {
        assertEquals(
            BambooPlaybackIntent.Pause,
            PlaybackEngineCommandMapper.fromPlayWhenReady(false)
        )
    }

    @Test
    fun previousPlayerCommandsMapToPreviousIntent() {
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
    fun nextPlayerCommandsMapToNextIntent() {
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
    fun unrelatedPlayerCommandDoesNotMapToPlaybackIntent() {
        assertNull(
            PlaybackEngineCommandMapper.fromPlayerCommand(
                Player.COMMAND_SEEK_FORWARD
            )
        )
    }
}
