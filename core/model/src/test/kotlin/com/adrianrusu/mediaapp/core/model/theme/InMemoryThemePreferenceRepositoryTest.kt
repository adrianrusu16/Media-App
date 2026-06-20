package com.adrianrusu.mediaapp.core.model.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class InMemoryThemePreferenceRepositoryTest {
    @Test
    fun `defaults to ready system theme preference`() {
        val repository = InMemoryThemePreferenceRepository()

        assertEquals(
            ThemePreferenceState.Ready(PandaWaveThemePreference.SystemDefault),
            repository.state.value
        )
    }

    @Test
    fun `use cases observe and set theme preference`() = runTest {
        val repository = InMemoryThemePreferenceRepository()
        val observe = ObserveThemePreferenceUseCase(repository)
        val set = SetThemePreferenceUseCase(repository)

        set(PandaWaveThemePreference.ForestTechLight)

        assertEquals(
            ThemePreferenceState.Ready(PandaWaveThemePreference.ForestTechLight),
            observe().value
        )
    }

    @Test
    fun `theme preference wire values are stable`() {
        PandaWaveThemePreference.entries.forEach { preference ->
            assertEquals(
                preference,
                PandaWaveThemePreference.fromWireOrNull(preference.wireValue)
            )
        }

        assertNull(PandaWaveThemePreference.fromWireOrNull("not_a_theme"))
    }
}
