package com.adrianrusu.mediaapp.feature.settings.domain

class DispatchSettingsIntentUseCase(
    private val repository: SettingsRepository,
) {
    operator fun invoke(intent: SettingsIntent) {
        repository.dispatch(intent)
    }
}
