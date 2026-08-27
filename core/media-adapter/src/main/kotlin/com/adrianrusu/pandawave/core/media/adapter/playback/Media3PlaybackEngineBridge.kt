package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.media.adapter.playback.focus.BambooAudioFocusChange
import com.adrianrusu.pandawave.core.media.adapter.playback.focus.BambooAudioFocusRequestResult
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackTelemetryAttributes
import com.adrianrusu.pandawave.core.playback.telemetryName
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule

/**
 * Projects Media3 playback requests into the shared Bamboo playback source of truth.
 */
data class PlaybackCompletionMetrics(val positionMillis: Long, val durationMillis: Long)

fun interface PlaybackCompletionMetricsProvider {
    fun currentMetrics(): PlaybackCompletionMetrics?
}

fun interface PlaybackCheckpointScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): AutoCloseable
}

private object NoOpPlaybackCheckpointScheduler : PlaybackCheckpointScheduler {
    override fun schedule(delayMillis: Long, action: () -> Unit): AutoCloseable = AutoCloseable { }
}

class Media3PlaybackEngineBridge(
    private val playbackRepository: BambooPlaybackRepository,
    telemetryLogger: TelemetryLogger,
    private val effectExecutor: BambooPlaybackEffectExecutor = NoOpBambooPlaybackEffectExecutor,
    private val playbackMetricsProvider: PlaybackCompletionMetricsProvider =
        PlaybackCompletionMetricsProvider { null },
    private val playbackInstanceIdProvider: () -> Long? = { null },
    private val playerSnapshotProvider: () -> Media3PlayerSnapshot? = { null },
    private val checkpointScheduler: PlaybackCheckpointScheduler = NoOpPlaybackCheckpointScheduler,
    private val checkpointIntervalMillis: Long = DEFAULT_CHECKPOINT_INTERVAL_MILLIS
) : Player.Listener,
    AutoCloseable {
    private val telemetryLogger = telemetryLogger.forModule(TelemetryModule.Media3)
    private var platformProjectionDepth = 0
    private var effectSubscription: AutoCloseable? = null
    private var isPlaying = false
    private var scheduledCheckpoint: AutoCloseable? = null
    private var lastMediaLoadedInstanceId: Long? = null
    private var hasLoggedFirstAudio = false

    fun bootstrap() {
        if (effectSubscription == null) {
            effectSubscription = playbackRepository.observeEffects { effects ->
                projectPlatformPlaybackState {
                    effectExecutor.execute(effects)
                }
            }
        }

        playbackRepository.start()
    }

    fun dispatchPlayWhenReady(playWhenReady: Boolean) {
        playbackRepository.dispatch(
            PlaybackEngineCommandMapper.fromPlayWhenReady(playWhenReady)
        )
    }

    fun dispatchPlatformEvent(type: String, payload: String? = null) {
        playbackRepository.dispatch(
            BambooPlaybackIntent.PlatformEvent(type = type, payload = payload)
        )
    }

    fun dispatchAudioFocusChange(change: BambooAudioFocusChange) {
        telemetryLogger.info(
            name = Media3PlaybackTelemetryEvents.AUDIO_FOCUS_CHANGED,
            attributes = mapOf(Media3PlaybackTelemetryAttributes.FOCUS_CHANGE to change.wireValue)
        )
        dispatchPlatformEvent(
            EnginePlatformEvent.TYPE_AUDIO_FOCUS_CHANGED,
            EngineCommandPayloads.audioFocusChanged(change.wireValue)
        )
    }

    fun dispatchAudioFocusRequestResult(
        result: BambooAudioFocusRequestResult,
        playbackInstanceId: Long? = playbackInstanceIdProvider()
    ) {
        telemetryLogger.info(
            name = Media3PlaybackTelemetryEvents.AUDIO_FOCUS_REQUEST_RESULT,
            attributes = mapOf(Media3PlaybackTelemetryAttributes.RESULT to result.wireValue)
        )
        dispatchPlatformEvent(
            EnginePlatformEvent.TYPE_AUDIO_FOCUS_REQUEST_RESULT,
            EngineCommandPayloads.audioFocusRequestResult(result.wireValue, playbackInstanceId)
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
        dispatchPlayWhenReady(playWhenReady)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val instanceId = playbackInstanceIdProvider()
        PandaLog.d(PandaLog.Tag.PLAYER) {
            "playback_state state=${playbackStateName(playbackState)} instance=$instanceId"
        }
        when (playbackState) {
            Player.STATE_READY -> {
                val instanceId = playbackInstanceIdProvider() ?: return
                if (lastMediaLoadedInstanceId == instanceId) {
                    return
                }
                lastMediaLoadedInstanceId = instanceId
                hasLoggedFirstAudio = false
                val durationMillis = playbackMetricsProvider.currentMetrics()
                    ?.durationMillis
                    ?.takeIf { duration -> duration > 0L }
                PandaLog.i(PandaLog.Tag.PLAYER) {
                    "media_loaded instance=$instanceId duration_ms=$durationMillis"
                }
                dispatchPlatformEvent(
                    EnginePlatformEvent.TYPE_MEDIA_LOADED,
                    EngineCommandPayloads.playbackObservation(
                        playbackInstanceId = instanceId,
                        durationMillis = durationMillis
                    )
                )
            }

            Player.STATE_BUFFERING -> {
                PandaLog.w(PandaLog.Tag.PLAYER) {
                    "rebuffer instance=$instanceId playing=$isPlaying"
                }
            }

            Player.STATE_ENDED -> {
                lastMediaLoadedInstanceId = null
                cancelScheduledCheckpoint()
                reportPlaybackCompletion()
            }

            Player.STATE_IDLE -> {
                lastMediaLoadedInstanceId = null
                cancelScheduledCheckpoint()
            }
        }
    }

    override fun onIsLoadingChanged(isLoading: Boolean) {
        PandaLog.d(PandaLog.Tag.PLAYER) {
            "loading loading=$isLoading instance=${playbackInstanceIdProvider()}"
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (this.isPlaying == isPlaying) return
        this.isPlaying = isPlaying
        PandaLog.d(PandaLog.Tag.PLAYER) {
            "is_playing playing=$isPlaying instance=${playbackInstanceIdProvider()}"
        }
        cancelScheduledCheckpoint()
        if (isPlaying) {
            if (!hasLoggedFirstAudio) {
                hasLoggedFirstAudio = true
                val instanceId = playbackInstanceIdProvider()
                val positionMillis = playbackMetricsProvider.currentMetrics()?.positionMillis
                PandaLog.i(PandaLog.Tag.PLAYER) {
                    "first_audio instance=$instanceId position_ms=$positionMillis"
                }
                PandaLog.i(PandaLog.Tag.HISTORY) {
                    "timer_armed instance=$instanceId threshold_ms=$checkpointIntervalMillis " +
                        "position_ms=$positionMillis"
                }
                telemetryLogger.info(
                    name = Media3PlaybackTelemetryEvents.FIRST_AUDIO,
                    attributes = buildMap {
                        instanceId?.let { id ->
                            put(Media3PlaybackTelemetryAttributes.PLAYBACK_INSTANCE_ID, id.toString())
                        }
                        positionMillis?.let { position ->
                            put(Media3PlaybackTelemetryAttributes.POSITION_MILLIS, position.toString())
                        }
                    }
                )
            }
            reportPlaybackPositionCheckpoint(PlaybackCheckpointTriggers.PLAYING_STARTED)
            scheduleNextCheckpoint()
        } else {
            val snapshot = playerSnapshotProvider()
            if (snapshot?.playbackState == Player.STATE_ENDED) {
                return
            }
            // A seek reports isPlaying=false while playWhenReady stays true.
            // Checkpointing the pre-seek sample rewinds engine/UI progress.
            if (snapshot?.playWhenReady == true) {
                PandaLog.w(PandaLog.Tag.PLAYER) {
                    "skipped_checkpoint reason=seek_in_progress instance=${playbackInstanceIdProvider()}"
                }
                telemetryLogger.debug(
                    name = Media3PlaybackTelemetryEvents.POSITION_CHECKPOINT_SKIPPED,
                    attributes = mapOf(
                        Media3PlaybackTelemetryAttributes.TRIGGER to PlaybackCheckpointTriggers.PAUSED,
                        Media3PlaybackTelemetryAttributes.REASON to Media3PlaybackTelemetryValues.SEEK_IN_PROGRESS
                    )
                )
                return
            }
            reportPlaybackPositionCheckpoint(PlaybackCheckpointTriggers.PAUSED)
        }
    }

    private fun scheduleNextCheckpoint() {
        scheduledCheckpoint = checkpointScheduler.schedule(checkpointIntervalMillis) {
            scheduledCheckpoint = null
            if (!isPlaying) return@schedule
            reportPlaybackPositionCheckpoint(PlaybackCheckpointTriggers.PERIODIC)
            scheduleNextCheckpoint()
        }
    }

    private fun cancelScheduledCheckpoint() {
        scheduledCheckpoint?.close()
        scheduledCheckpoint = null
    }

    private fun reportPlaybackPositionCheckpoint(trigger: String) {
        val metrics = playbackMetricsProvider.currentMetrics()
        if (metrics == null) {
            PandaLog.w(PandaLog.Tag.HISTORY) {
                "checkpoint_skipped trigger=$trigger reason=missing_metrics instance=${playbackInstanceIdProvider()}"
            }
            return
        }
        val positionMillis = metrics.positionMillis.takeIf { it >= 0L }
        if (positionMillis == null) {
            PandaLog.w(PandaLog.Tag.HISTORY) {
                "checkpoint_skipped trigger=$trigger reason=invalid_position instance=${playbackInstanceIdProvider()}"
            }
            return
        }
        val playbackInstanceId = playbackInstanceIdProvider()
        if (playbackInstanceId == null) {
            PandaLog.w(PandaLog.Tag.HISTORY) {
                "checkpoint_skipped trigger=$trigger reason=missing_instance"
            }
            return
        }
        val durationMillis = metrics.durationMillis.takeIf { it > 0L }
        telemetryLogger.debug(
            name = Media3PlaybackTelemetryEvents.POSITION_CHECKPOINT_DISPATCHED,
            attributes = buildMap {
                put(Media3PlaybackTelemetryAttributes.PLAYBACK_INSTANCE_ID, playbackInstanceId.toString())
                put(Media3PlaybackTelemetryAttributes.POSITION_MILLIS, positionMillis.toString())
                put(Media3PlaybackTelemetryAttributes.TRIGGER, trigger)
                durationMillis?.let { duration ->
                    put(Media3PlaybackTelemetryAttributes.DURATION_MILLIS, duration.toString())
                }
            }
        )
        dispatchPlatformEvent(
            EnginePlatformEvent.TYPE_PLAYBACK_POSITION_CHECKPOINT,
            EngineCommandPayloads.playbackPositionCheckpoint(
                playbackInstanceId = playbackInstanceId,
                positionMillis = positionMillis,
                durationMillis = durationMillis
            )
        )
    }

    private fun reportPlaybackCompletion() {
        val playbackState = playbackRepository.state.value
        val trackId = playbackState.mediaId?.trim()?.takeIf(String::isNotBlank) ?: return
        val metrics = playbackMetricsProvider.currentMetrics()
        val durationMillis = metrics?.durationMillis?.takeIf { duration -> duration > 0L }
            ?: playbackState.durationMillis?.takeIf { duration -> duration > 0L }
            ?: return
        val positionMillis = metrics?.positionMillis?.takeIf { position -> position >= 0L } ?: 0L
        val completionRatio = if (durationMillis == 0L) {
            0.0
        } else {
            positionMillis.toDouble().div(durationMillis.toDouble()).coerceIn(0.0, 1.0)
        }
        val playbackInstanceId = playbackInstanceIdProvider() ?: return
        telemetryLogger.info(
            name = Media3PlaybackTelemetryEvents.PLAYBACK_COMPLETION_DISPATCHED,
            attributes = mapOf(
                Media3PlaybackTelemetryAttributes.PLAYBACK_INSTANCE_ID to playbackInstanceId.toString(),
                Media3PlaybackTelemetryAttributes.DURATION_MILLIS to durationMillis.toString(),
                Media3PlaybackTelemetryAttributes.COMPLETION_RATIO to completionRatio.toString()
            )
        )
        dispatchPlatformEvent(
            EnginePlatformEvent.TYPE_PLAYBACK_COMPLETED,
            EngineCommandPayloads.playbackCompleted(trackId, durationMillis, completionRatio, playbackInstanceId)
        )
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        val playbackInstanceId = playbackInstanceIdProvider() ?: return
        val failureKind = error.toEngineFailureKind()
        PandaLog.e(PandaLog.Tag.PLAYER, error) {
            "player_error instance=$playbackInstanceId kind=$failureKind code=${error.errorCode}"
        }
        telemetryLogger.warning(
            name = Media3PlaybackTelemetryEvents.PLAYER_ERROR,
            attributes = mapOf(
                Media3PlaybackTelemetryAttributes.PLAYBACK_INSTANCE_ID to playbackInstanceId.toString(),
                Media3PlaybackTelemetryAttributes.REASON to failureKind,
                Media3PlaybackTelemetryAttributes.PLAYER_COMMAND to error.errorCode.toString()
            )
        )
        dispatchPlatformEvent(
            EnginePlatformEvent.TYPE_MEDIA_ERROR,
            if (failureKind == "decoder_failed") {
                val player = playerSnapshotProvider()
                EngineCommandPayloads.decoderFailed(
                    playbackInstanceId = playbackInstanceId,
                    positionMillis = player?.positionMillis ?: 0L,
                    decoder = error.decoderName(),
                    errorCode = error.errorCode,
                    phase = error.decoderFailurePhase(),
                    playWhenReady = player?.playWhenReady ?: true
                )
            } else {
                EngineCommandPayloads.playbackObservation(playbackInstanceId, failureKind)
            }
        )
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
        PandaLog.i(PandaLog.Tag.PLAYER) { "seek_requested position_ms=${intent.positionMillis}" }
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

    fun dispatchVolume(volume: Float): Boolean {
        val intent = BambooPlaybackIntent.SetVolume(volume.coerceIn(MIN_VOLUME, MAX_VOLUME))
        playbackRepository.dispatch(intent)
        return true
    }

    override fun onVolumeChanged(volume: Float) {
        if (platformProjectionDepth == 0) {
            dispatchVolume(volume)
        }
    }

    fun dispatchCatalogBrowse(parentId: String) {
        val intent = BambooPlaybackIntent.BrowseCatalog(parentId = parentId)
        telemetryLogger.debug(
            name = Media3PlaybackTelemetryEvents.CATALOG_COMMAND_DISPATCHED,
            attributes = mapOf(
                BambooPlaybackTelemetryAttributes.INTENT to intent.telemetryName,
                Media3PlaybackTelemetryAttributes.CATALOG_PARENT_ID_PRESENT to parentId.isNotBlank().toString()
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
                Media3PlaybackTelemetryAttributes.CATALOG_QUERY_LENGTH to query.length.toString()
            )
        )
        playbackRepository.dispatch(intent)
    }

    fun dispatchCatalogPlay(mediaId: String): Boolean {
        val normalizedMediaId = mediaId.trim()
        if (normalizedMediaId.isBlank()) {
            return false
        }

        val intent = BambooPlaybackIntent.PlayMedia(mediaId = normalizedMediaId)
        PandaLog.i(PandaLog.Tag.MEDIA) { "play_requested source=catalog trackId=$normalizedMediaId" }
        telemetryLogger.debug(
            name = Media3PlaybackTelemetryEvents.CATALOG_COMMAND_DISPATCHED,
            attributes = mapOf(
                BambooPlaybackTelemetryAttributes.INTENT to intent.telemetryName,
                Media3PlaybackTelemetryAttributes.MEDIA_ID_PRESENT to "true"
            )
        )
        playbackRepository.dispatch(intent)
        return true
    }

    fun dispatchCatalogPlayQueue(mediaIds: List<String>, startIndex: Int): Boolean {
        val normalized = mediaIds.map(String::trim).filter(String::isNotBlank)
        if (normalized.isEmpty()) {
            return false
        }
        val index = startIndex.coerceIn(0, normalized.lastIndex)
        val intent = BambooPlaybackIntent.PlayQueue(mediaIds = normalized, startIndex = index)
        PandaLog.i(PandaLog.Tag.MEDIA) {
            "play_requested source=catalog command=PlayQueue count=${normalized.size} startIndex=$index"
        }
        telemetryLogger.debug(
            name = Media3PlaybackTelemetryEvents.CATALOG_COMMAND_DISPATCHED,
            attributes = mapOf(
                BambooPlaybackTelemetryAttributes.INTENT to intent.telemetryName,
                Media3PlaybackTelemetryAttributes.MEDIA_ID_PRESENT to "true"
            )
        )
        playbackRepository.dispatch(intent)
        return true
    }

    fun dispatchPlayFromContext(intent: BambooPlaybackIntent.PlayFromContext): Boolean {
        if (intent.selectedMediaId.isBlank() && intent.mediaIds.isEmpty()) {
            return false
        }
        PandaLog.i(PandaLog.Tag.MEDIA) {
            "play_requested source=catalog command=PlayFromContext trackId=${intent.selectedMediaId}"
        }
        telemetryLogger.debug(
            name = Media3PlaybackTelemetryEvents.CATALOG_COMMAND_DISPATCHED,
            attributes = mapOf(
                BambooPlaybackTelemetryAttributes.INTENT to intent.telemetryName,
                Media3PlaybackTelemetryAttributes.MEDIA_ID_PRESENT to intent.selectedMediaId.isNotBlank().toString()
            )
        )
        playbackRepository.dispatch(intent)
        return true
    }

    override fun close() {
        isPlaying = false
        hasLoggedFirstAudio = false
        lastMediaLoadedInstanceId = null
        cancelScheduledCheckpoint()
        effectSubscription?.close()
        effectSubscription = null
        playbackRepository.close()
    }
}

data class Media3PlayerSnapshot(
    val positionMillis: Long,
    val playWhenReady: Boolean,
    val playbackState: Int = Player.STATE_READY
)

private fun androidx.media3.common.PlaybackException.toEngineFailureKind(): String = when (errorCode) {
    androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "source_rejected"

    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "network"

    androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED -> "decoder_failed"

    else -> "unknown"
}

private fun androidx.media3.common.PlaybackException.decoderFailurePhase(): String = when (errorCode) {
    androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "initialization"
    androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED -> "decoding"
    else -> "unknown"
}

private fun androidx.media3.common.PlaybackException.decoderName(): String? = cause
    ?.message
    ?.let { message -> DECODER_NAME_PATTERN.find(message)?.groupValues?.getOrNull(1) }
    ?.takeIf(String::isNotBlank)

private val DECODER_NAME_PATTERN = Regex("(?:Decoder failed:|decoder(?:Name)?[=:])\\s*([^\\s,]+)")

internal object Media3PlaybackTelemetryEvents {
    const val AUDIO_FOCUS_CHANGED = "media3.audio_focus.changed"
    const val AUDIO_FOCUS_REQUEST_RESULT = "media3.audio_focus.request_result"
    const val PLAY_WHEN_READY_RECEIVED = "media3.play_when_ready.received"
    const val PLAY_WHEN_READY_IGNORED = "media3.play_when_ready.ignored"
    const val PLAYER_COMMAND_DISPATCHED = "media3.player_command.dispatched"
    const val PLAYER_COMMAND_IGNORED = "media3.player_command.ignored"
    const val PLAYBACK_COMPLETION_DISPATCHED = "media3.playback.completion.dispatched"
    const val CATALOG_COMMAND_DISPATCHED = "media3.catalog.command.dispatched"
    const val POSITION_CHECKPOINT_DISPATCHED = "media3.playback.position_checkpoint.dispatched"
    const val POSITION_CHECKPOINT_SKIPPED = "media3.playback.position_checkpoint.skipped"
    const val PLAYER_ERROR = "media3.player.error"
    const val FIRST_AUDIO = "media3.playback.first_audio"
}

internal object Media3PlaybackTelemetryAttributes {
    const val CATALOG_PARENT_ID_PRESENT = "catalog_parent_id_present"
    const val CATALOG_QUERY_LENGTH = "catalog_query_length"
    const val COMPLETION_RATIO = "completion_ratio"
    const val DURATION_MILLIS = "duration_millis"
    const val FOCUS_CHANGE = "focus_change"
    const val MEDIA_ID_PRESENT = "media_id_present"
    const val PLAY_WHEN_READY = "play_when_ready"
    const val PLAYBACK_INSTANCE_ID = "playback_instance_id"
    const val PLAYER_COMMAND = "player_command"
    const val POSITION_MILLIS = "position_millis"
    const val REASON = "reason"
    const val RESULT = "result"
    const val SOURCE = "source"
    const val SPEED = "speed"
    const val TRIGGER = "trigger"
}

internal object Media3PlaybackTelemetryValues {
    const val PLATFORM_PROJECTION = "platform_projection"
    const val SEEK_IN_PROGRESS = "seek_in_progress"
}

private object PlaybackCheckpointTriggers {
    const val PLAYING_STARTED = "playing_started"
    const val PERIODIC = "periodic"
    const val PAUSED = "paused"
}

private const val MIN_VOLUME = 0F
private const val MAX_VOLUME = 1F
private const val DEFAULT_CHECKPOINT_INTERVAL_MILLIS = 5_000L

private fun playbackStateName(playbackState: Int): String = when (playbackState) {
    Player.STATE_IDLE -> "idle"
    Player.STATE_BUFFERING -> "buffering"
    Player.STATE_READY -> "ready"
    Player.STATE_ENDED -> "ended"
    else -> playbackState.toString()
}
