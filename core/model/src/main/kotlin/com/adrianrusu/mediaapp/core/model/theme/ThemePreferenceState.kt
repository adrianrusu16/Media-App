package com.adrianrusu.mediaapp.core.model.theme

sealed interface ThemePreferenceState {
    data object Loading : ThemePreferenceState

    data class Ready(val preference: PandaWaveThemePreference) : ThemePreferenceState
}
