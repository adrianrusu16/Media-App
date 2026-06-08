package com.adrianrusu.mediaapp.core.model.theme

import kotlinx.coroutines.flow.StateFlow

interface ThemePreferenceRepository {
    val preference: StateFlow<PandaWaveThemePreference>

    fun setPreference(preference: PandaWaveThemePreference)
}
