package com.adrianrusu.mediaapp.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianrusu.mediaapp.feature.settings.domain.DispatchSettingsIntentUseCase
import com.adrianrusu.mediaapp.feature.settings.domain.ObserveSettingsStateUseCase
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsIntent
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    observeState: ObserveSettingsStateUseCase,
    private val dispatchIntent: DispatchSettingsIntentUseCase
) : ViewModel() {
    val state = observeState()

    init {
        repository.start(viewModelScope)
    }

    fun onIntent(intent: SettingsIntent) {
        viewModelScope.launch { dispatchIntent(intent) }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
