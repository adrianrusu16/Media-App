package com.adrianrusu.mediaapp.core.model.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryThemePreferenceRepository : ThemePreferenceRepository {
    private val mutablePreference = MutableStateFlow(PandaWaveThemePreference.SystemDefault)

    override val preference: StateFlow<PandaWaveThemePreference> = mutablePreference.asStateFlow()

    override fun setPreference(preference: PandaWaveThemePreference) {
        mutablePreference.value = preference
    }
}
