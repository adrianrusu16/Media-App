package com.adrianrusu.mediaapp.feature.settings.domain

sealed interface SettingsIntent {
    data object ToggleDiagnostics : SettingsIntent
    data object TogglePersonalization : SettingsIntent
    data object ToggleExplicitContent : SettingsIntent
    data object AcknowledgePrivacyNotice : SettingsIntent
}
