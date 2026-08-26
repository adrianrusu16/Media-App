package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.adrianrusu.pandawave.core.media.adapter.playback.focus.BambooAudioFocusController
import com.adrianrusu.pandawave.core.media.adapter.playback.focus.BambooAudioFocusRequestResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Media3EngineEffectExecutorTest {
    @Test
    fun `delayed audio focus blocks the paired play effect and records the reason`() {
        val player = RecordingEffectPlayer(playbackState = Player.STATE_READY)
        val focusController = RecordingAudioFocusController(BambooAudioFocusRequestResult.Delayed)
        val telemetrySink = RecordingEffectTelemetrySink()
        val executor = effectExecutor(
            player = player,
            focusController = focusController,
            telemetrySink = telemetrySink,
        )

        executor.execute(
            listOf(
                EngineEffect(type = EngineEffect.TYPE_REQUEST_AUDIO_FOCUS),
                EngineEffect(type = EngineEffect.TYPE_PLAY),
            )
        )

        assertEquals(listOf("request"), focusController.calls)
        assertEquals(emptyList<String>(), player.calls)
        assertEquals(
            BambooAudioFocusRequestResult.Delayed.wireValue,
            telemetrySink.events
                .single { it.name == Media3EffectTelemetryEvents.AUDIO_FOCUS_REQUESTED }
                .attributes[Media3EffectTelemetryAttributes.RESULT],
        )
        assertEquals(
            Media3EffectTelemetryValues.AUDIO_FOCUS_NOT_GRANTED,
            telemetrySink.events
                .last { it.name == Media3EffectTelemetryEvents.EFFECT_IGNORED }
                .attributes[Media3EffectTelemetryAttributes.REASON],
        )
    }

    @Test
    fun `prepare playback source effect sets projected media item before play`() {
        val player = RecordingEffectPlayer(playbackState = Player.STATE_IDLE)
        val executor = effectExecutor(
            player = player,
            currentProjection = {
                BambooMediaSessionStateProjection(
                    mediaItem = MediaItem.Builder()
                        .setMediaId("track-1")
                        .setMimeType("audio/mpeg")
                        .build(),
                    playWhenReady = false,
                    positionMillis = 9_000L
                )
            }
        )

        executor.execute(
            listOf(
                EngineEffect(
                    type = EngineEffect.TYPE_PREPARE_PLAYBACK_SOURCE,
                    mediaId = "track-1",
                    playbackInstanceId = 42L
                ),
                EngineEffect(
                    type = EngineEffect.TYPE_UPDATE_METADATA,
                    mediaId = "track-1"
                ),
                EngineEffect(type = EngineEffect.TYPE_PLAY)
            )
        )

        assertEquals(
            listOf(
                "setMediaItem:track-1:9000",
                "prepare",
                "updateMediaMetadata:none",
                "play"
            ),
            player.calls
        )
    }

    @Test
    fun `prepare playback source effect uses the engine start position`() {
        val player = RecordingEffectPlayer(playbackState = Player.STATE_IDLE)
        val executor = effectExecutor(
            player = player,
            currentProjection = {
                BambooMediaSessionStateProjection(
                    mediaItem = MediaItem.Builder()
                        .setMediaId("track-2")
                        .setMimeType("audio/mpeg")
                        .build(),
                    playWhenReady = false,
                    positionMillis = 55_000L
                )
            }
        )

        executor.execute(
            listOf(
                EngineEffect(
                    type = EngineEffect.TYPE_PREPARE_PLAYBACK_SOURCE,
                    mediaId = "track-2",
                    playbackInstanceId = 7L,
                    positionMillis = 0L
                )
            )
        )

        assertEquals(listOf("setMediaItem:track-2:0", "prepare"), player.calls)
    }

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
    fun `stale prepare playback source effect is ignored`() {
        val telemetrySink = RecordingEffectTelemetrySink()
        val player = RecordingEffectPlayer(playbackState = Player.STATE_READY)
        val executor = effectExecutor(
            player = player,
            telemetrySink = telemetrySink,
            currentProjection = {
                BambooMediaSessionStateProjection(
                    mediaItem = MediaItem.Builder()
                        .setMediaId("track-2")
                        .build(),
                    playWhenReady = false,
                    positionMillis = 0L
                )
            }
        )

        executor.execute(
            listOf(
                EngineEffect(
                    type = EngineEffect.TYPE_PREPARE_PLAYBACK_SOURCE,
                    mediaId = "track-1"
                )
            )
        )

        assertEquals(emptyList<String>(), player.calls)
        assertEquals(Media3EffectTelemetryEvents.EFFECT_IGNORED, telemetrySink.events.last().name)
        assertEquals(
            Media3EffectTelemetryValues.STALE_PROJECTION,
            telemetrySink.events.last().attributes[Media3EffectTelemetryAttributes.REASON]
        )
        assertEquals(TelemetryModule.Media3, telemetrySink.events.last().module)
        assertEquals(
            "true",
            telemetrySink.events.last().attributes[Media3EffectTelemetryAttributes.MEDIA_ID_PRESENT]
        )
        assertFalse(telemetrySink.events.last().attributes.values.contains("track-1"))
    }

    @Test
    fun `decoder recovery recreates the player and restores the requested position`() {
        val player = RecordingEffectPlayer(playbackState = Player.STATE_IDLE)
        var recreations = 0
        val executor = Media3EngineEffectExecutor(
            player = { player },
            audioFocusController = RecordingAudioFocusController(),
            telemetryLogger = TelemetryLogger(sink = TelemetrySink { }, clock = { 42L }),
            currentProjection = {
                BambooMediaSessionStateProjection(
                    mediaItem = MediaItem.Builder().setMediaId("track-1").build(),
                    playWhenReady = true,
                    positionMillis = 1_000L
                )
            },
            recreatePlayer = { recreations += 1 }
        )

        executor.execute(
            listOf(
                EngineEffect(
                    type = EngineEffect.TYPE_RECREATE_PLAYER_AND_LOAD,
                    mediaId = "track-1",
                    positionMillis = 9_000L,
                    playbackInstanceId = 42L
                ),
                EngineEffect(type = EngineEffect.TYPE_PLAY)
            )
        )

        assertEquals(1, recreations)
        assertEquals(listOf("setMediaItem:track-1:9000", "prepare", "play"), player.calls)
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
    telemetrySink: TelemetrySink = TelemetrySink { },
    currentProjection: () -> BambooMediaSessionStateProjection? = { null }
): Media3EngineEffectExecutor = Media3EngineEffectExecutor(
    player = { player },
    audioFocusController = focusController,
    telemetryLogger = TelemetryLogger(
        sink = telemetrySink,
        clock = { 42L }
    ),
    currentProjection = currentProjection
)

private class RecordingEffectPlayer(override var playbackState: Int) : Media3EffectPlayer {
    val calls = mutableListOf<String>()

    override fun setMediaItem(mediaItem: MediaItem, positionMillis: Long) {
        calls += "setMediaItem:${mediaItem.mediaId}:$positionMillis"
    }

    override fun prepare() {
        calls += "prepare"
        playbackState = Player.STATE_BUFFERING
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

    override fun updateMediaMetadata(metadata: androidx.media3.common.MediaMetadata) {
        calls += "updateMediaMetadata:${metadata.title ?: "none"}"
    }
}

private class RecordingAudioFocusController(
    private val requestResult: BambooAudioFocusRequestResult = BambooAudioFocusRequestResult.Granted,
) : BambooAudioFocusController {
    val calls = mutableListOf<String>()

    override fun requestAudioFocus(): BambooAudioFocusRequestResult {
        calls += "request"
        return requestResult
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
