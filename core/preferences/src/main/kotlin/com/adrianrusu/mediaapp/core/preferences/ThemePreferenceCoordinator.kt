package com.adrianrusu.mediaapp.core.preferences

import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.mediaapp.core.model.theme.ThemePreferenceState
import kotlinx.coroutines.flow.StateFlow

interface ThemePreferenceCoordinator : AutoCloseable {
    val state: StateFlow<ThemePreferenceState>

    fun start()

    suspend fun select(preference: PandaWaveThemePreference)
}
