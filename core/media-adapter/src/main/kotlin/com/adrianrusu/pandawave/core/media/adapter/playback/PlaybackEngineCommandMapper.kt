package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent

internal object PlaybackEngineCommandMapper {
    fun fromPlayWhenReady(playWhenReady: Boolean): BambooPlaybackIntent = if (playWhenReady) {
        BambooPlaybackIntent.Play
    } else {
        BambooPlaybackIntent.Pause
    }

    fun fromPlayerCommand(playerCommand: Int): BambooPlaybackIntent? = when (playerCommand) {
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> BambooPlaybackIntent.SkipPrevious

        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> BambooPlaybackIntent.SkipNext

        else -> null
    }

    fun fromSeekPosition(positionMillis: Long): BambooPlaybackIntent.SeekTo = BambooPlaybackIntent.SeekTo(
        positionMillis = positionMillis.coerceAtLeast(0L)
    )

    fun fromPlaybackSpeed(speed: Float): BambooPlaybackIntent.SetSpeed = BambooPlaybackIntent.SetSpeed(
        speed = speed.coerceAtLeast(0F)
    )
}
