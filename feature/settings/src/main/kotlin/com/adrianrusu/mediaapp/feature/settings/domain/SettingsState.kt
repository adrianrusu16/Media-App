package com.adrianrusu.mediaapp.feature.settings.domain

import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference

data class SettingsState(
    val diagnosticsEnabled: Boolean = true,
    val personalizationEnabled: Boolean = false,
    val explicitContentAllowed: Boolean = false,
    val privacyNoticeAcknowledged: Boolean = false,
    val themePreference: PandaWaveThemePreference = PandaWaveThemePreference.SystemDefault,
    val restriction: SettingsRestrictionState = SettingsRestrictionState.Unavailable
)

data class SettingsRestrictionState(val isRestricted: Boolean) {
    companion object {
        val Unavailable = SettingsRestrictionState(
            isRestricted = false
        )
    }
}
