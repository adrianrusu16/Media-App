package com.adrianrusu.mediaapp.feature.settings.domain

class ObserveSettingsStateUseCase(private val repository: SettingsRepository) {
    operator fun invoke() = repository.state
}
