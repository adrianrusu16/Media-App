package com.adrianrusu.pandawave.feature.settings.domain

import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
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
    fun `allows setting changes when restrictions target unrelated capabilities`() {
        val restricted = SettingsState(
            personalizationEnabled = false,
            restriction = SettingsRestrictionState(
                isRestricted = true
            )
        )

        val result = SettingsReducer.reduce(
            state = restricted,
            intent = SettingsIntent.TogglePersonalization
        )

        assertTrue(result.personalizationEnabled)
    }

    @Test
    fun `allows privacy acknowledgement when restricted`() {
        val restricted = SettingsState(
            privacyNoticeAcknowledged = false,
            restriction = SettingsRestrictionState(
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
            intent = SettingsIntent.SelectThemePreference(PandaWaveThemePreference.ForestTechDark)
        )

        assertEquals(PandaWaveThemePreference.ForestTechDark, result.themePreference)
    }

    @Test
    fun `ambient persistence intents do not speculate over repository state`() {
        val state = SettingsState(
            ambientModeEnabled = true,
            ambientTimeoutSeconds = 15
        )

        assertEquals(
            state,
            SettingsReducer.reduce(state, SettingsIntent.SetAmbientModeEnabled(false))
        )
        assertEquals(
            state,
            SettingsReducer.reduce(state, SettingsIntent.SetAmbientTimeoutSeconds(45))
        )
    }
}
