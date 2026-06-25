package com.adrianrusu.pandawave.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionRepository
import com.adrianrusu.pandawave.feature.settings.domain.DispatchSettingsIntentUseCase
import com.adrianrusu.pandawave.feature.settings.domain.ObserveSettingsStateUseCase
import com.adrianrusu.pandawave.feature.settings.domain.SettingsIntent
import com.adrianrusu.pandawave.feature.settings.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    observeState: ObserveSettingsStateUseCase,
    private val dispatchIntent: DispatchSettingsIntentUseCase,
    private val visualizerPermissionRepository: VisualizerPermissionRepository
) : ViewModel() {
    val state = observeState()

    init {
        repository.start(viewModelScope)
    }

    fun onIntent(intent: SettingsIntent) {
        viewModelScope.launch { dispatchIntent(intent) }
    }

    fun onVisualizerPermissionSnapshot(shouldShowRationale: Boolean) {
        visualizerPermissionRepository.refresh(shouldShowRationale)
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
