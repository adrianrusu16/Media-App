package com.adrianrusu.pandawave.feature.nowplaying.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianrusu.pandawave.core.audio.visualizer.AmbientAudioVisualizer
import com.adrianrusu.pandawave.core.audio.visualizer.AmbientVisualizerAvailability
import com.adrianrusu.pandawave.core.audio.visualizer.AudioSessionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionState
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
import com.adrianrusu.pandawave.feature.nowplaying.domain.ObserveNowPlayingStateUseCase
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientEligibility
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeEffect
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeInput
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeReducer
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeState
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeTransition
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    observeState: ObserveNowPlayingStateUseCase,
    private val repository: NowPlayingRepository,
    private val dispatchIntent: DispatchNowPlayingIntentUseCase,
    audioSessionRepository: AudioSessionRepository,
    private val visualizer: AmbientAudioVisualizer,
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
    val amplitudes = visualizer.amplitudes
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
            combine(
                state,
                visualizer.availability,
                routeVisible,
                lifecycleResumed
            ) { nowPlaying, visualizerAvailability, isRouteVisible, isLifecycleResumed ->
                AmbientEligibility(
                    routeVisible = isRouteVisible,
                    lifecycleResumed = isLifecycleResumed,
                    safetyPermitted = nowPlaying.ambientSafetyPermitted,
                    preferenceEnabled = nowPlaying.ambientModeEnabled,
                    isPlaying = nowPlaying.isPlaying,
                    timeoutMillis = nowPlaying.ambientTimeoutSeconds * MILLIS_PER_SECOND,
                    visualizerAvailable = nowPlaying.visualizerPermissionState == VisualizerPermissionState.Granted &&
                        visualizerAvailability == AmbientVisualizerAvailability.Ready
                )
            }.collect { eligibility ->
                reduce(
                    AmbientModeInput.EligibilityChanged(
                        eligibility = eligibility,
                        nowMillis = clock.nowMillis()
                    )
                )
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

    fun onVisualizerPermissionSnapshot(shouldShowRationale: Boolean) {
        visualizerPermissionRepository.refresh(shouldShowRationale)
    }

    fun onVisualizerPermissionResult(granted: Boolean, shouldShowRationale: Boolean) {
        visualizerPermissionRepository.onRequestResult(granted, shouldShowRationale)
    }

    fun onIntent(intent: NowPlayingIntent) {
        interactionTracker.recordInteraction()
        dispatchIntent(intent)
    }

    override fun onCleared() {
        timeoutJob?.cancel()
        repository.close()
        visualizer.close()
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

            previousState == AmbientModeState.AmbientVisualizing &&
                currentState == AmbientModeState.AmbientStatic -> {
                val unavailable = visualizer.availability.value as? AmbientVisualizerAvailability.Unavailable
                    ?: return
                logger.warning(
                    name = AmbientTelemetryEvents.VISUALIZER_UNAVAILABLE,
                    attributes = mapOf(
                        AmbientTelemetryAttributes.REASON to
                            AmbientTelemetryVisualizerReasons.from(unavailable.reason)
                    )
                )
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
                    delay((effect.deadlineMillis - clock.nowMillis()).coerceAtLeast(0L))
                    reduce(AmbientModeInput.TimeoutElapsed(effect.token))
                }
            }

            AmbientModeEffect.StartVisualizer -> visualizer.start()

            AmbientModeEffect.StopVisualizer -> visualizer.stop()
        }
    }

    private companion object {
        const val DEFAULT_AMBIENT_TIMEOUT_SECONDS = 15
        const val MILLIS_PER_SECOND = 1_000L
        const val STATE_STOP_TIMEOUT_MILLIS = 5_000L
    }
}
