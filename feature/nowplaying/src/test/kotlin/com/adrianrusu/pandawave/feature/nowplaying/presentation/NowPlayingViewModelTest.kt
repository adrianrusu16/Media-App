package com.adrianrusu.pandawave.feature.nowplaying.presentation

import com.adrianrusu.pandawave.core.audio.visualizer.AmbientAmplitudeSource
import com.adrianrusu.pandawave.core.audio.visualizer.AmbientAudioVisualizer
import com.adrianrusu.pandawave.core.audio.visualizer.AmbientVisualizerAvailability
import com.adrianrusu.pandawave.core.audio.visualizer.AudioSessionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionState
import com.adrianrusu.pandawave.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceRepository
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceState
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferences
import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import com.adrianrusu.pandawave.core.telemetry.TelemetrySeverity
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
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
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModelTest {
    private lateinit var dispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `eligible inactivity enters visualization and manual skip resets the timer`() = runTest(dispatcher) {
        val repository = RecordingNowPlayingRepository(
            NowPlayingState(
                mediaId = "track-1",
                playbackState = NowPlayingPlaybackState.Playing,
                isParked = true,
                isUxUnrestricted = true
            )
        )
        val visualizer = RecordingAmbientAudioVisualizer()
        val telemetrySink = RecordingTelemetrySink()
        val clock = MutableMonotonicClock(nowMillis = 1_000L)
        val viewModel = NowPlayingViewModel(
            observeState = ObserveNowPlayingStateUseCase(repository),
            repository = repository,
            dispatchIntent = DispatchNowPlayingIntentUseCase(repository),
            audioSessionRepository = RecordingAudioSessionRepository(),
            visualizer = visualizer,
            sleepingAmplitudeSource = RecordingAmbientAmplitudeSource(),
            visualizerPermissionRepository = RecordingVisualizerPermissionRepository(),
            ambientModePreferenceRepository = RecordingAmbientModePreferenceRepository(),
            interactionTracker = UserInteractionTracker(),
            clock = clock,
            telemetryLogger = TelemetryLogger(telemetrySink)
        )

        viewModel.onRouteVisibilityChanged(true)
        viewModel.onLifecycleResumedChanged(true)
        runCurrent()

        assertIs<AmbientModeState.WaitingForInactivity>(viewModel.ambientModeState.value)
        assertEquals(1, visualizer.startCount)

        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(AmbientModeState.AmbientVisualizing, viewModel.ambientModeState.value)
        assertEquals(1, visualizer.startCount)
        assertEquals(
            listOf(AmbientTelemetryEvents.ENTERED),
            telemetrySink.events.map(TelemetryEvent::name)
        )

        clock.nowMillis = 6_000L
        viewModel.onIntent(NowPlayingIntent.SkipNext)
        runCurrent()

        assertIs<AmbientModeState.WaitingForInactivity>(viewModel.ambientModeState.value)
        assertEquals(0, visualizer.stopCount)
        assertEquals(listOf<NowPlayingIntent>(NowPlayingIntent.SkipNext), repository.intents)
        assertEquals(
            listOf(AmbientTelemetryEvents.ENTERED, AmbientTelemetryEvents.EXITED),
            telemetrySink.events.map(TelemetryEvent::name)
        )
        assertEquals(
            AmbientTelemetryExitReasons.INTERACTION,
            telemetrySink.events.last().attributes[AmbientTelemetryAttributes.REASON]
        )
        assertTrue(telemetrySink.events.all { it.module == TelemetryModule.Ambient })
    }

    @Test
    fun `denied permission stays interactive without starting an amplitude source`() = runTest(dispatcher) {
        val repository = RecordingNowPlayingRepository(
            NowPlayingState(
                mediaId = "track-1",
                playbackState = NowPlayingPlaybackState.Playing,
                isParked = true,
                isUxUnrestricted = true
            )
        )
        val visualizer = RecordingAmbientAudioVisualizer()
        val viewModel = NowPlayingViewModel(
            observeState = ObserveNowPlayingStateUseCase(repository),
            repository = repository,
            dispatchIntent = DispatchNowPlayingIntentUseCase(repository),
            audioSessionRepository = RecordingAudioSessionRepository(),
            visualizer = visualizer,
            sleepingAmplitudeSource = RecordingAmbientAmplitudeSource(),
            visualizerPermissionRepository = RecordingVisualizerPermissionRepository(
                VisualizerPermissionState.Denied(canRequest = true)
            ),
            ambientModePreferenceRepository = RecordingAmbientModePreferenceRepository(),
            interactionTracker = UserInteractionTracker(),
            clock = MutableMonotonicClock(nowMillis = 1_000L),
            telemetryLogger = testTelemetryLogger()
        )

        viewModel.onRouteVisibilityChanged(true)
        viewModel.onLifecycleResumedChanged(true)
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(AmbientModeState.Interactive, viewModel.ambientModeState.value)
        assertEquals(0, visualizer.startCount)
        assertEquals(emptyList(), visualizer.attachedAudioSessions)
    }

    @Test
    fun `ineligible ambient state does not start playback visualizer`() = runTest(dispatcher) {
        val repository = RecordingNowPlayingRepository(
            NowPlayingState(
                mediaId = "track-1",
                playbackState = NowPlayingPlaybackState.Playing,
                isParked = false,
                isUxUnrestricted = true
            )
        )
        val visualizer = RecordingAmbientAudioVisualizer()
        val viewModel = NowPlayingViewModel(
            observeState = ObserveNowPlayingStateUseCase(repository),
            repository = repository,
            dispatchIntent = DispatchNowPlayingIntentUseCase(repository),
            audioSessionRepository = RecordingAudioSessionRepository(),
            visualizer = visualizer,
            sleepingAmplitudeSource = RecordingAmbientAmplitudeSource(),
            visualizerPermissionRepository = RecordingVisualizerPermissionRepository(),
            ambientModePreferenceRepository = RecordingAmbientModePreferenceRepository(),
            interactionTracker = UserInteractionTracker(),
            clock = MutableMonotonicClock(nowMillis = 1_000L),
            telemetryLogger = testTelemetryLogger()
        )

        viewModel.onRouteVisibilityChanged(true)
        viewModel.onLifecycleResumedChanged(true)
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(AmbientModeState.Interactive, viewModel.ambientModeState.value)
        assertEquals(0, visualizer.startCount)
        assertEquals(0, visualizer.stopCount)
    }

    @Test
    fun `ambient wake exits without touching playback visualizer or dispatching playback`() = runTest(dispatcher) {
        val repository = RecordingNowPlayingRepository(
            NowPlayingState(
                mediaId = "track-1",
                playbackState = NowPlayingPlaybackState.Playing,
                isParked = true,
                isUxUnrestricted = true
            )
        )
        val visualizer = RecordingAmbientAudioVisualizer()
        val viewModel = NowPlayingViewModel(
            observeState = ObserveNowPlayingStateUseCase(repository),
            repository = repository,
            dispatchIntent = DispatchNowPlayingIntentUseCase(repository),
            audioSessionRepository = RecordingAudioSessionRepository(),
            visualizer = visualizer,
            sleepingAmplitudeSource = RecordingAmbientAmplitudeSource(),
            visualizerPermissionRepository = RecordingVisualizerPermissionRepository(),
            ambientModePreferenceRepository = RecordingAmbientModePreferenceRepository(),
            interactionTracker = UserInteractionTracker(),
            clock = MutableMonotonicClock(nowMillis = 1_000L),
            telemetryLogger = testTelemetryLogger()
        )

        viewModel.onRouteVisibilityChanged(true)
        viewModel.onLifecycleResumedChanged(true)
        runCurrent()

        assertEquals(1, visualizer.startCount)
        assertEquals(0, visualizer.stopCount)

        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(AmbientModeState.AmbientVisualizing, viewModel.ambientModeState.value)
        assertEquals(1, visualizer.startCount)
        assertEquals(0, visualizer.stopCount)

        viewModel.onUserInteraction()
        runCurrent()

        assertIs<AmbientModeState.WaitingForInactivity>(viewModel.ambientModeState.value)
        assertEquals(1, visualizer.startCount)
        assertEquals(0, visualizer.stopCount)
        assertEquals(emptyList(), repository.intents)
    }

    @Test
    fun `visualizer failure is recorded once without frame payload noise`() = runTest(dispatcher) {
        val repository = RecordingNowPlayingRepository(
            NowPlayingState(
                mediaId = "private-track-id",
                artworkUri = "content://private-artwork",
                title = "Private title",
                artist = "Private artist",
                playbackState = NowPlayingPlaybackState.Playing,
                isParked = true,
                isUxUnrestricted = true
            )
        )
        val visualizer = RecordingAmbientAudioVisualizer()
        val telemetrySink = RecordingTelemetrySink()
        val viewModel = NowPlayingViewModel(
            observeState = ObserveNowPlayingStateUseCase(repository),
            repository = repository,
            dispatchIntent = DispatchNowPlayingIntentUseCase(repository),
            audioSessionRepository = RecordingAudioSessionRepository(),
            visualizer = visualizer,
            sleepingAmplitudeSource = RecordingAmbientAmplitudeSource(),
            visualizerPermissionRepository = RecordingVisualizerPermissionRepository(),
            ambientModePreferenceRepository = RecordingAmbientModePreferenceRepository(),
            interactionTracker = UserInteractionTracker(),
            clock = MutableMonotonicClock(nowMillis = 1_000L),
            telemetryLogger = TelemetryLogger(telemetrySink)
        )

        viewModel.onRouteVisibilityChanged(true)
        viewModel.onLifecycleResumedChanged(true)
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()

        visualizer.availability.value = AmbientVisualizerAvailability.Unavailable(
            AmbientVisualizerAvailability.Reason.RuntimeFailed
        )
        runCurrent()
        visualizer.amplitudes.value = floatArrayOf(0.1F, 0.9F)
        visualizer.amplitudes.value = floatArrayOf(0.2F, 0.8F)
        visualizer.availability.value = AmbientVisualizerAvailability.Unavailable(
            AmbientVisualizerAvailability.Reason.RuntimeFailed
        )
        runCurrent()

        assertEquals(
            listOf(
                AmbientTelemetryEvents.ENTERED,
                AmbientTelemetryEvents.VISUALIZER_UNAVAILABLE,
                AmbientTelemetryEvents.EXITED
            ),
            telemetrySink.events.map(TelemetryEvent::name)
        )
        val unavailableEvent = telemetrySink.events.single {
            it.name == AmbientTelemetryEvents.VISUALIZER_UNAVAILABLE
        }
        assertEquals(
            AmbientTelemetryVisualizerReasons.RUNTIME_FAILED,
            unavailableEvent.attributes[AmbientTelemetryAttributes.REASON]
        )
        val telemetryPayload = telemetrySink.events
            .flatMap { event -> listOf(event.name) + event.attributes.keys + event.attributes.values }
            .joinToString()
            .lowercase()
        assertTrue("42" !in telemetryPayload)
        assertTrue("private-track-id" !in telemetryPayload)
        assertTrue("private title" !in telemetryPayload)
        assertTrue("private artist" !in telemetryPayload)
        assertTrue("content://private-artwork" !in telemetryPayload)
        assertTrue("0.1" !in telemetryPayload)
        assertTrue("0.9" !in telemetryPayload)
    }

    @Test
    fun `safety loss records a categorized exit`() = runTest(dispatcher) {
        val repository = RecordingNowPlayingRepository(
            NowPlayingState(
                playbackState = NowPlayingPlaybackState.Playing,
                isParked = true,
                isUxUnrestricted = true
            )
        )
        val telemetrySink = RecordingTelemetrySink()
        val viewModel = NowPlayingViewModel(
            observeState = ObserveNowPlayingStateUseCase(repository),
            repository = repository,
            dispatchIntent = DispatchNowPlayingIntentUseCase(repository),
            audioSessionRepository = RecordingAudioSessionRepository(),
            visualizer = RecordingAmbientAudioVisualizer(),
            sleepingAmplitudeSource = RecordingAmbientAmplitudeSource(),
            visualizerPermissionRepository = RecordingVisualizerPermissionRepository(),
            ambientModePreferenceRepository = RecordingAmbientModePreferenceRepository(),
            interactionTracker = UserInteractionTracker(),
            clock = MutableMonotonicClock(nowMillis = 1_000L),
            telemetryLogger = TelemetryLogger(telemetrySink)
        )

        viewModel.onRouteVisibilityChanged(true)
        viewModel.onLifecycleResumedChanged(true)
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()

        repository.state.value = repository.state.value.copy(isParked = false)
        runCurrent()

        val exit = telemetrySink.events.single { it.name == AmbientTelemetryEvents.EXITED }
        assertEquals(AmbientTelemetryExitReasons.SAFETY_LOST, exit.attributes[AmbientTelemetryAttributes.REASON])
        assertEquals(TelemetrySeverity.Info, exit.severity)
    }

    @Test
    fun `unexpected platform availability loss records a warning exit`() = runTest(dispatcher) {
        val repository = RecordingNowPlayingRepository(
            NowPlayingState(
                playbackState = NowPlayingPlaybackState.Playing,
                isParked = true,
                isUxUnrestricted = true
            )
        )
        val telemetrySink = RecordingTelemetrySink()
        val viewModel = NowPlayingViewModel(
            observeState = ObserveNowPlayingStateUseCase(repository),
            repository = repository,
            dispatchIntent = DispatchNowPlayingIntentUseCase(repository),
            audioSessionRepository = RecordingAudioSessionRepository(),
            visualizer = RecordingAmbientAudioVisualizer(),
            sleepingAmplitudeSource = RecordingAmbientAmplitudeSource(),
            visualizerPermissionRepository = RecordingVisualizerPermissionRepository(),
            ambientModePreferenceRepository = RecordingAmbientModePreferenceRepository(),
            interactionTracker = UserInteractionTracker(),
            clock = MutableMonotonicClock(nowMillis = 1_000L),
            telemetryLogger = TelemetryLogger(telemetrySink)
        )

        viewModel.onRouteVisibilityChanged(true)
        viewModel.onLifecycleResumedChanged(true)
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()

        repository.state.value = repository.state.value.copy(
            isParked = false,
            engineConnection = BambooEngineConnectionUiState.Unavailable
        )
        runCurrent()

        val exit = telemetrySink.events.single { it.name == AmbientTelemetryEvents.EXITED }
        assertEquals(AmbientTelemetryExitReasons.SAFETY_LOST, exit.attributes[AmbientTelemetryAttributes.REASON])
        assertEquals(TelemetrySeverity.Warning, exit.severity)
    }

    @Test
    fun `idle inactivity starts the sleeping amplitude source`() = runTest(dispatcher) {
        val repository = RecordingNowPlayingRepository(
            NowPlayingState(
                playbackState = NowPlayingPlaybackState.Idle,
                isParked = true,
                isUxUnrestricted = true
            )
        )
        val sleepingSource = RecordingAmbientAmplitudeSource()
        val viewModel = NowPlayingViewModel(
            observeState = ObserveNowPlayingStateUseCase(repository),
            repository = repository,
            dispatchIntent = DispatchNowPlayingIntentUseCase(repository),
            audioSessionRepository = RecordingAudioSessionRepository(),
            visualizer = RecordingAmbientAudioVisualizer(),
            sleepingAmplitudeSource = sleepingSource,
            visualizerPermissionRepository = RecordingVisualizerPermissionRepository(),
            ambientModePreferenceRepository = RecordingAmbientModePreferenceRepository(),
            interactionTracker = UserInteractionTracker(),
            clock = MutableMonotonicClock(nowMillis = 1_000L),
            telemetryLogger = testTelemetryLogger()
        )

        viewModel.onRouteVisibilityChanged(true)
        viewModel.onLifecycleResumedChanged(true)
        runCurrent()
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals(AmbientModeState.AmbientSleeping, viewModel.ambientModeState.value)
        assertEquals(1, sleepingSource.startCount)
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

private class RecordingVisualizerPermissionRepository(
    initialState: VisualizerPermissionState = VisualizerPermissionState.Granted
) : VisualizerPermissionRepository {
    override val state = MutableStateFlow(initialState)

    override suspend fun markRequestLaunched() = Unit

    override fun refresh(shouldShowRationale: Boolean) = Unit

    override fun onRequestResult(granted: Boolean, shouldShowRationale: Boolean) = Unit
}

private class RecordingAmbientAudioVisualizer : AmbientAudioVisualizer {
    override val amplitudes = MutableStateFlow(FloatArray(0))
    override val availability = MutableStateFlow<AmbientVisualizerAvailability>(
        AmbientVisualizerAvailability.Ready
    )

    var startCount = 0
    var stopCount = 0
    val attachedAudioSessions = mutableListOf<Int>()

    override fun attachToAudioSession(audioSessionId: Int) {
        attachedAudioSessions += audioSessionId
    }

    override fun detachFromAudioSession() = Unit

    override fun start() {
        startCount += 1
    }

    override fun stop() {
        stopCount += 1
    }

    override fun close() = Unit
}

private class RecordingAmbientAmplitudeSource : AmbientAmplitudeSource {
    override val amplitudes = MutableStateFlow(FloatArray(0))
    var startCount = 0
    var stopCount = 0

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

private fun testTelemetryLogger(): TelemetryLogger = TelemetryLogger(TelemetrySink { })

private class RecordingTelemetrySink : TelemetrySink {
    val events = mutableListOf<TelemetryEvent>()

    override fun record(event: TelemetryEvent) {
        events += event
    }
}
