package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

@UnstableApi
internal class BambooMediaSessionPlayer(
    delegate: Player,
    private val playbackEngineBridge: Media3PlaybackEngineBridge,
    private val controlsEnabled: () -> Boolean
) : ForwardingPlayer(delegate) {
    override fun getAvailableCommands(): Player.Commands = BambooMediaSessionCommandPolicy.availablePlayerCommands(
        playerCommands = super.getAvailableCommands(),
        controlsEnabled = controlsEnabled()
    )

    override fun seekToPreviousMediaItem() {
        playbackEngineBridge.dispatchPlayerCommand(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        super.seekToPreviousMediaItem()
    }

    override fun seekToPrevious() {
        playbackEngineBridge.dispatchPlayerCommand(COMMAND_SEEK_TO_PREVIOUS)
        super.seekToPrevious()
    }

    override fun seekToNextMediaItem() {
        playbackEngineBridge.dispatchPlayerCommand(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        super.seekToNextMediaItem()
    }

    override fun seekToNext() {
        playbackEngineBridge.dispatchPlayerCommand(COMMAND_SEEK_TO_NEXT)
        super.seekToNext()
    }
}
