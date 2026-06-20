package com.adrianrusu.mediaapp.core.model.theme

class SetThemePreferenceUseCase(private val repository: ThemePreferenceRepository) {
    suspend operator fun invoke(preference: PandaWaveThemePreference) {
        repository.setPreference(preference)
    }
}
