package com.adrianrusu.pandawave.feature.nowplaying.presentation

import com.adrianrusu.pandawave.core.audio.visualizer.AmbientAudioVisualizer
import com.adrianrusu.pandawave.core.audio.visualizer.AmbientVisualizerAvailability
import com.adrianrusu.pandawave.core.audio.visualizer.AudioSessionRepository
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceRepository
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceState
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferences
import com.adrianrusu.pandawave.core.ui.interaction.MonotonicClock
import com.adrianrusu.pandawave.core.ui.interaction.UserInteractionTracker
import com.adrianrusu.pandawave.feature.nowplaying.domain.DispatchNowPlayingIntentUseCase
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingIntent
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingPlaybackState
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingRepository
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingState
import com.adrianrusu.pandawave.feature.nowplaying.domain.ObserveNowPlayingStateUseCase
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `eligible inactivity starts visualization and manual skip resets the timer`() = runTest(dispatcher) {
        val repository = RecordingNowPlayingRepository(
            NowPlayingState(
                mediaId = "track-1",
                playbackState = NowPlayingPlaybackState.Playing,
                ambientSafetyPermitted = true
            )
        )
        val visualizer = RecordingAmbientAudioVisualizer()
        val clock = MutableMonotonicClock(nowMillis = 1_000L)
        val viewModel = NowPlayingViewModel(
            observeState = ObserveNowPlayingStateUseCase(repository),
            repository = repository,
            dispatchIntent = DispatchNowPlayingIntentUseCase(repository),
            audioSessionRepository = RecordingAudioSessionRepository(),
            visualizer = visualizer,
            ambientModePreferenceRepository = RecordingAmbientModePreferenceRepository(),
            interactionTracker = UserInteractionTracker(),
            clock = clock
        )

        viewModel.onRouteVisibilityChanged(true)
        viewModel.onLifecycleResumedChanged(true)
        runCurrent()

        assertIs<AmbientModeState.WaitingForInactivity>(viewModel.ambientModeState.value)
        assertEquals(0, visualizer.startCount)

        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(AmbientModeState.AmbientVisualizing, viewModel.ambientModeState.value)
        assertEquals(1, visualizer.startCount)

        clock.nowMillis = 6_000L
        viewModel.onIntent(NowPlayingIntent.SkipNext)
        runCurrent()

        assertIs<AmbientModeState.WaitingForInactivity>(viewModel.ambientModeState.value)
        assertEquals(1, visualizer.stopCount)
        assertEquals(listOf<NowPlayingIntent>(NowPlayingIntent.SkipNext), repository.intents)
    }
}

private class RecordingNowPlayingRepository(initialState: NowPlayingState) : NowPlayingRepository {
    override val state = MutableStateFlow(initialState)
    val intents = mutableListOf<NowPlayingIntent>()

    override fun start() = Unit

    override fun dispatch(intent: NowPlayingIntent) {
        intents += intent
    }

    override fun close() = Unit
}

private class RecordingAmbientModePreferenceRepository : AmbientModePreferenceRepository {
    override val state = MutableStateFlow<AmbientModePreferenceState>(
        AmbientModePreferenceState.Ready(
            AmbientModePreferences(
                enabled = true,
                timeoutSeconds = 5
            )
        )
    )

    override suspend fun setEnabled(enabled: Boolean) = Unit

    override suspend fun setTimeoutSeconds(timeoutSeconds: Int) = Unit
}

private class RecordingAudioSessionRepository : AudioSessionRepository {
    override val audioSessionId: StateFlow<Int?> = MutableStateFlow(42)
}

private class RecordingAmbientAudioVisualizer : AmbientAudioVisualizer {
    override val amplitudes = MutableStateFlow(FloatArray(0))
    override val availability = MutableStateFlow<AmbientVisualizerAvailability>(
        AmbientVisualizerAvailability.Ready
    )

    var startCount = 0
    var stopCount = 0

    override fun attachToAudioSession(audioSessionId: Int) = Unit

    override fun start() {
        startCount += 1
    }

    override fun stop() {
        stopCount += 1
    }

    override fun close() = Unit
}

private class MutableMonotonicClock(var nowMillis: Long) : MonotonicClock {
    override fun nowMillis(): Long = nowMillis
}
