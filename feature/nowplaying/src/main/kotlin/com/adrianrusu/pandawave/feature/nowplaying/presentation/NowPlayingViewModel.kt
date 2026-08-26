package com.adrianrusu.pandawave.feature.nowplaying.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianrusu.pandawave.core.audio.visualizer.AmbientAmplitudeSource
import com.adrianrusu.pandawave.core.audio.visualizer.AmbientAudioVisualizer
import com.adrianrusu.pandawave.core.audio.visualizer.AmbientVisualizerAvailability
import com.adrianrusu.pandawave.core.audio.visualizer.AudioSessionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionState
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.playback.BambooEngineConnectionStatus
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceRepository
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceState
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import com.adrianrusu.pandawave.core.ui.interaction.MonotonicClock
import com.adrianrusu.pandawave.core.ui.interaction.UserInteractionTracker
import com.adrianrusu.pandawave.feature.nowplaying.domain.DispatchNowPlayingIntentUseCase
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingIntent
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingRepository
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingState
import com.adrianrusu.pandawave.feature.nowplaying.domain.ObserveNowPlayingStateUseCase
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientEligibility
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeEffect
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeInput
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeReducer
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeState
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeTransition
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    observeState: ObserveNowPlayingStateUseCase,
    private val repository: NowPlayingRepository,
    private val dispatchIntent: DispatchNowPlayingIntentUseCase,
    audioSessionRepository: AudioSessionRepository,
    private val visualizer: AmbientAudioVisualizer,
    private val sleepingAmplitudeSource: AmbientAmplitudeSource,
    private val visualizerPermissionRepository: VisualizerPermissionRepository,
    ambientModePreferenceRepository: AmbientModePreferenceRepository,
    private val interactionTracker: UserInteractionTracker,
    private val clock: MonotonicClock,
    telemetryLogger: TelemetryLogger
) : ViewModel() {
    private val logger = telemetryLogger.forModule(TelemetryModule.Ambient)
    private val reducer = AmbientModeReducer()
    private val routeVisible = MutableStateFlow(false)
    private val lifecycleResumed = MutableStateFlow(false)
    private val mutableAmbientModeState = MutableStateFlow<AmbientModeState>(AmbientModeState.Hidden)
    private val mutableAmbientTransition = MutableStateFlow(AmbientModeTransition.Immediate)
    private var timeoutJob: Job? = null

    private val playbackState = observeState()

    val state = combine(
        playbackState,
        ambientModePreferenceRepository.state,
        visualizerPermissionRepository.state
    ) { nowPlaying, preferenceState, permissionState ->
        val preferences = (preferenceState as? AmbientModePreferenceState.Ready)?.preferences
        nowPlaying.copy(
            ambientModeEnabled = preferences?.enabled == true,
            ambientTimeoutSeconds = preferences?.timeoutSeconds ?: DEFAULT_AMBIENT_TIMEOUT_SECONDS,
            visualizerPermissionState = permissionState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STATE_STOP_TIMEOUT_MILLIS),
        initialValue = playbackState.value
    )
    val ambientModeState: StateFlow<AmbientModeState> = mutableAmbientModeState.asStateFlow()
    val ambientTransition: StateFlow<AmbientModeTransition> = mutableAmbientTransition.asStateFlow()
    private val ambientEligibility: StateFlow<AmbientEligibility> = combine(
        state,
        visualizer.availability,
        routeVisible,
        lifecycleResumed
    ) { nowPlaying, visualizerAvailability, isRouteVisible, isLifecycleResumed ->
        nowPlaying.toAmbientEligibility(
            visualizerAvailability = visualizerAvailability,
            isRouteVisible = isRouteVisible,
            isLifecycleResumed = isLifecycleResumed
        )
    }.distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_STOP_TIMEOUT_MILLIS),
            initialValue = state.value.toAmbientEligibility(
                visualizerAvailability = visualizer.availability.value,
                isRouteVisible = routeVisible.value,
                isLifecycleResumed = lifecycleResumed.value
            )
        )
    val amplitudes: StateFlow<FloatArray> = ambientModeState
        .flatMapLatest { ambientState ->
            when (ambientState) {
                AmbientModeState.AmbientSleeping -> sleepingAmplitudeSource.amplitudes
                AmbientModeState.AmbientVisualizing -> visualizer.amplitudes
                else -> flowOf(EMPTY_AMPLITUDE_FRAME)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STATE_STOP_TIMEOUT_MILLIS),
            initialValue = EMPTY_AMPLITUDE_FRAME
        )

    init {
        repository.start()

        viewModelScope.launch {
            var attachedSessionId: Int? = null
            combine(
                audioSessionRepository.audioSessionId,
                visualizerPermissionRepository.state
            ) { audioSessionId, permissionState ->
                audioSessionId.takeIf { permissionState == VisualizerPermissionState.Granted }
            }.collect { permittedSessionId ->
                if (permittedSessionId != null) {
                    visualizer.attachToAudioSession(permittedSessionId)
                    attachedSessionId = permittedSessionId
                } else if (attachedSessionId != null) {
                    visualizer.detachFromAudioSession()
                    attachedSessionId = null
                }
            }
        }

        viewModelScope.launch {
            ambientEligibility.collect { eligibility ->
                reduce(
                    AmbientModeInput.EligibilityChanged(
                        eligibility = eligibility,
                        nowMillis = clock.nowMillis()
                    )
                )
            }
        }

        viewModelScope.launch {
            var realVisualizerRunning = false
            ambientEligibility
                .map { eligibility ->
                eligibility.ambientPermitted &&
                    eligibility.isPlaying &&
                    eligibility.hasUsableAmplitudeSource
                }
                .distinctUntilChanged()
                .collect { shouldRun ->
                when {
                    shouldRun && !realVisualizerRunning -> {
                        visualizer.start()
                        realVisualizerRunning = true
                    }

                    !shouldRun && realVisualizerRunning -> {
                        visualizer.stop()
                        realVisualizerRunning = false
                    }
                }
            }
        }

        viewModelScope.launch {
            interactionTracker.revision
                .drop(1)
                .collect {
                    reduce(AmbientModeInput.UserInteraction(nowMillis = clock.nowMillis()))
                }
        }
    }

    fun onRouteVisibilityChanged(isVisible: Boolean) {
        if (isVisible && !routeVisible.value) {
            interactionTracker.recordInteraction()
        }
        routeVisible.value = isVisible
    }

    fun onLifecycleResumedChanged(isResumed: Boolean) {
        if (isResumed && !lifecycleResumed.value) {
            interactionTracker.recordInteraction()
        }
        lifecycleResumed.value = isResumed
    }

    fun onUserInteraction() {
        interactionTracker.recordInteraction()
    }

    fun onIntent(intent: NowPlayingIntent) {
        val current = state.value
        val trackId = current.mediaId.orEmpty()
        val title = PandaLog.field(current.title)
        when (intent) {
            NowPlayingIntent.TogglePlayback -> {
                val action = if (current.isPlaying) "pause" else "play"
                PandaLog.v(PandaLog.Tag.NPS) {
                    "click action=$action trackId=$trackId title=$title"
                }
                PandaLog.i(PandaLog.Tag.NPS) {
                    "${action}_requested trackId=$trackId title=$title"
                }
            }
            NowPlayingIntent.SkipNext -> {
                PandaLog.v(PandaLog.Tag.NPS) { "click action=skip_next trackId=$trackId title=$title" }
                PandaLog.i(PandaLog.Tag.NPS) { "skip_next_requested trackId=$trackId title=$title" }
            }
            NowPlayingIntent.SkipPrevious -> {
                PandaLog.v(PandaLog.Tag.NPS) { "click action=skip_previous trackId=$trackId title=$title" }
                PandaLog.i(PandaLog.Tag.NPS) { "skip_previous_requested trackId=$trackId title=$title" }
            }
            is NowPlayingIntent.SetVolume -> {
                PandaLog.v(PandaLog.Tag.NPS) { "click action=set_volume volume=${intent.volume}" }
            }
            NowPlayingIntent.Refresh -> {
                PandaLog.d(PandaLog.Tag.NPS) { "refresh_requested trackId=$trackId" }
            }
        }
        interactionTracker.recordInteraction()
        dispatchIntent(intent)
    }

    override fun onCleared() {
        timeoutJob?.cancel()
        repository.close()
        visualizer.close()
        sleepingAmplitudeSource.close()
        super.onCleared()
    }

    private fun reduce(input: AmbientModeInput) {
        val previousState = mutableAmbientModeState.value
        val reduction = reducer.reduce(
            state = previousState,
            input = input
        )
        mutableAmbientModeState.value = reduction.state
        mutableAmbientTransition.value = reduction.transition
        reduction.effects.forEach(::handleEffect)
        logCommittedTransition(
            previousState = previousState,
            currentState = reduction.state,
            input = input
        )
    }

    private fun logCommittedTransition(
        previousState: AmbientModeState,
        currentState: AmbientModeState,
        input: AmbientModeInput
    ) {
        if (previousState == currentState) return

        if (
            previousState == AmbientModeState.AmbientVisualizing &&
            currentState == AmbientModeState.Interactive
        ) {
            val unavailable = visualizer.availability.value as? AmbientVisualizerAvailability.Unavailable
            if (unavailable != null) {
                logger.warning(
                    name = AmbientTelemetryEvents.VISUALIZER_UNAVAILABLE,
                    attributes = mapOf(
                        AmbientTelemetryAttributes.REASON to
                            AmbientTelemetryVisualizerReasons.from(unavailable.reason)
                    )
                )
            }
        }

        when {
            !previousState.isAmbientPresentation && currentState.isAmbientPresentation -> logger.info(
                name = AmbientTelemetryEvents.ENTERED,
                attributes = mapOf(AmbientTelemetryAttributes.MODE to currentState.telemetryMode)
            )

            previousState.isAmbientPresentation && !currentState.isAmbientPresentation -> {
                val reason = AmbientTelemetryExitReasons.from(input)
                val attributes = mapOf(AmbientTelemetryAttributes.REASON to reason)
                if (
                    reason == AmbientTelemetryExitReasons.SAFETY_LOST &&
                    state.value.engineConnection.status == BambooEngineConnectionStatus.Unavailable
                ) {
                    logger.warning(name = AmbientTelemetryEvents.EXITED, attributes = attributes)
                } else {
                    logger.info(name = AmbientTelemetryEvents.EXITED, attributes = attributes)
                }
            }
        }
    }

    private fun handleEffect(effect: AmbientModeEffect) {
        when (effect) {
            AmbientModeEffect.CancelTimeout -> {
                timeoutJob?.cancel()
                timeoutJob = null
            }

            is AmbientModeEffect.ScheduleTimeout -> {
                timeoutJob?.cancel()
                timeoutJob = viewModelScope.launch {
                    delay((effect.deadlineMillis - clock.nowMillis()).coerceAtLeast(0L).milliseconds)
                    reduce(AmbientModeInput.TimeoutElapsed(effect.token))
                }
            }

            AmbientModeEffect.StartSleepingAnimation -> sleepingAmplitudeSource.start()

            AmbientModeEffect.StopSleepingAnimation -> sleepingAmplitudeSource.stop()

            AmbientModeEffect.StartRealVisualizer,
            AmbientModeEffect.StopRealVisualizer -> Unit
        }
    }

    private companion object {
        const val DEFAULT_AMBIENT_TIMEOUT_SECONDS = 15
        const val STATE_STOP_TIMEOUT_MILLIS = 5_000L
        val EMPTY_AMPLITUDE_FRAME = FloatArray(0)
    }
}

private fun NowPlayingState.toAmbientEligibility(
    visualizerAvailability: AmbientVisualizerAvailability,
    isRouteVisible: Boolean,
    isLifecycleResumed: Boolean
): AmbientEligibility = AmbientEligibility(
    routeVisible = isRouteVisible,
    lifecycleResumed = isLifecycleResumed,
    isParked = isParked,
    isUxUnrestricted = isUxUnrestricted,
    preferenceEnabled = ambientModeEnabled,
    permissionGranted = visualizerPermissionState == VisualizerPermissionState.Granted,
    isPlaying = isPlaying,
    timeoutMillis = ambientTimeoutSeconds * AMBIENT_MILLIS_PER_SECOND,
    realVisualizerReady = visualizerAvailability == AmbientVisualizerAvailability.Ready
)

private const val AMBIENT_MILLIS_PER_SECOND = 1_000L
