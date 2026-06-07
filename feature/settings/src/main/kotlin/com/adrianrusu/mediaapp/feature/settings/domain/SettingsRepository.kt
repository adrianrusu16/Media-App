package com.adrianrusu.mediaapp.feature.settings.domain

import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository : AutoCloseable {
    val state: StateFlow<SettingsState>

    fun start()

    fun dispatch(intent: SettingsIntent)
}
