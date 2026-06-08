package com.adrianrusu.mediaapp.theme

class ObserveThemePreferenceUseCase(private val repository: ThemePreferenceRepository) {
    operator fun invoke() = repository.preference
}
