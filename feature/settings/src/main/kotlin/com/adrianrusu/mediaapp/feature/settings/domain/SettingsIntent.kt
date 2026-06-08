package com.adrianrusu.mediaapp.feature.settings.domain

import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference

sealed interface SettingsIntent {
    data object ToggleDiagnostics : SettingsIntent
    data object TogglePersonalization : SettingsIntent
    data object ToggleExplicitContent : SettingsIntent
    data object AcknowledgePrivacyNotice : SettingsIntent
    data class SelectThemePreference(val preference: PandaWaveThemePreference) : SettingsIntent
}
