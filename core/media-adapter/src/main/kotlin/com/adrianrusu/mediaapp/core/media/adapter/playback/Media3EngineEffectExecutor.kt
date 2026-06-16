package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.media.adapter.playback.focus.BambooAudioFocusController
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.mediaapp.core.telemetry.TelemetryLogger

fun interface BambooPlaybackEffectExecutor {
    fun execute(effects: List<EngineEffect>)
}

internal object NoOpBambooPlaybackEffectExecutor : BambooPlaybackEffectExecutor {
    override fun execute(effects: List<EngineEffect>) = Unit
}

internal class Media3EngineEffectExecutor(
    private val player: Media3EffectPlayer,
    private val audioFocusController: BambooAudioFocusController,
    private val telemetryLogger: TelemetryLogger,
    private val currentProjection: () -> BambooMediaSessionStateProjection? = { null }
) : BambooPlaybackEffectExecutor {
    override fun execute(effects: List<EngineEffect>) {
        effects.forEach(::execute)
    }

    private fun execute(effect: EngineEffect) {
        telemetryLogger.debug(
            name = Media3EffectTelemetryEvents.EFFECT_RECEIVED,
            attributes = mapOf(Media3EffectTelemetryAttributes.EFFECT_TYPE to effect.type)
        )

        when (effect.type) {
            EngineEffect.TYPE_REQUEST_AUDIO_FOCUS -> audioFocusController.requestAudioFocus()
            EngineEffect.TYPE_ABANDON_AUDIO_FOCUS -> audioFocusController.abandonAudioFocus()
            EngineEffect.TYPE_PLAY -> play()
            EngineEffect.TYPE_PAUSE -> player.pause()
            EngineEffect.TYPE_STOP -> player.stop()
            EngineEffect.TYPE_UPDATE_METADATA -> updateMetadata(effect)
            EngineEffect.TYPE_SEEK -> seek(effect)
            EngineEffect.TYPE_SET_SPEED -> setSpeed(effect)
            else -> logNoOp(effect)
        }
    }

    private fun play() {
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }

        player.play()
    }

    private fun updateMetadata(effect: EngineEffect) {
        val mediaId = effect.mediaId ?: return logMissingPayload(effect)
        val projection = currentProjection() ?: return logMissingProjection(effect)
        if (projection.mediaItem.mediaId != mediaId) {
            return logStaleMetadata(effect)
        }

        player.setMediaItem(projection.mediaItem, projection.positionMillis)
    }

    private fun seek(effect: EngineEffect) {
        val positionMillis = effect.positionMillis ?: return logMissingPayload(effect)
        player.seekTo(positionMillis.coerceAtLeast(MIN_POSITION_MILLIS))
    }

    private fun setSpeed(effect: EngineEffect) {
        val speed = effect.speed ?: return logMissingPayload(effect)
        player.setPlaybackSpeed(speed.coerceAtLeast(MIN_PLAYBACK_SPEED))
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

    private fun logStaleMetadata(effect: EngineEffect) {
        telemetryLogger.debug(
            name = Media3EffectTelemetryEvents.EFFECT_IGNORED,
            attributes = mapOf(
                Media3EffectTelemetryAttributes.EFFECT_TYPE to effect.type,
                Media3EffectTelemetryAttributes.MEDIA_ID to effect.mediaId.orEmpty(),
                Media3EffectTelemetryAttributes.REASON to Media3EffectTelemetryValues.STALE_METADATA
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
}

internal object Media3EffectTelemetryEvents {
    const val EFFECT_RECEIVED = "media3.effect.received"
    const val EFFECT_IGNORED = "media3.effect.ignored"
    const val EFFECT_NO_OP = "media3.effect.no_op"
}

internal object Media3EffectTelemetryAttributes {
    const val EFFECT_TYPE = "effect_type"
    const val MEDIA_ID = "media_id"
    const val REASON = "reason"
}

internal object Media3EffectTelemetryValues {
    const val MISSING_PAYLOAD = "missing_payload"
    const val MISSING_PROJECTION = "missing_projection"
    const val STALE_METADATA = "stale_metadata"
}

private const val MIN_POSITION_MILLIS = 0L
private const val MIN_PLAYBACK_SPEED = 0F
