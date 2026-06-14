package com.adrianrusu.mediaapp.feature.settings.data

import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.model.theme.InMemoryThemePreferenceRepository
import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemorySettingsRepositoryTest {
    @Test
    fun `applies ux restrictions to settings state`() {
        val observer = RecordingUxRestrictionObserver()
        val repository = InMemorySettingsRepository(
            uxRestrictionObserver = observer,
            themePreferenceRepository = InMemoryThemePreferenceRepository()
        )

        repository.start()
        observer.emit(
            AutomotiveUxRestrictions(
                source = AutomotiveUxRestrictions.Source.AutomotivePlatform,
                requiresDistractionOptimization = true,
                activeRestrictions = AutomotiveUxRestrictions.NO_RESTRICTIONS,
                maxContentDepth = 1,
                maxCumulativeContentItems = 2,
                maxRestrictedStringLength = 24
            )
        )

        assertTrue(repository.state.value.restriction.isRestricted)
        assertFalse(repository.state.value.controlsEnabled)
    }

    @Test
    fun `dispatches setting intents when unrestricted`() {
        val repository = InMemorySettingsRepository(
            uxRestrictionObserver = RecordingUxRestrictionObserver(),
            themePreferenceRepository = InMemoryThemePreferenceRepository()
        )

        repository.dispatch(SettingsIntent.TogglePersonalization)

        assertTrue(repository.state.value.personalizationEnabled)
    }

    @Test
    fun `dispatches theme preference to shared repository`() {
        val themePreferenceRepository = InMemoryThemePreferenceRepository()
        val repository = InMemorySettingsRepository(
            uxRestrictionObserver = RecordingUxRestrictionObserver(),
            themePreferenceRepository = themePreferenceRepository
        )

        repository.dispatch(SettingsIntent.SelectThemePreference(PandaWaveThemePreference.MoonlitBambooDark))

        assertEquals(PandaWaveThemePreference.MoonlitBambooDark, repository.state.value.themePreference)
        assertEquals(PandaWaveThemePreference.MoonlitBambooDark, themePreferenceRepository.preference.value)
    }
}

private class RecordingUxRestrictionObserver : AutomotiveUxRestrictionObserver {
    private var listener: ((AutomotiveUxRestrictions) -> Unit)? = null

    override fun current(): AutomotiveUxRestrictions =
        AutomotiveUxRestrictions.unrestricted(AutomotiveUxRestrictions.Source.NotAutomotive)

    override fun start(onChanged: (AutomotiveUxRestrictions) -> Unit) {
        listener = onChanged
        onChanged(current())
    }

    fun emit(restrictions: AutomotiveUxRestrictions) {
        listener?.invoke(restrictions)
    }

    override fun close() {
        listener = null
    }
}
