package com.adrianrusu.pandawave.feature.settings.domain

import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionState
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference

data class SettingsState(
    val diagnosticsEnabled: Boolean = true,
    val personalizationEnabled: Boolean = false,
    val explicitContentAllowed: Boolean = false,
    val privacyNoticeAcknowledged: Boolean = false,
    val themePreference: PandaWaveThemePreference = PandaWaveThemePreference.SystemDefault,
    val ambientModeEnabled: Boolean = true,
    val ambientTimeoutSeconds: Int = 15,
    val visualizerPermissionState: VisualizerPermissionState = VisualizerPermissionState.Unknown,
    val restriction: SettingsRestrictionState = SettingsRestrictionState.Unavailable
)

data class SettingsRestrictionState(val isRestricted: Boolean) {
    companion object {
        val Unavailable = SettingsRestrictionState(
            isRestricted = true
        )
    }
}
