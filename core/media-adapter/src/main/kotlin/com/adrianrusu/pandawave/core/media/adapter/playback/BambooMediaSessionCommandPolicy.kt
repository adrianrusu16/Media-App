package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.adrianrusu.pandawave.core.playback.BambooControlState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackControls

@UnstableApi
internal object BambooMediaSessionCommandPolicy {
    fun availablePlayerCommands(
        controls: BambooPlaybackControls,
        hasSeekableTimeline: Boolean = false
    ): Player.Commands = Player.Commands.Builder()
        .apply {
            availableCommandTypes(controls, hasSeekableTimeline).forEach { command ->
                add(command)
            }
        }
        .build()

    fun availablePlayerCommands(playerCommands: Player.Commands, controls: BambooPlaybackControls): Player.Commands =
        Player.Commands.Builder()
            .apply {
                availableCommandTypes(
                    supportedCommandTypes = commandTypes(playerCommands),
                    controls = controls
                ).forEach { command -> add(command) }
            }
            .build()

    fun availablePlayerCommands(playerCommands: Player.Commands, controlsEnabled: Boolean): Player.Commands =
        availablePlayerCommands(playerCommands, controlsFor(controlsEnabled))

    fun availableCommandTypes(controls: BambooPlaybackControls, hasSeekableTimeline: Boolean = false): Set<Int> =
        buildSet {
            addAll(AlwaysReadableCommandTypes)
            if (controls.playPause.isEnabled) {
                addAll(CurrentItemCommandTypes)
                addAll(MediaSelectionCommandTypes)
            }
            if (controls.skipPrevious.isEnabled) addAll(PreviousCommandTypes)
            if (controls.skipNext.isEnabled) addAll(NextCommandTypes)
            if (hasSeekableTimeline && controls.playPause.isEnabled) {
                add(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
            }
        }

    fun availableCommandTypes(supportedCommandTypes: Set<Int>, controls: BambooPlaybackControls): Set<Int> =
        availableCommandTypes(controls = controls, hasSeekableTimeline = false)
            .intersect(supportedCommandTypes)

    fun availableCommandTypes(supportedCommandTypes: Set<Int>, controlsEnabled: Boolean): Set<Int> =
        availableCommandTypes(supportedCommandTypes, controlsFor(controlsEnabled))

    private fun commandTypes(commands: Player.Commands): Set<Int> = buildSet {
        for (command in ProjectedCommandTypes) {
            if (commands.contains(command)) add(command)
        }
    }

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

private val AlwaysReadableCommandTypes = listOf(
    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
    Player.COMMAND_GET_METADATA,
    Player.COMMAND_GET_VOLUME,
    Player.COMMAND_SET_VOLUME
)

private val CurrentItemCommandTypes = listOf(
    Player.COMMAND_PLAY_PAUSE,
    Player.COMMAND_STOP,
    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
    Player.COMMAND_SET_SPEED_AND_PITCH
)

private val MediaSelectionCommandTypes = listOf(Player.COMMAND_SET_MEDIA_ITEM)

private val PreviousCommandTypes = listOf(
    Player.COMMAND_SEEK_TO_PREVIOUS,
    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
)

private val NextCommandTypes = listOf(
    Player.COMMAND_SEEK_TO_NEXT,
    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
)

private val ProjectedCommandTypes =
    AlwaysReadableCommandTypes +
        CurrentItemCommandTypes +
        MediaSelectionCommandTypes +
        PreviousCommandTypes +
        NextCommandTypes +
        listOf(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
