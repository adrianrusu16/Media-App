package com.adrianrusu.mediaapp.feature.settings.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsReducerTest {
    @Test
    fun togglesDiagnosticsWhenControlsAreEnabled() {
        val result = SettingsReducer.reduce(
            state = SettingsState(diagnosticsEnabled = true),
            intent = SettingsIntent.ToggleDiagnostics
        )

        assertFalse(result.diagnosticsEnabled)
    }

    @Test
    fun blocksSettingChangesWhenRestricted() {
        val restricted = SettingsState(
            personalizationEnabled = false,
            restriction = SettingsRestrictionState(
                label = "Parked required",
                isRestricted = true
            )
        )

        val result = SettingsReducer.reduce(
            state = restricted,
            intent = SettingsIntent.TogglePersonalization
        )

        assertFalse(result.personalizationEnabled)
    }

    @Test
    fun allowsPrivacyAcknowledgementWhenRestricted() {
        val restricted = SettingsState(
            privacyNoticeAcknowledged = false,
            restriction = SettingsRestrictionState(
                label = "Parked required",
                isRestricted = true
            )
        )

        val result = SettingsReducer.reduce(
            state = restricted,
            intent = SettingsIntent.AcknowledgePrivacyNotice
        )

        assertTrue(result.privacyNoticeAcknowledged)
    }
}
