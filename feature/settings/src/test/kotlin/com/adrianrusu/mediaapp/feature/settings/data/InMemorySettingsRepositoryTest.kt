package com.adrianrusu.mediaapp.feature.settings.data

import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.mediaapp.core.model.theme.ThemePreferenceState
import com.adrianrusu.mediaapp.core.preferences.ThemePreferenceCoordinator
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class InMemorySettingsRepositoryTest {
    @Test
    fun `keeps settings interactive while projecting ux restrictions`() = runTest {
        val observer = RecordingUxRestrictionObserver()
        val repository = InMemorySettingsRepository(
            uxRestrictionObserver = observer,
            themePreferenceCoordinator = RecordingThemePreferenceCoordinator()
        )

        repository.start(backgroundScope)
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

        assertTrue(repository.settingsState.value.restriction.isRestricted)

        repository.dispatch(SettingsIntent.TogglePersonalization)

        assertTrue(repository.settingsState.value.personalizationEnabled)
    }

    @Test
    fun `dispatches setting intents when unrestricted`() = runTest {
        val repository = InMemorySettingsRepository(
            uxRestrictionObserver = RecordingUxRestrictionObserver(),
            themePreferenceCoordinator = RecordingThemePreferenceCoordinator()
        )

        repository.dispatch(SettingsIntent.TogglePersonalization)

        assertTrue(repository.settingsState.value.personalizationEnabled)
    }

    @Test
    fun `dispatches theme preference through coordinator`() = runTest {
        val coordinator = RecordingThemePreferenceCoordinator()
        val repository = InMemorySettingsRepository(
            uxRestrictionObserver = RecordingUxRestrictionObserver(),
            themePreferenceCoordinator = coordinator
        )

        repository.dispatch(SettingsIntent.SelectThemePreference(PandaWaveThemePreference.MoonlitBambooDark))

        assertEquals(PandaWaveThemePreference.MoonlitBambooDark, repository.settingsState.value.themePreference)
        assertEquals(PandaWaveThemePreference.MoonlitBambooDark, coordinator.selected.single())
    }

    @Test
    fun `projects remotely synchronized theme into settings`() = runTest {
        val coordinator = RecordingThemePreferenceCoordinator()
        val repository = InMemorySettingsRepository(
            uxRestrictionObserver = RecordingUxRestrictionObserver(),
            themePreferenceCoordinator = coordinator
        )
        repository.start(backgroundScope)

        coordinator.emit(PandaWaveThemePreference.ForestTechDark)
        runCurrent()

        assertEquals(PandaWaveThemePreference.ForestTechDark, repository.settingsState.value.themePreference)
    }
}

private class RecordingThemePreferenceCoordinator : ThemePreferenceCoordinator {
    private val mutableState = MutableStateFlow<ThemePreferenceState>(
        ThemePreferenceState.Ready(PandaWaveThemePreference.SystemDefault)
    )
    override val state: StateFlow<ThemePreferenceState> = mutableState
    val selected = mutableListOf<PandaWaveThemePreference>()

    override fun start() = Unit

    override suspend fun select(preference: PandaWaveThemePreference) {
        selected += preference
        emit(preference)
    }

    fun emit(preference: PandaWaveThemePreference) {
        mutableState.value = ThemePreferenceState.Ready(preference)
    }

    override fun close() = Unit
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
