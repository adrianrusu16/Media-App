package com.adrianrusu.pandawave.core.preferences

sealed interface AmbientModePreferenceState {
    data object Loading : AmbientModePreferenceState

    data class Ready(val preferences: AmbientModePreferences) : AmbientModePreferenceState
}
