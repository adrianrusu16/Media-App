package com.adrianrusu.mediaapp.core.model.theme

class ObserveThemePreferenceUseCase(private val repository: ThemePreferenceRepository) {
    operator fun invoke() = repository.state
}
