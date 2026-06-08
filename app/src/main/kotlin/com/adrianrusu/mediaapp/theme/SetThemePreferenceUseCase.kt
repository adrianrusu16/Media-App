package com.adrianrusu.mediaapp.theme

import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference

class SetThemePreferenceUseCase(private val repository: ThemePreferenceRepository) {
    operator fun invoke(preference: PandaWaveThemePreference) {
        repository.setPreference(preference)
    }
}
