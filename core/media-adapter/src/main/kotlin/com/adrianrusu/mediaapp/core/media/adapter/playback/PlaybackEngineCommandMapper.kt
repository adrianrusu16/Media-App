package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand

internal object PlaybackEngineCommandMapper {
    fun fromPlayWhenReady(playWhenReady: Boolean): EngineCommand = EngineCommand(
        type = if (playWhenReady) {
            EngineCommand.TYPE_PLAY
        } else {
            EngineCommand.TYPE_PAUSE
        },
        payload = null
    )

    fun fromPlayerCommand(playerCommand: Int): EngineCommand? {
        val commandType = when (playerCommand) {
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> EngineCommand.TYPE_SKIP_PREVIOUS

            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> EngineCommand.TYPE_SKIP_NEXT

            else -> null
        }

        return commandType?.let { type ->
            EngineCommand(
                type = type,
                payload = null
            )
        }
    }
}
