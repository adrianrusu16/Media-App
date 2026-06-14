package com.adrianrusu.mediaapp.feature.settings.domain

import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsReducerTest {
    @Test
    fun `toggles diagnostics when controls are enabled`() {
        val result = SettingsReducer.reduce(
            state = SettingsState(diagnosticsEnabled = true),
            intent = SettingsIntent.ToggleDiagnostics
        )

        assertFalse(result.diagnosticsEnabled)
    }

    @Test
    fun `blocks setting changes when restricted`() {
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
    fun `allows privacy acknowledgement when restricted`() {
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

    @Test
    fun `selects theme preference when controls are enabled`() {
        val result = SettingsReducer.reduce(
            state = SettingsState(),
            intent = SettingsIntent.SelectThemePreference(PandaWaveThemePreference.MoonlitBambooDark)
        )

        assertEquals(PandaWaveThemePreference.MoonlitBambooDark, result.themePreference)
    }
}
