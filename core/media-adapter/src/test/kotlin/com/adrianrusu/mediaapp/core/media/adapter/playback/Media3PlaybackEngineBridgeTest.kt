package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackIntent
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackState
import com.adrianrusu.mediaapp.core.telemetry.TelemetryEvent
import com.adrianrusu.mediaapp.core.telemetry.TelemetryLogger
import com.adrianrusu.mediaapp.core.telemetry.TelemetrySink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class Media3PlaybackEngineBridgeTest {
    @Test
    fun bootstrapStartsPlaybackRepository() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.bootstrap()

        assertEquals(1, repository.startCount)
    }

    @Test
    fun closeStopsPlaybackRepository() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.close()

        assertEquals(1, repository.closeCount)
    }

    @Test
    fun playWhenReadyChangeDispatchesPlaybackIntents() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        bridge.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)

        assertEquals(
            listOf(BambooPlaybackIntent.Play, BambooPlaybackIntent.Pause),
            repository.intents
        )
    }

    @Test
    fun projectedPlatformPlaybackStateDoesNotDispatchPlaybackIntent() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.projectPlatformPlaybackState {
            bridge.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        }

        assertEquals(emptyList<BambooPlaybackIntent>(), repository.intents)
    }

    @Test
    fun playerSkipCommandsDispatchThroughPlaybackRepository() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.dispatchPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
        bridge.dispatchPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)

        assertEquals(
            listOf(
                BambooPlaybackIntent.SkipPrevious,
                BambooPlaybackIntent.SkipNext
            ),
            repository.intents
        )
    }

    @Test
    fun unrelatedPlayerCommandIsIgnoredByPlaybackRepository() {
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())

        bridge.dispatchPlayerCommand(Player.COMMAND_SEEK_FORWARD)

        assertEquals(emptyList<BambooPlaybackIntent>(), repository.intents)
    }

    @Test
    fun telemetryRecordsDispatchedIgnoredAndProjectedMedia3Commands() {
        val telemetrySink = RecordingTelemetrySink()
        val repository = RecordingPlaybackRepository()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger(telemetrySink))

        bridge.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        bridge.projectPlatformPlaybackState {
            bridge.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        }
        bridge.dispatchPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
        bridge.dispatchPlayerCommand(Player.COMMAND_SEEK_FORWARD)

        assertEquals(
            listOf(
                Media3PlaybackTelemetryEvents.PLAY_WHEN_READY_RECEIVED,
                Media3PlaybackTelemetryEvents.PLAY_WHEN_READY_IGNORED,
                Media3PlaybackTelemetryEvents.PLAYER_COMMAND_DISPATCHED,
                Media3PlaybackTelemetryEvents.PLAYER_COMMAND_IGNORED
            ),
            telemetrySink.events.map { it.name }
        )
        assertEquals("true", telemetrySink.events[0].attributes["play_when_ready"])
        assertEquals("platform_projection", telemetrySink.events[1].attributes["source"])
        assertEquals("skip_next", telemetrySink.events[2].attributes["intent"])
        assertEquals(Player.COMMAND_SEEK_FORWARD.toString(), telemetrySink.events[3].attributes["player_command"])
    }
}

private class RecordingPlaybackRepository : BambooPlaybackRepository {
    private val mutableState = MutableStateFlow(BambooPlaybackState())

    var startCount = 0
    var closeCount = 0
    val intents = mutableListOf<BambooPlaybackIntent>()

    override val state: StateFlow<BambooPlaybackState> = mutableState

    override fun start() {
        startCount += 1
    }

    override fun dispatch(intent: BambooPlaybackIntent) {
        intents += intent
    }

    override fun observe(listener: (BambooPlaybackState) -> Unit): AutoCloseable {
        listener(state.value)
        return AutoCloseable { }
    }

    override fun close() {
        closeCount += 1
    }
}

private fun testTelemetryLogger(sink: TelemetrySink = TelemetrySink { }): TelemetryLogger = TelemetryLogger(
    sink = sink,
    clock = { 42L }
)

private class RecordingTelemetrySink : TelemetrySink {
    val events = mutableListOf<TelemetryEvent>()

    override fun record(event: TelemetryEvent) {
        events += event
    }
}
