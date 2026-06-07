package com.adrianrusu.mediaapp.feature.settings.domain

data class SettingsState(
    val diagnosticsEnabled: Boolean = true,
    val personalizationEnabled: Boolean = false,
    val explicitContentAllowed: Boolean = false,
    val privacyNoticeAcknowledged: Boolean = false,
    val restriction: SettingsRestrictionState = SettingsRestrictionState.Unavailable,
) {
    val controlsEnabled: Boolean
        get() = !restriction.isRestricted
}

data class SettingsRestrictionState(
    val label: String,
    val isRestricted: Boolean,
) {
    companion object {
        val Unavailable = SettingsRestrictionState(
            label = "Safety status unavailable",
            isRestricted = false,
        )
    }
}
