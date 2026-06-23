package com.adrianrusu.pandawave.feature.nowplaying.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianrusu.pandawave.core.audio.visualizer.AmbientAudioVisualizer
import com.adrianrusu.pandawave.core.audio.visualizer.AmbientVisualizerAvailability
import com.adrianrusu.pandawave.core.audio.visualizer.AudioSessionRepository
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceRepository
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceState
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    observeState: ObserveNowPlayingStateUseCase,
    private val repository: NowPlayingRepository,
    private val dispatchIntent: DispatchNowPlayingIntentUseCase,
    audioSessionRepository: AudioSessionRepository,
    private val visualizer: AmbientAudioVisualizer,
    ambientModePreferenceRepository: AmbientModePreferenceRepository,
    private val interactionTracker: UserInteractionTracker,
    private val clock: MonotonicClock
) : ViewModel() {
    private val reducer = AmbientModeReducer()
    private val routeVisible = MutableStateFlow(false)
    private val lifecycleResumed = MutableStateFlow(false)
    private val mutableAmbientModeState = MutableStateFlow<AmbientModeState>(AmbientModeState.Hidden)
    private val mutableAmbientTransition = MutableStateFlow(AmbientModeTransition.Immediate)
    private var timeoutJob: Job? = null

    val amplitudes = visualizer.amplitudes
    val state = observeState()
    val ambientModeState: StateFlow<AmbientModeState> = mutableAmbientModeState.asStateFlow()
    val ambientTransition: StateFlow<AmbientModeTransition> = mutableAmbientTransition.asStateFlow()

    init {
        repository.start()

        viewModelScope.launch {
            audioSessionRepository.audioSessionId
                .filterNotNull()
                .collect { audioSessionId ->
                    visualizer.attachToAudioSession(audioSessionId)
                }
        }

        viewModelScope.launch {
            combine(
                state,
                ambientModePreferenceRepository.state,
                visualizer.availability,
                routeVisible,
                lifecycleResumed
            ) { nowPlaying, preferenceState, visualizerAvailability, isRouteVisible, isLifecycleResumed ->
                val preferences = (preferenceState as? AmbientModePreferenceState.Ready)?.preferences
                AmbientEligibility(
                    routeVisible = isRouteVisible,
                    lifecycleResumed = isLifecycleResumed,
                    safetyPermitted = nowPlaying.ambientSafetyPermitted,
                    preferenceEnabled = preferences?.enabled == true,
                    isPlaying = nowPlaying.isPlaying,
                    timeoutMillis = (preferences?.timeoutSeconds ?: 15) * MILLIS_PER_SECOND,
                    visualizerAvailable = visualizerAvailability == AmbientVisualizerAvailability.Ready
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
        val reduction = reducer.reduce(
            state = mutableAmbientModeState.value,
            input = input
        )
        mutableAmbientModeState.value = reduction.state
        mutableAmbientTransition.value = reduction.transition
        reduction.effects.forEach(::handleEffect)
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
        const val MILLIS_PER_SECOND = 1_000L
    }
}
