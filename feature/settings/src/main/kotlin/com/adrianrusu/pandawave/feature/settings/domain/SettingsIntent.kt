package com.adrianrusu.pandawave.feature.settings.domain

import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference

sealed interface SettingsIntent {
    data object ToggleDiagnostics : SettingsIntent
    data object TogglePersonalization : SettingsIntent
    data object ToggleExplicitContent : SettingsIntent
    data object AcknowledgePrivacyNotice : SettingsIntent
    data class SelectThemePreference(val preference: PandaWaveThemePreference) : SettingsIntent
    data class SetAmbientModeEnabled(val enabled: Boolean) : SettingsIntent
    data class SetAmbientTimeoutSeconds(val timeoutSeconds: Int) : SettingsIntent
    data object RequestVisualizerPermission : SettingsIntent
}
