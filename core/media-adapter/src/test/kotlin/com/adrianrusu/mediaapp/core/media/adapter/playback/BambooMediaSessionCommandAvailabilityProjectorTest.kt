package com.adrianrusu.mediaapp.core.media.adapter.playback

import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackIntent
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackState
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BambooMediaSessionCommandAvailabilityProjectorTest {
    @Test
    fun `start projects current engine readiness`() {
        val repository = CommandAvailabilityRecordingPlaybackRepository(
            BambooPlaybackState(engineConnection = BambooEngineConnectionUiState.Ready)
        )
        val sink = RecordingCommandAvailabilitySink()
        val projector = BambooMediaSessionCommandAvailabilityProjector(
            playbackRepository = repository,
            sink = sink
        )

        projector.start()

        assertEquals(listOf(true), sink.values)
    }

    @Test
    fun `duplicate readiness is not projected again`() {
        val repository = CommandAvailabilityRecordingPlaybackRepository(BambooPlaybackState())
        val sink = RecordingCommandAvailabilitySink()
        val projector = BambooMediaSessionCommandAvailabilityProjector(
            playbackRepository = repository,
            sink = sink
        )

        projector.start()
        repository.push(BambooPlaybackState())

        assertEquals(listOf(false), sink.values)
    }

    @Test
    fun `readiness change updates command availability`() {
        val repository = CommandAvailabilityRecordingPlaybackRepository(BambooPlaybackState())
        val sink = RecordingCommandAvailabilitySink()
        val projector = BambooMediaSessionCommandAvailabilityProjector(
            playbackRepository = repository,
            sink = sink
        )

        projector.start()
        repository.push(BambooPlaybackState(engineConnection = BambooEngineConnectionUiState.Ready))

        assertEquals(listOf(false, true), sink.values)
    }

    @Test
    fun `close stops command availability updates`() {
        val repository = CommandAvailabilityRecordingPlaybackRepository(BambooPlaybackState())
        val sink = RecordingCommandAvailabilitySink()
        val projector = BambooMediaSessionCommandAvailabilityProjector(
            playbackRepository = repository,
            sink = sink
        )

        projector.start()
        projector.close()
        repository.push(BambooPlaybackState(engineConnection = BambooEngineConnectionUiState.Ready))

        assertEquals(listOf(false), sink.values)
    }
}

private class RecordingCommandAvailabilitySink : BambooMediaSessionCommandAvailabilitySink {
    val values = mutableListOf<Boolean>()

    override fun project(controlsEnabled: Boolean) {
        values += controlsEnabled
    }
}

private class CommandAvailabilityRecordingPlaybackRepository(initialState: BambooPlaybackState) :
    BambooPlaybackRepository {
    private val mutableState = MutableStateFlow(initialState)
    private val listeners = mutableSetOf<(BambooPlaybackState) -> Unit>()

    override val state: StateFlow<BambooPlaybackState> = mutableState

    override fun start() = Unit

    override fun dispatch(intent: BambooPlaybackIntent) = Unit

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
