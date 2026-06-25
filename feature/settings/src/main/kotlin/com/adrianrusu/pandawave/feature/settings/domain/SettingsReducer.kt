package com.adrianrusu.pandawave.feature.settings.domain

internal object SettingsReducer {
    fun reduce(state: SettingsState, intent: SettingsIntent): SettingsState = when (intent) {
        SettingsIntent.ToggleDiagnostics ->
            state.copy(diagnosticsEnabled = !state.diagnosticsEnabled)

        SettingsIntent.TogglePersonalization ->
            state.copy(personalizationEnabled = !state.personalizationEnabled)

        SettingsIntent.ToggleExplicitContent ->
            state.copy(explicitContentAllowed = !state.explicitContentAllowed)

        SettingsIntent.AcknowledgePrivacyNotice ->
            state.copy(privacyNoticeAcknowledged = true)

        is SettingsIntent.SelectThemePreference ->
            state.copy(themePreference = intent.preference)

        is SettingsIntent.SetAmbientModeEnabled,
        is SettingsIntent.SetAmbientTimeoutSeconds -> state
    }
}
