package com.adrianrusu.mediaapp.feature.settings.presentation

import androidx.lifecycle.ViewModel
import com.adrianrusu.mediaapp.feature.settings.domain.DispatchSettingsIntentUseCase
import com.adrianrusu.mediaapp.feature.settings.domain.ObserveSettingsStateUseCase
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsIntent
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    observeState: ObserveSettingsStateUseCase,
    private val dispatchIntent: DispatchSettingsIntentUseCase
) : ViewModel() {
    val state = observeState()

    init {
        repository.start()
    }

    fun onIntent(intent: SettingsIntent) {
        dispatchIntent(intent)
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
