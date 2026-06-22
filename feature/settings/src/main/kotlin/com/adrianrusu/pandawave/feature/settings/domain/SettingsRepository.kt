package com.adrianrusu.pandawave.feature.settings.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository : AutoCloseable {
    val settingsState: StateFlow<SettingsState>

    fun start(scope: CoroutineScope)

    suspend fun dispatch(intent: SettingsIntent)
}
