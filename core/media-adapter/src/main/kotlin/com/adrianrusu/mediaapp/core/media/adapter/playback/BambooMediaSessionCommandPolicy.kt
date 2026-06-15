package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@UnstableApi
internal object BambooMediaSessionCommandPolicy {
    fun availablePlayerCommands(playerCommands: Player.Commands, controlsEnabled: Boolean): Player.Commands =
        Player.Commands.Builder()
            .apply {
                availableCommandTypes(
                    supportedCommandTypes = ProjectedCommandTypes.filter(playerCommands::contains).toSet(),
                    controlsEnabled = controlsEnabled
                ).forEach { command ->
                    add(command)
                }
            }
            .build()

    fun availableCommandTypes(supportedCommandTypes: Set<Int>, controlsEnabled: Boolean): Set<Int> = buildSet {
        MetadataCommandTypes.forEach { command ->
            if (command in supportedCommandTypes) {
                add(command)
            }
        }

        if (controlsEnabled) {
            ControlCommandTypes.forEach { command ->
                if (command in supportedCommandTypes) {
                    add(command)
                }
            }
        }
    }
}

private val MetadataCommandTypes = listOf(
    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
    Player.COMMAND_GET_METADATA
)

private val ControlCommandTypes = listOf(
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

private val ProjectedCommandTypes = MetadataCommandTypes + ControlCommandTypes
