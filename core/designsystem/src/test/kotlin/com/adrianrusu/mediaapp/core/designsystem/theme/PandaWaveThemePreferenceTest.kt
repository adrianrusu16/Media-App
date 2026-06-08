package com.adrianrusu.mediaapp.core.designsystem.theme

import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PandaWaveThemePreferenceTest {
    @Test
    fun systemDefaultUsesLightProfileWhenSystemIsLight() {
        val profile = PandaWaveThemePreference.SystemDefault.toThemeProfile(systemDark = false)

        assertEquals(PandaWaveThemeId.BambooGroveLight, profile.id)
        assertFalse(profile.isDark)
    }

    @Test
    fun systemDefaultUsesDarkProfileWhenSystemIsDark() {
        val profile = PandaWaveThemePreference.SystemDefault.toThemeProfile(systemDark = true)

        assertEquals(PandaWaveThemeId.MoonlitBambooDark, profile.id)
        assertTrue(profile.isDark)
    }

    @Test
    fun explicitPreferenceOverridesSystemMode() {
        val lightProfile = PandaWaveThemePreference.BambooGroveLight.toThemeProfile(systemDark = true)
        val darkProfile = PandaWaveThemePreference.MoonlitBambooDark.toThemeProfile(systemDark = false)

        assertEquals(PandaWaveThemeId.BambooGroveLight, lightProfile.id)
        assertFalse(lightProfile.isDark)
        assertEquals(PandaWaveThemeId.MoonlitBambooDark, darkProfile.id)
        assertTrue(darkProfile.isDark)
    }
}
