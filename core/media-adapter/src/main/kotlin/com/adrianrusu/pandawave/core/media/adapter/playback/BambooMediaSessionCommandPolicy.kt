package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.adrianrusu.pandawave.core.playback.BambooControlState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackControls

@UnstableApi
internal object BambooMediaSessionCommandPolicy {
    fun availablePlayerCommands(playerCommands: Player.Commands, controls: BambooPlaybackControls): Player.Commands =
        Player.Commands.Builder()
            .apply {
                availableCommandTypes(
                    supportedCommandTypes = ProjectedCommandTypes.filter(playerCommands::contains).toSet(),
                    controls = controls
                ).forEach { command ->
                    add(command)
                }
            }
            .build()

    /** Compatibility helper for callers that only have a global readiness bit. */
    fun availablePlayerCommands(playerCommands: Player.Commands, controlsEnabled: Boolean): Player.Commands =
        availablePlayerCommands(playerCommands, controlsFor(controlsEnabled))

    fun availableCommandTypes(supportedCommandTypes: Set<Int>, controls: BambooPlaybackControls): Set<Int> = buildSet {
        MetadataCommandTypes.forEach { command ->
            if (command in supportedCommandTypes) {
                add(command)
            }
        }

        fun addSupported(commands: List<Int>) {
            commands.forEach { command ->
                if (command in supportedCommandTypes) add(command)
            }
        }

        if (controls.playPause.isEnabled) addSupported(PlayPauseCommandTypes)
        if (controls.skipPrevious.isEnabled) addSupported(PreviousCommandTypes)
        if (controls.skipNext.isEnabled) addSupported(NextCommandTypes)
    }

    /** Compatibility helper for existing non-playback projections. */
    fun availableCommandTypes(supportedCommandTypes: Set<Int>, controlsEnabled: Boolean): Set<Int> =
        availableCommandTypes(supportedCommandTypes, controlsFor(controlsEnabled))

    private fun controlsFor(enabled: Boolean): BambooPlaybackControls {
        val control = if (enabled) BambooControlState.enabled() else BambooControlState.hidden()
        return BambooPlaybackControls(
            playPause = control,
            skipNext = control,
            skipPrevious = control,
            showPlayIcon = true
        )
    }
}

private val MetadataCommandTypes = listOf(
    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
    Player.COMMAND_GET_METADATA,
    Player.COMMAND_GET_VOLUME,
    Player.COMMAND_SET_VOLUME
)

private val PlayPauseCommandTypes = listOf(
    Player.COMMAND_PLAY_PAUSE,
    Player.COMMAND_STOP,
    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
    Player.COMMAND_SET_SPEED_AND_PITCH,
    Player.COMMAND_SET_MEDIA_ITEM
)

private val PreviousCommandTypes = listOf(
    Player.COMMAND_SEEK_TO_PREVIOUS,
    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
)

private val NextCommandTypes = listOf(
    Player.COMMAND_SEEK_TO_NEXT,
    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
)

private val ProjectedCommandTypes =
    MetadataCommandTypes + PlayPauseCommandTypes + PreviousCommandTypes + NextCommandTypes
