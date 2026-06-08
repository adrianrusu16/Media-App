package com.adrianrusu.mediaapp.theme

import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import kotlinx.coroutines.flow.StateFlow

interface ThemePreferenceRepository {
    val preference: StateFlow<PandaWaveThemePreference>

    fun setPreference(preference: PandaWaveThemePreference)
}
