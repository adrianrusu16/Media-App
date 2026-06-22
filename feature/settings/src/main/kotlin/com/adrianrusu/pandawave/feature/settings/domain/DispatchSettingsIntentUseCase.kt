package com.adrianrusu.pandawave.feature.settings.domain

class DispatchSettingsIntentUseCase(private val repository: SettingsRepository) {
    suspend operator fun invoke(intent: SettingsIntent) {
        repository.dispatch(intent)
    }
}
