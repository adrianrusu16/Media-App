package com.adrianrusu.pandawave.feature.nowplaying.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianrusu.pandawave.core.audio.visualizer.AmbientAudioVisualizer
import com.adrianrusu.pandawave.core.audio.visualizer.AudioSessionRepository
import com.adrianrusu.pandawave.feature.nowplaying.domain.DispatchNowPlayingIntentUseCase
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingIntent
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingRepository
import com.adrianrusu.pandawave.feature.nowplaying.domain.ObserveNowPlayingStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    observeState: ObserveNowPlayingStateUseCase,
    private val repository: NowPlayingRepository,
    private val dispatchIntent: DispatchNowPlayingIntentUseCase,
    audioSessionRepository: AudioSessionRepository,
    private val visualizer: AmbientAudioVisualizer
) : ViewModel() {
    private val _ambientModeActive = MutableStateFlow(false)
    val ambientModeActive: StateFlow<Boolean> = _ambientModeActive.asStateFlow()

    val amplitudes = visualizer.amplitudes
    val state = observeState()

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
            _ambientModeActive
                .collect { isActive ->
                    if (isActive) {
                        visualizer.start()
                    } else {
                        visualizer.stop()
                    }
                }
        }
    }

    fun setAmbientModeActive(isActive: Boolean) {
        _ambientModeActive.value = isActive
    }

    fun onIntent(intent: NowPlayingIntent) {
        dispatchIntent(intent)
    }

    override fun onCleared() {
        repository.close()
        visualizer.close()
        super.onCleared()
    }
}
