package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackIntent
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackTelemetryAttributes
import com.adrianrusu.mediaapp.core.playback.telemetryName
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
                    Media3PlaybackTelemetryAttributes.PLAY_WHEN_READY to playWhenReady.toString(),
                    Media3PlaybackTelemetryAttributes.REASON to reason.toString(),
                    Media3PlaybackTelemetryAttributes.SOURCE to Media3PlaybackTelemetryValues.PLATFORM_PROJECTION
                )
            )
            return
        }

        telemetryLogger.debug(
            name = Media3PlaybackTelemetryEvents.PLAY_WHEN_READY_RECEIVED,
            attributes = mapOf(
                Media3PlaybackTelemetryAttributes.PLAY_WHEN_READY to playWhenReady.toString(),
                Media3PlaybackTelemetryAttributes.REASON to reason.toString()
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
                attributes = mapOf(Media3PlaybackTelemetryAttributes.PLAYER_COMMAND to playerCommand.toString())
            )
            return false
        }

        telemetryLogger.debug(
            name = Media3PlaybackTelemetryEvents.PLAYER_COMMAND_DISPATCHED,
            attributes = mapOf(
                Media3PlaybackTelemetryAttributes.PLAYER_COMMAND to playerCommand.toString(),
                BambooPlaybackTelemetryAttributes.INTENT to intent.telemetryName
            )
        )
        playbackRepository.dispatch(intent)
        return true
    }

    fun dispatchSeek(positionMillis: Long): Boolean {
        val intent = PlaybackEngineCommandMapper.fromSeekPosition(positionMillis)
        telemetryLogger.debug(
            name = Media3PlaybackTelemetryEvents.PLAYER_COMMAND_DISPATCHED,
            attributes = mapOf(
                Media3PlaybackTelemetryAttributes.PLAYER_COMMAND to intent.telemetryName,
                BambooPlaybackTelemetryAttributes.INTENT to intent.telemetryName,
                Media3PlaybackTelemetryAttributes.POSITION_MILLIS to intent.positionMillis.toString()
            )
        )
        playbackRepository.dispatch(intent)
        return true
    }

    fun dispatchPlaybackSpeed(speed: Float): Boolean {
        val intent = PlaybackEngineCommandMapper.fromPlaybackSpeed(speed)
        telemetryLogger.debug(
            name = Media3PlaybackTelemetryEvents.PLAYER_COMMAND_DISPATCHED,
            attributes = mapOf(
                Media3PlaybackTelemetryAttributes.PLAYER_COMMAND to intent.telemetryName,
                BambooPlaybackTelemetryAttributes.INTENT to intent.telemetryName,
                Media3PlaybackTelemetryAttributes.SPEED to intent.speed.toString()
            )
        )
        playbackRepository.dispatch(intent)
        return true
    }

    fun dispatchCatalogBrowse(parentId: String) {
        val intent = BambooPlaybackIntent.BrowseCatalog(parentId = parentId)
        telemetryLogger.debug(
            name = Media3PlaybackTelemetryEvents.CATALOG_COMMAND_DISPATCHED,
            attributes = mapOf(
                BambooPlaybackTelemetryAttributes.INTENT to intent.telemetryName,
                Media3PlaybackTelemetryAttributes.CATALOG_PARENT_ID to parentId
            )
        )
        playbackRepository.dispatch(intent)
    }

    fun dispatchCatalogSearch(query: String) {
        val intent = BambooPlaybackIntent.SearchCatalog(query = query)
        telemetryLogger.debug(
            name = Media3PlaybackTelemetryEvents.CATALOG_COMMAND_DISPATCHED,
            attributes = mapOf(
                BambooPlaybackTelemetryAttributes.INTENT to intent.telemetryName,
                Media3PlaybackTelemetryAttributes.CATALOG_QUERY to query
            )
        )
        playbackRepository.dispatch(intent)
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
    const val CATALOG_COMMAND_DISPATCHED = "media3.catalog.command.dispatched"
}

internal object Media3PlaybackTelemetryAttributes {
    const val CATALOG_PARENT_ID = "catalog_parent_id"
    const val CATALOG_QUERY = "catalog_query"
    const val PLAY_WHEN_READY = "play_when_ready"
    const val PLAYER_COMMAND = "player_command"
    const val POSITION_MILLIS = "position_millis"
    const val REASON = "reason"
    const val SOURCE = "source"
    const val SPEED = "speed"
}

internal object Media3PlaybackTelemetryValues {
    const val PLATFORM_PROJECTION = "platform_projection"
}
