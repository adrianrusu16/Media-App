package com.adrianrusu.pandawave.core.preferences

import kotlinx.coroutines.flow.StateFlow

interface AmbientModePreferenceRepository {
    val state: StateFlow<AmbientModePreferenceState>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setTimeoutSeconds(timeoutSeconds: Int)
}
