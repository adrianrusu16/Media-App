package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.media.adapter.playback.focus.BambooAudioFocusController
import com.adrianrusu.pandawave.core.media.adapter.playback.focus.BambooAudioFocusRequestResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule

fun interface BambooPlaybackEffectExecutor {
    fun execute(effects: List<EngineEffect>)
}

internal object NoOpBambooPlaybackEffectExecutor : BambooPlaybackEffectExecutor {
    override fun execute(effects: List<EngineEffect>) = Unit
}

internal class Media3EngineEffectExecutor(
    private val player: () -> Media3EffectPlayer,
    private val audioFocusController: BambooAudioFocusController,
    telemetryLogger: TelemetryLogger,
    private val currentProjection: () -> BambooMediaSessionStateProjection? = { null },
    private val recreatePlayer: () -> Unit = {},
    private val notifyUser: (String) -> Unit = {},
    private val onAudioFocusRequestResult: (BambooAudioFocusRequestResult) -> Unit = {}
) : BambooPlaybackEffectExecutor {
    private val telemetryLogger = telemetryLogger.forModule(TelemetryModule.Media3)

    override fun execute(effects: List<EngineEffect>) {
        var focusRequestResult: BambooAudioFocusRequestResult? = null
        effects.forEach { effect ->
            when {
                effect.type == EngineEffect.TYPE_REQUEST_AUDIO_FOCUS -> {
                    focusRequestResult = requestAudioFocus(effect)
                }

                effect.type == EngineEffect.TYPE_PLAY &&
                    focusRequestResult != null &&
                    focusRequestResult != BambooAudioFocusRequestResult.Granted -> {
                    logEffectReceived(effect)
                    logAudioFocusNotGranted(effect, checkNotNull(focusRequestResult))
                }

                else -> execute(effect)
            }
        }
    }

    private fun execute(effect: EngineEffect) {
        logEffectReceived(effect)

        when (effect.type) {
            EngineEffect.TYPE_ABANDON_AUDIO_FOCUS -> audioFocusController.abandonAudioFocus()
            EngineEffect.TYPE_PLAY -> play()
            EngineEffect.TYPE_PAUSE -> player().pause()
            EngineEffect.TYPE_STOP -> player().stop()
            EngineEffect.TYPE_PREPARE_PLAYBACK_SOURCE -> preparePlaybackSource(effect)
            EngineEffect.TYPE_RECREATE_PLAYER_AND_LOAD -> recreatePlayerAndLoad(effect)
            EngineEffect.TYPE_NOTIFY_USER -> effect.message?.let(notifyUser) ?: logMissingPayload(effect)
            EngineEffect.TYPE_UPDATE_METADATA -> updateMetadata(effect)
            EngineEffect.TYPE_SEEK -> seek(effect)
            EngineEffect.TYPE_SET_SPEED -> setSpeed(effect)
            else -> logNoOp(effect)
        }
    }

    private fun requestAudioFocus(effect: EngineEffect): BambooAudioFocusRequestResult {
        logEffectReceived(effect)
        val result = audioFocusController.requestAudioFocus()
        onAudioFocusRequestResult(result)
        telemetryLogger.info(
            name = Media3EffectTelemetryEvents.AUDIO_FOCUS_REQUESTED,
            attributes = mapOf(Media3EffectTelemetryAttributes.RESULT to result.wireValue)
        )
        return result
    }

    private fun logEffectReceived(effect: EngineEffect) {
        PandaLog.d(PandaLog.Tag.MEDIA) { "effect_received type=${effect.type}" }
        telemetryLogger.debug(
            name = Media3EffectTelemetryEvents.EFFECT_RECEIVED,
            attributes = mapOf(Media3EffectTelemetryAttributes.EFFECT_TYPE to effect.type)
        )
    }

    private fun logAudioFocusNotGranted(effect: EngineEffect, result: BambooAudioFocusRequestResult) {
        telemetryLogger.warning(
            name = Media3EffectTelemetryEvents.EFFECT_IGNORED,
            attributes = mapOf(
                Media3EffectTelemetryAttributes.EFFECT_TYPE to effect.type,
                Media3EffectTelemetryAttributes.REASON to Media3EffectTelemetryValues.AUDIO_FOCUS_NOT_GRANTED,
                Media3EffectTelemetryAttributes.RESULT to result.wireValue
            )
        )
    }

    private fun play() {
        val player = player()
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(MIN_POSITION_MILLIS)
        }

        player.play()
    }

    private fun preparePlaybackSource(effect: EngineEffect) {
        val mediaId = effect.mediaId ?: return logMissingPayload(effect)
        val projection = currentProjection() ?: return logMissingProjection(effect)
        if (projection.mediaItem.mediaId != mediaId) {
            return logStaleProjection(effect)
        }

        val playbackInstanceId = effect.playbackInstanceId ?: return logMissingPayload(effect)
        val startPositionMillis = effect.positionMillis ?: projection.positionMillis
        val mediaItem = projection.mediaItem.buildUpon().setTag(playbackInstanceId).build()
        val remainingMs = projection.playbackExpiresAtEpochMillis?.let { expiry ->
            expiry - System.currentTimeMillis()
        }
        telemetryLogger.info(
            name = Media3EffectTelemetryEvents.SOURCE_PREPARED,
            attributes = buildMap {
                put(Media3EffectTelemetryAttributes.EFFECT_TYPE, effect.type)
                put(Media3EffectTelemetryAttributes.PLAYBACK_INSTANCE_ID, playbackInstanceId.toString())
                put(Media3EffectTelemetryAttributes.POSITION_MILLIS, startPositionMillis.toString())
                put(Media3EffectTelemetryAttributes.URI_SCHEME, mediaItem.localConfiguration?.uri?.scheme.orEmpty())
                put(Media3EffectTelemetryAttributes.URI_HOST, mediaItem.localConfiguration?.uri?.host.orEmpty())
                put(Media3EffectTelemetryAttributes.URI_PATH, mediaItem.localConfiguration?.uri?.path.orEmpty())
                remainingMs?.let { remaining ->
                    put(Media3EffectTelemetryAttributes.REMAINING_MS, remaining.toString())
                }
            }
        )
        PandaLog.i(PandaLog.Tag.PLAYER) {
            "source_prepared instance=$playbackInstanceId trackId=$mediaId position_ms=$startPositionMillis " +
                "uri_scheme=${mediaItem.localConfiguration?.uri?.scheme.orEmpty()} " +
                "uri_host=${mediaItem.localConfiguration?.uri?.host.orEmpty()} " +
                "uri_path=${mediaItem.localConfiguration?.uri?.path.orEmpty()} remaining_ms=$remainingMs"
        }
        player().setMediaItem(mediaItem, startPositionMillis)
        player().prepare()
    }

    private fun recreatePlayerAndLoad(effect: EngineEffect) {
        recreatePlayer()
        preparePlaybackSource(effect)
    }

    private fun updateMetadata(effect: EngineEffect) {
        val projection = currentProjection() ?: return logMissingProjection(effect)
        if (effect.mediaId != null && projection.mediaItem.mediaId != effect.mediaId) {
            return logStaleProjection(effect)
        }
        player().updateMediaMetadata(projection.mediaItem.mediaMetadata)
    }

    private fun seek(effect: EngineEffect) {
        val positionMillis = effect.positionMillis ?: return logMissingPayload(effect)
        val player = player()
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
        player.seekTo(positionMillis.coerceAtLeast(MIN_POSITION_MILLIS))
    }

    private fun setSpeed(effect: EngineEffect) {
        val speed = effect.speed ?: return logMissingPayload(effect)
        player().setPlaybackSpeed(speed.coerceAtLeast(MIN_PLAYBACK_SPEED))
    }

    private fun logMissingPayload(effect: EngineEffect) {
        telemetryLogger.debug(
            name = Media3EffectTelemetryEvents.EFFECT_IGNORED,
            attributes = mapOf(
                Media3EffectTelemetryAttributes.EFFECT_TYPE to effect.type,
                Media3EffectTelemetryAttributes.REASON to Media3EffectTelemetryValues.MISSING_PAYLOAD
            )
        )
    }

    private fun logMissingProjection(effect: EngineEffect) {
        telemetryLogger.debug(
            name = Media3EffectTelemetryEvents.EFFECT_IGNORED,
            attributes = mapOf(
                Media3EffectTelemetryAttributes.EFFECT_TYPE to effect.type,
                Media3EffectTelemetryAttributes.REASON to Media3EffectTelemetryValues.MISSING_PROJECTION
            )
        )
    }

    private fun logStaleProjection(effect: EngineEffect) {
        telemetryLogger.debug(
            name = Media3EffectTelemetryEvents.EFFECT_IGNORED,
            attributes = mapOf(
                Media3EffectTelemetryAttributes.EFFECT_TYPE to effect.type,
                Media3EffectTelemetryAttributes.MEDIA_ID_PRESENT to (effect.mediaId != null).toString(),
                Media3EffectTelemetryAttributes.REASON to Media3EffectTelemetryValues.STALE_PROJECTION
            )
        )
    }

    private fun logNoOp(effect: EngineEffect) {
        telemetryLogger.debug(
            name = Media3EffectTelemetryEvents.EFFECT_NO_OP,
            attributes = mapOf(Media3EffectTelemetryAttributes.EFFECT_TYPE to effect.type)
        )
    }
}

