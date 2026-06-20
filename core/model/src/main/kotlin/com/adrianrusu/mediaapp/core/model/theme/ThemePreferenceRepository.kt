package com.adrianrusu.mediaapp.core.model.theme

import kotlinx.coroutines.flow.StateFlow

interface ThemePreferenceRepository {
    val state: StateFlow<ThemePreferenceState>

    suspend fun setPreference(preference: PandaWaveThemePreference)
}
