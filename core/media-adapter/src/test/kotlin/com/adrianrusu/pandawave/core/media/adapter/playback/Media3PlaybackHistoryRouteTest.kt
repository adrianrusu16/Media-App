package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Media3PlaybackHistoryRouteTest {
    @Test
    fun `ended playback reports completion to PandaEngine route`() {
        val repository = HistoryRoutePlaybackRepository()
        val telemetrySink = RecordingHistoryTelemetrySink()
        val bridge = Media3PlaybackEngineBridge(
            repository,
            TelemetryLogger(sink = telemetrySink, clock = { 1L }),
            playbackMetricsProvider = PlaybackCompletionMetricsProvider {
                PlaybackCompletionMetrics(positionMillis = 750, durationMillis = 1_000)
            },
            playbackInstanceIdProvider = { 42L },
        )

        bridge.onPlaybackStateChanged(Player.STATE_ENDED)

        val event = repository.intents.single() as BambooPlaybackIntent.PlatformEvent
        assertEquals(EnginePlatformEvent.TYPE_PLAYBACK_COMPLETED, event.type)
        assertTrue(event.payload.orEmpty().contains("\"track_id\":\"track-1\""))
        assertTrue(event.payload.orEmpty().contains("\"duration_ms\":1000"))
        assertTrue(event.payload.orEmpty().contains("\"completion_ratio\":0.75"))
        assertEquals(
            mapOf(
                "playback_instance_id" to "42",
                "duration_millis" to "1000",
                "completion_ratio" to "0.75",
            ),
            telemetrySink.events.single {
                it.name == "media3.playback.completion.dispatched"
            }.attributes,
        )
        assertTrue(telemetrySink.events.none { event ->
            event.attributes.values.any { value -> value == "track-1" }
        })
    }
}

private class RecordingHistoryTelemetrySink : TelemetrySink {
    val events = mutableListOf<TelemetryEvent>()

    override fun record(event: TelemetryEvent) {
        events += event
    }
}

private class HistoryRoutePlaybackRepository : BambooPlaybackRepository {
    override val state: StateFlow<BambooPlaybackState> = MutableStateFlow(
        BambooPlaybackState(mediaId = "track-1", durationMillis = 1_000, positionMillis = 0)
    )
    val intents = mutableListOf<BambooPlaybackIntent>()
    override fun start() = Unit
    override fun dispatch(intent: BambooPlaybackIntent) { intents += intent }
    override fun observe(listener: (BambooPlaybackState) -> Unit) = AutoCloseable { }
    override fun observeEffects(listener: (List<EngineEffect>) -> Unit) = AutoCloseable { }
    override fun close() = Unit
}
