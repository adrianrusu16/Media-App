package com.adrianrusu.pandawave.core.model.theme

class ObserveThemePreferenceUseCase(private val repository: ThemePreferenceRepository) {
    operator fun invoke() = repository.state
}
