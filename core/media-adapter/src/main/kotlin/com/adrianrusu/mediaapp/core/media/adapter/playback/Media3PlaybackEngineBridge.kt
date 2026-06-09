package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackIntent
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.mediaapp.core.telemetry.TelemetryLogger

/**
 * Projects Media3 playback requests into the shared Bamboo playback source of truth.
 */
class Media3PlaybackEngineBridge(
    private val playbackRepository: BambooPlaybackRepository,
    private val telemetryLogger: TelemetryLogger
) : Player.Listener,
    AutoCloseable {
    private var platformProjectionDepth = 0

    fun bootstrap() {
        playbackRepository.start()
    }

    fun dispatchPlatformEvent(type: String, payload: String? = null) {
        playbackRepository.dispatch(
            BambooPlaybackIntent.PlatformEvent(type = type, payload = payload)
        )
    }

    fun projectPlatformPlaybackState(block: () -> Unit) {
        platformProjectionDepth += 1
        try {
            block()
        } finally {
            platformProjectionDepth -= 1
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (platformProjectionDepth > 0) {
            telemetryLogger.debug(
                name = Media3PlaybackTelemetryEvents.PLAY_WHEN_READY_IGNORED,
                attributes = mapOf(
                    "play_when_ready" to playWhenReady.toString(),
                    "reason" to reason.toString(),
                    "source" to "platform_projection"
                )
            )
            return
        }

        telemetryLogger.debug(
            name = Media3PlaybackTelemetryEvents.PLAY_WHEN_READY_RECEIVED,
            attributes = mapOf(
                "play_when_ready" to playWhenReady.toString(),
                "reason" to reason.toString()
            )
        )
        playbackRepository.dispatch(
            PlaybackEngineCommandMapper.fromPlayWhenReady(playWhenReady)
        )
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> {
                dispatchPlatformEvent(EnginePlatformEvent.TYPE_MEDIA_LOADED)
            }

            Player.STATE_BUFFERING -> {
                // We could dispatch a buffering event if needed, but Rust handles this via commands
            }
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        dispatchPlatformEvent(EnginePlatformEvent.TYPE_MEDIA_ERROR, error.message)
    }

    fun dispatchPlayerCommand(playerCommand: Int): Boolean {
        val intent = PlaybackEngineCommandMapper.fromPlayerCommand(playerCommand)
        if (intent == null) {
            telemetryLogger.debug(
                name = Media3PlaybackTelemetryEvents.PLAYER_COMMAND_IGNORED,
                attributes = mapOf("player_command" to playerCommand.toString())
            )
            return false
        }

        telemetryLogger.debug(
            name = Media3PlaybackTelemetryEvents.PLAYER_COMMAND_DISPATCHED,
            attributes = mapOf(
                "player_command" to playerCommand.toString(),
                "intent" to intent.telemetryName
            )
        )
        playbackRepository.dispatch(intent)
        return true
    }

    override fun close() {
        playbackRepository.close()
    }
}

internal object Media3PlaybackTelemetryEvents {
    const val PLAY_WHEN_READY_RECEIVED = "media3.play_when_ready.received"
    const val PLAY_WHEN_READY_IGNORED = "media3.play_when_ready.ignored"
    const val PLAYER_COMMAND_DISPATCHED = "media3.player_command.dispatched"
    const val PLAYER_COMMAND_IGNORED = "media3.player_command.ignored"
}

private val BambooPlaybackIntent.telemetryName: String
    get() = when (this) {
        BambooPlaybackIntent.Refresh -> "refresh"
        BambooPlaybackIntent.Play -> "play"
        BambooPlaybackIntent.Pause -> "pause"
        BambooPlaybackIntent.TogglePlayback -> "toggle_playback"
        BambooPlaybackIntent.SkipPrevious -> "skip_previous"
        BambooPlaybackIntent.SkipNext -> "skip_next"
    }
