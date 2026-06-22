package com.adrianrusu.pandawave.core.model.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryThemePreferenceRepository : ThemePreferenceRepository {
    private val mutableState = MutableStateFlow<ThemePreferenceState>(
        ThemePreferenceState.Ready(PandaWaveThemePreference.SystemDefault)
    )

    override val state: StateFlow<ThemePreferenceState> = mutableState.asStateFlow()

    override suspend fun setPreference(preference: PandaWaveThemePreference) {
        mutableState.value = ThemePreferenceState.Ready(preference)
    }
}
