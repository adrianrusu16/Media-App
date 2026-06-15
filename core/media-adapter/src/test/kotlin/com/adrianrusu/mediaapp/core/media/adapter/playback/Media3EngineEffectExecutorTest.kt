package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.media.adapter.playback.focus.BambooAudioFocusController
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.mediaapp.core.telemetry.TelemetryEvent
import com.adrianrusu.mediaapp.core.telemetry.TelemetryLogger
import com.adrianrusu.mediaapp.core.telemetry.TelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals

class Media3EngineEffectExecutorTest {
    @Test
    fun `play effect prepares idle player and starts playback`() {
        val player = RecordingEffectPlayer(playbackState = Player.STATE_IDLE)
        val executor = effectExecutor(player = player)

        executor.execute(listOf(EngineEffect(type = EngineEffect.TYPE_PLAY)))

        assertEquals(listOf("prepare", "play"), player.calls)
    }

    @Test
    fun `pause stop seek and speed effects control player`() {
        val player = RecordingEffectPlayer(playbackState = Player.STATE_READY)
        val executor = effectExecutor(player = player)

        executor.execute(
            listOf(
                EngineEffect(type = EngineEffect.TYPE_PAUSE),
                EngineEffect(type = EngineEffect.TYPE_STOP),
                EngineEffect(type = EngineEffect.TYPE_SEEK, positionMillis = 12_345L),
                EngineEffect(type = EngineEffect.TYPE_SET_SPEED, speed = 1.25F)
            )
        )

        assertEquals(
            listOf(
                "pause",
                "stop",
                "seekTo:12345",
                "setPlaybackSpeed:1.25"
            ),
            player.calls
        )
    }

    @Test
    fun `audio focus effects call focus controller`() {
        val focusController = RecordingAudioFocusController()
        val executor = effectExecutor(focusController = focusController)

        executor.execute(
            listOf(
                EngineEffect(type = EngineEffect.TYPE_REQUEST_AUDIO_FOCUS),
                EngineEffect(type = EngineEffect.TYPE_ABANDON_AUDIO_FOCUS)
            )
        )

        assertEquals(listOf("request", "abandon"), focusController.calls)
    }

    @Test
    fun `missing effect payloads are ignored`() {
        val telemetrySink = RecordingEffectTelemetrySink()
        val player = RecordingEffectPlayer(playbackState = Player.STATE_READY)
        val executor = effectExecutor(
            player = player,
            telemetrySink = telemetrySink
        )

        executor.execute(
            listOf(
                EngineEffect(type = EngineEffect.TYPE_SEEK),
                EngineEffect(type = EngineEffect.TYPE_SET_SPEED)
            )
        )

        assertEquals(emptyList<String>(), player.calls)
        assertEquals(
            listOf(
                Media3EffectTelemetryEvents.EFFECT_RECEIVED,
                Media3EffectTelemetryEvents.EFFECT_IGNORED,
                Media3EffectTelemetryEvents.EFFECT_RECEIVED,
                Media3EffectTelemetryEvents.EFFECT_IGNORED
            ),
            telemetrySink.events.map { event -> event.name }
        )
    }
}

private fun effectExecutor(
    player: RecordingEffectPlayer = RecordingEffectPlayer(playbackState = Player.STATE_READY),
    focusController: RecordingAudioFocusController = RecordingAudioFocusController(),
    telemetrySink: TelemetrySink = TelemetrySink { }
): Media3EngineEffectExecutor = Media3EngineEffectExecutor(
    player = player,
    audioFocusController = focusController,
    telemetryLogger = TelemetryLogger(
        sink = telemetrySink,
        clock = { 42L }
    )
)

private class RecordingEffectPlayer(override val playbackState: Int) : Media3EffectPlayer {
    val calls = mutableListOf<String>()

    override fun prepare() {
        calls += "prepare"
    }

    override fun play() {
        calls += "play"
    }

    override fun pause() {
        calls += "pause"
    }

    override fun stop() {
        calls += "stop"
    }

    override fun seekTo(positionMillis: Long) {
        calls += "seekTo:$positionMillis"
    }

    override fun setPlaybackSpeed(speed: Float) {
        calls += "setPlaybackSpeed:$speed"
    }
}

private class RecordingAudioFocusController : BambooAudioFocusController {
    val calls = mutableListOf<String>()

    override fun requestAudioFocus() {
        calls += "request"
    }

    override fun abandonAudioFocus() {
        calls += "abandon"
    }
}

private class RecordingEffectTelemetrySink : TelemetrySink {
    val events = mutableListOf<TelemetryEvent>()

    override fun record(event: TelemetryEvent) {
        events += event
    }
}
