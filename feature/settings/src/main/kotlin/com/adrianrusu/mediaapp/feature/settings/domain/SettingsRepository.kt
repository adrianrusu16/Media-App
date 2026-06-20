package com.adrianrusu.mediaapp.feature.settings.domain

import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository : AutoCloseable {
    val settingsState: StateFlow<SettingsState>

    fun start()

    fun dispatch(intent: SettingsIntent)
}
