package com.adrianrusu.mediaapp.feature.settings.domain

internal object SettingsReducer {
    fun reduce(state: SettingsState, intent: SettingsIntent): SettingsState {
        if (state.restriction.isRestricted && intent != SettingsIntent.AcknowledgePrivacyNotice) {
            return state
        }

        return when (intent) {
            SettingsIntent.ToggleDiagnostics ->
                state.copy(diagnosticsEnabled = !state.diagnosticsEnabled)

            SettingsIntent.TogglePersonalization ->
                state.copy(personalizationEnabled = !state.personalizationEnabled)

            SettingsIntent.ToggleExplicitContent ->
                state.copy(explicitContentAllowed = !state.explicitContentAllowed)

            SettingsIntent.AcknowledgePrivacyNotice ->
                state.copy(privacyNoticeAcknowledged = true)
        }
    }
}
