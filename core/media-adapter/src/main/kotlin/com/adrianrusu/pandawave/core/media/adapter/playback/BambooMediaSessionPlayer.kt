package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.adrianrusu.pandawave.core.playback.BambooControlState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackControls

@UnstableApi
internal class BambooMediaSessionPlayer(
    delegate: Player,
    private val playbackEngineBridge: Media3PlaybackEngineBridge,
    private val controlsEnabled: () -> Boolean,
    private val controls: () -> BambooPlaybackControls = { controlsFor(controlsEnabled()) }
) : ForwardingPlayer(delegate) {
    override fun getAvailableCommands(): Player.Commands = BambooMediaSessionCommandPolicy.availablePlayerCommands(
        playerCommands = super.getAvailableCommands(),
        controls = controls()
    )

    override fun play() {
        playbackEngineBridge.dispatchPlayWhenReady(playWhenReady = true)
    }

    override fun pause() {
        playbackEngineBridge.dispatchPlayWhenReady(playWhenReady = false)
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        playbackEngineBridge.dispatchPlayWhenReady(playWhenReady)
    }

    override fun seekToPreviousMediaItem() {
        playbackEngineBridge.dispatchPlayerCommand(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
    }

    override fun seekToPrevious() {
        playbackEngineBridge.dispatchPlayerCommand(COMMAND_SEEK_TO_PREVIOUS)
    }

    override fun seekToNextMediaItem() {
        playbackEngineBridge.dispatchPlayerCommand(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
    }

    override fun seekToNext() {
        playbackEngineBridge.dispatchPlayerCommand(COMMAND_SEEK_TO_NEXT)
    }

    override fun seekTo(positionMs: Long) {
        playbackEngineBridge.dispatchSeek(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        playbackEngineBridge.dispatchSeek(positionMs)
    }

    override fun setPlaybackSpeed(speed: Float) {
        playbackEngineBridge.dispatchPlaybackSpeed(speed)
    }

    override fun setVolume(volume: Float) {
        playbackEngineBridge.dispatchVolume(volume)
    }
}

private fun controlsFor(enabled: Boolean): BambooPlaybackControls {
    val control = if (enabled) BambooControlState.enabled() else BambooControlState.hidden()
    return BambooPlaybackControls(control, control, control, showPlayIcon = true)
}
