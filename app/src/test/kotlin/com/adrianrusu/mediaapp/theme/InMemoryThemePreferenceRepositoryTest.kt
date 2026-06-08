package com.adrianrusu.mediaapp.theme

import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryThemePreferenceRepositoryTest {
    @Test
    fun defaultsToSystemThemePreference() {
        val repository = InMemoryThemePreferenceRepository()

        assertEquals(PandaWaveThemePreference.SystemDefault, repository.preference.value)
    }

    @Test
    fun useCasesObserveAndSetThemePreference() {
        val repository = InMemoryThemePreferenceRepository()
        val observe = ObserveThemePreferenceUseCase(repository)
        val set = SetThemePreferenceUseCase(repository)

        set(PandaWaveThemePreference.BambooGroveLight)

        assertEquals(PandaWaveThemePreference.BambooGroveLight, observe().value)
    }
}
