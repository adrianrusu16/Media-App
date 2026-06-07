package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@UnstableApi
internal object BambooMediaSessionCommandPolicy {
    fun availablePlayerCommands(playerCommands: Player.Commands): Player.Commands = Player.Commands.Builder()
        .addIf(Player.COMMAND_PLAY_PAUSE, playerCommands.contains(Player.COMMAND_PLAY_PAUSE))
        .addIf(Player.COMMAND_PREPARE, playerCommands.contains(Player.COMMAND_PREPARE))
        .addIf(Player.COMMAND_STOP, playerCommands.contains(Player.COMMAND_STOP))
        .addIf(Player.COMMAND_GET_CURRENT_MEDIA_ITEM, playerCommands.contains(Player.COMMAND_GET_CURRENT_MEDIA_ITEM))
        .addIf(Player.COMMAND_GET_METADATA, playerCommands.contains(Player.COMMAND_GET_METADATA))
        .addIf(Player.COMMAND_SEEK_TO_PREVIOUS, playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
        .addIf(
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        )
        .addIf(Player.COMMAND_SEEK_TO_NEXT, playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT))
        .addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        .build()
}
