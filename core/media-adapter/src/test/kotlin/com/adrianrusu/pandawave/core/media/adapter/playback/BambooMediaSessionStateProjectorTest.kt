package com.adrianrusu.pandawave.core.media.adapter.playback

import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackStatus
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BambooMediaSessionStateProjectorTest {
    @Test
    fun `start projects current playback state`() {
        val repository = ProjectorRecordingPlaybackRepository(
            BambooPlaybackState(
                mediaId = "track-1",
                title = "Bamboo Drive",
                artist = "PandaWave",
                playbackStatus = BambooPlaybackStatus.Playing
            )
        )
        val sink = RecordingMediaSessionStateSink()
        val bridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())
        val projector = BambooMediaSessionStateProjector(
            playbackRepository = repository,
            sink = sink,
            playbackEngineBridge = bridge
        )

        projector.start()

        assertEquals(1, sink.projections.size)
        assertEquals("track-1", sink.projections.single().mediaItem.mediaId)
        assertEquals(true, sink.projections.single().playWhenReady)
    }

    @Test
    fun `duplicate state is not projected again`() {
        val state = BambooPlaybackState(
            mediaId = "track-1",
            title = "Bamboo Drive",
            artist = "PandaWave",
            playbackStatus = BambooPlaybackStatus.Paused,
            positionMillis = 1_000L
        )
        val repository = ProjectorRecordingPlaybackRepository(state)
        val sink = RecordingMediaSessionStateSink()
        val projector = BambooMediaSessionStateProjector(
            playbackRepository = repository,
            sink = sink,
            playbackEngineBridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())
        )

        projector.start()
        repository.push(state)

        assertEquals(1, sink.projections.size)
    }

    @Test
    fun `position-only changes are not projected to media3`() {
        val state = BambooPlaybackState(
            mediaId = "track-1",
            title = "Bamboo Drive",
            artist = "PandaWave",
            playbackStatus = BambooPlaybackStatus.Playing,
            positionMillis = 1_000L
        )
        val repository = ProjectorRecordingPlaybackRepository(state)
        val sink = RecordingMediaSessionStateSink()
        val projector = BambooMediaSessionStateProjector(
            playbackRepository = repository,
            sink = sink,
            playbackEngineBridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())
        )

        projector.start()
        repository.push(state.copy(positionMillis = 4_000L))

        assertEquals(1, sink.projections.size)
        assertEquals(1_000L, sink.projections.single().positionMillis)
    }

    @Test
    fun `metadata changes are projected without a position change`() {
        val state = BambooPlaybackState(
            mediaId = "track-1",
            title = "Bamboo Drive",
            artist = "PandaWave",
            playbackStatus = BambooPlaybackStatus.Playing,
            positionMillis = 1_000L
        )
        val repository = ProjectorRecordingPlaybackRepository(state)
        val sink = RecordingMediaSessionStateSink()
        val projector = BambooMediaSessionStateProjector(
            playbackRepository = repository,
            sink = sink,
            playbackEngineBridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())
        )

        projector.start()
        repository.push(state.copy(title = "Canopy Drift", positionMillis = 8_000L))

        assertEquals(
            listOf("Bamboo Drive", "Canopy Drift"),
            sink.projections.map {
                it.mediaItem.mediaMetadata.title.toString()
            }
        )
    }

    @Test
    fun `close stops projection updates`() {
        val repository = ProjectorRecordingPlaybackRepository(BambooPlaybackState())
        val sink = RecordingMediaSessionStateSink()
        val projector = BambooMediaSessionStateProjector(
            playbackRepository = repository,
            sink = sink,
            playbackEngineBridge = Media3PlaybackEngineBridge(repository, testTelemetryLogger())
        )

        projector.start()
        projector.close()
        repository.push(
            BambooPlaybackState(
                mediaId = "track-2",
                title = "Quiet Cabin",
                artist = "PandaWave",
                playbackStatus = BambooPlaybackStatus.Playing
            )
        )

        assertEquals(1, sink.projections.size)
    }
}

private fun testTelemetryLogger(): TelemetryLogger = TelemetryLogger(
    sink = TelemetrySink { },
    clock = { 42L }
)

private class RecordingMediaSessionStateSink : BambooMediaSessionStateSink {
    val projections = mutableListOf<BambooMediaSessionStateProjection>()

    override fun project(projection: BambooMediaSessionStateProjection) {
        projections += projection
    }
}

private class ProjectorRecordingPlaybackRepository(initialState: BambooPlaybackState) : BambooPlaybackRepository {
    private val mutableState = MutableStateFlow(initialState)
    private val listeners = mutableSetOf<(BambooPlaybackState) -> Unit>()

    val intents = mutableListOf<BambooPlaybackIntent>()

    override val state: StateFlow<BambooPlaybackState> = mutableState

    override fun start() = Unit

    override fun dispatch(intent: BambooPlaybackIntent) {
        intents += intent
    }

    override fun observe(listener: (BambooPlaybackState) -> Unit): AutoCloseable {
        listeners += listener
        listener(state.value)
        return AutoCloseable {
            listeners -= listener
        }
    }

    override fun observeEffects(listener: (List<EngineEffect>) -> Unit): AutoCloseable = AutoCloseable { }

    override fun close() = Unit

    fun push(state: BambooPlaybackState) {
        mutableState.value = state
        listeners.toList().forEach { listener ->
            listener(state)
        }
    }
}
