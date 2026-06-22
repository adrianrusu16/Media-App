package com.adrianrusu.pandawave.feature.settings.domain

class ObserveSettingsStateUseCase(private val repository: SettingsRepository) {
    operator fun invoke() = repository.settingsState
}
