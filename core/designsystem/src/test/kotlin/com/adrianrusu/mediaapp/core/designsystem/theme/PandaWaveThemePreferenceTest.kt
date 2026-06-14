package com.adrianrusu.mediaapp.core.designsystem.theme

import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PandaWaveThemePreferenceTest {
    @Test
    fun `system default uses light profile when system is light`() {
        val profile = PandaWaveThemePreference.SystemDefault.toThemeProfile(systemDark = false)

        assertEquals(PandaWaveThemeId.BambooGroveLight, profile.id)
        assertFalse(profile.isDark)
    }

    @Test
    fun `system default uses dark profile when system is dark`() {
        val profile = PandaWaveThemePreference.SystemDefault.toThemeProfile(systemDark = true)

        assertEquals(PandaWaveThemeId.MoonlitBambooDark, profile.id)
        assertTrue(profile.isDark)
    }

    @Test
    fun `explicit preference overrides system mode`() {
        val lightProfile = PandaWaveThemePreference.BambooGroveLight.toThemeProfile(systemDark = true)
        val darkProfile = PandaWaveThemePreference.MoonlitBambooDark.toThemeProfile(systemDark = false)

        assertEquals(PandaWaveThemeId.BambooGroveLight, lightProfile.id)
        assertFalse(lightProfile.isDark)
        assertEquals(PandaWaveThemeId.MoonlitBambooDark, darkProfile.id)
        assertTrue(darkProfile.isDark)
    }
}