internal interface Media3EffectPlayer {
    val playbackState: Int

    fun setMediaItem(mediaItem: MediaItem, positionMillis: Long)

    fun prepare()

    fun play()

    fun pause()

    fun stop()

    fun seekTo(positionMillis: Long)

    fun setPlaybackSpeed(speed: Float)

    fun updateMediaMetadata(metadata: MediaMetadata)
}

internal class PlayerMedia3EffectPlayer(private val player: Player) : Media3EffectPlayer {
    override val playbackState: Int
        get() = player.playbackState

    override fun setMediaItem(mediaItem: MediaItem, positionMillis: Long) {
        player.setMediaItem(mediaItem, positionMillis)
    }

    override fun prepare() {
        player.prepare()
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun stop() {
        player.stop()
    }

    override fun seekTo(positionMillis: Long) {
        player.seekTo(positionMillis)
    }

    override fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    override fun updateMediaMetadata(metadata: MediaMetadata) {
        val current = player.currentMediaItem ?: return
        val index = player.currentMediaItemIndex
        if (index == C.INDEX_UNSET) return
        player.replaceMediaItem(
            index,
            current.buildUpon().setMediaMetadata(metadata).build()
        )
    }
}

internal object Media3EffectTelemetryEvents {
    const val EFFECT_RECEIVED = "media3.effect.received"
    const val EFFECT_IGNORED = "media3.effect.ignored"
    const val EFFECT_NO_OP = "media3.effect.no_op"
    const val AUDIO_FOCUS_REQUESTED = "media3.audio_focus.requested"
    const val SOURCE_PREPARED = "media3.effect.source_prepared"
}

internal object Media3EffectTelemetryAttributes {
    const val EFFECT_TYPE = "effect_type"
    const val MEDIA_ID_PRESENT = "media_id_present"
    const val PLAYBACK_INSTANCE_ID = "playback_instance_id"
    const val POSITION_MILLIS = "position_millis"
    const val REASON = "reason"
    const val RESULT = "result"
    const val URI_HOST = "uri_host"
    const val URI_PATH = "uri_path"
    const val URI_SCHEME = "uri_scheme"
    const val REMAINING_MS = "remaining_ms"
}

internal object Media3EffectTelemetryValues {
    const val MISSING_PAYLOAD = "missing_payload"
    const val MISSING_PROJECTION = "missing_projection"
    const val STALE_PROJECTION = "stale_projection"
    const val AUDIO_FOCUS_NOT_GRANTED = "audio_focus_not_granted"
}

private const val MIN_POSITION_MILLIS = 0L
private const val MIN_PLAYBACK_SPEED = 0F
