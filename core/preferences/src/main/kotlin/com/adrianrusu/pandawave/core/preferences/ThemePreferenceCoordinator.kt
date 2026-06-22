package com.adrianrusu.pandawave.core.preferences

import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceState
import kotlinx.coroutines.flow.StateFlow

interface ThemePreferenceCoordinator : AutoCloseable {
    val state: StateFlow<ThemePreferenceState>

    fun start()

    suspend fun select(preference: PandaWaveThemePreference)
}
