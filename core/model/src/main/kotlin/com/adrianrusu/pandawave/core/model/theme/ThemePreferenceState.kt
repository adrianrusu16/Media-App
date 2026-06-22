package com.adrianrusu.pandawave.core.model.theme

sealed interface ThemePreferenceState {
    data object Loading : ThemePreferenceState

    data class Ready(val preference: PandaWaveThemePreference) : ThemePreferenceState
}
