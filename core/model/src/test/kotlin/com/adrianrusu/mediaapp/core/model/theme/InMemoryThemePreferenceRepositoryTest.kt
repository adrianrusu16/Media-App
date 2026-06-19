package com.adrianrusu.mediaapp.core.model.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryThemePreferenceRepositoryTest {
    @Test
    fun `defaults to system theme preference`() {
        val repository = InMemoryThemePreferenceRepository()

        assertEquals(PandaWaveThemePreference.SystemDefault, repository.preference.value)
    }

    @Test
    fun `use cases observe and set theme preference`() {
        val repository = InMemoryThemePreferenceRepository()
        val observe = ObserveThemePreferenceUseCase(repository)
        val set = SetThemePreferenceUseCase(repository)

        set(PandaWaveThemePreference.ForestTechLight)

        assertEquals(PandaWaveThemePreference.ForestTechLight, observe().value)
    }
}
