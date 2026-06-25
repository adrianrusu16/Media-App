package com.adrianrusu.pandawave.feature.settings.data

import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionState
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceState
import com.adrianrusu.pandawave.core.playback.BambooDrivingState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.playback.BambooRestrictionState
import com.adrianrusu.pandawave.core.playback.BambooVehicleSafetyState
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceRepository
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceState
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferences
import com.adrianrusu.pandawave.core.preferences.ThemePreferenceCoordinator
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.feature.settings.domain.SettingsIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class InMemorySettingsRepositoryTest {
    @Test
    fun `keeps settings interactive while projecting engine safety`() = runTest {
        val playback = RecordingPlaybackRepository()
        val repository = createRepository(playbackRepository = playback)

        repository.start(backgroundScope)
        playback.emitSafety(
            BambooVehicleSafetyState(
                drivingState = BambooDrivingState.Moving,
                restrictionState = BambooRestrictionState.Restricted
            )
        )
        runCurrent()

        assertTrue(repository.settingsState.value.restriction.isRestricted)

        repository.dispatch(SettingsIntent.TogglePersonalization)

        assertTrue(repository.settingsState.value.personalizationEnabled)
    }

    @Test
    fun `parked unrestricted engine state permits ambient actions`() = runTest {
        val playback = RecordingPlaybackRepository()
        val repository = createRepository(playbackRepository = playback)
        repository.start(backgroundScope)

        playback.emitSafety(
            BambooVehicleSafetyState(
                drivingState = BambooDrivingState.Parked,
                restrictionState = BambooRestrictionState.Unrestricted
            )
        )
        runCurrent()

        assertFalse(repository.settingsState.value.restriction.isRestricted)
    }

    @Test
    fun `settings restriction follows ux state without depending on driving state`() = runTest {
        val playback = RecordingPlaybackRepository()
        val repository = createRepository(playbackRepository = playback)
        repository.start(backgroundScope)

        playback.emitSafety(
            BambooVehicleSafetyState(
                drivingState = BambooDrivingState.Moving,
                restrictionState = BambooRestrictionState.Unrestricted
            )
        )
        runCurrent()

        assertFalse(repository.settingsState.value.restriction.isRestricted)
    }

    @Test
    fun `dispatches theme preference through coordinator`() = runTest {
        val coordinator = RecordingThemePreferenceCoordinator()
        val repository = createRepository(themePreferenceCoordinator = coordinator)

        repository.dispatch(SettingsIntent.SelectThemePreference(PandaWaveThemePreference.MoonlitBambooDark))

        assertEquals(PandaWaveThemePreference.MoonlitBambooDark, repository.settingsState.value.themePreference)
        assertEquals(PandaWaveThemePreference.MoonlitBambooDark, coordinator.selected.single())
    }

    @Test
    fun `projects remotely synchronized theme into settings`() = runTest {
        val coordinator = RecordingThemePreferenceCoordinator()
        val repository = createRepository(themePreferenceCoordinator = coordinator)
        repository.start(backgroundScope)

        coordinator.emit(PandaWaveThemePreference.ForestTechDark)
        runCurrent()

        assertEquals(PandaWaveThemePreference.ForestTechDark, repository.settingsState.value.themePreference)
    }

    @Test
    fun `projects ambient preferences from repository`() = runTest {
        val ambientPreferences = RecordingAmbientModePreferenceRepository()
        val repository = createRepository(ambientModePreferenceRepository = ambientPreferences)
        repository.start(backgroundScope)

        ambientPreferences.emit(AmbientModePreferences(enabled = false, timeoutSeconds = 35))
        runCurrent()

        assertFalse(repository.settingsState.value.ambientModeEnabled)
        assertEquals(35, repository.settingsState.value.ambientTimeoutSeconds)
    }

    @Test
    fun `projects visualizer permission from repository`() = runTest {
        val permission = RecordingVisualizerPermissionRepository()
        val repository = createRepository(visualizerPermissionRepository = permission)
        repository.start(backgroundScope)

        permission.emit(VisualizerPermissionState.Denied(canRequest = false))
        runCurrent()

        assertEquals(
            VisualizerPermissionState.Denied(canRequest = false),
            repository.settingsState.value.visualizerPermissionState
        )
    }

    @Test
    fun `ambient enablement intent persists without speculative state`() = runTest {
        val ambientPreferences = RecordingAmbientModePreferenceRepository()
        val repository = createRepository(ambientModePreferenceRepository = ambientPreferences)

        repository.dispatch(SettingsIntent.SetAmbientModeEnabled(false))

        assertEquals(listOf(false), ambientPreferences.enabledWrites)
        assertTrue(repository.settingsState.value.ambientModeEnabled)
    }

    @Test
    fun `ambient timeout intent persists without speculative state`() = runTest {
        val ambientPreferences = RecordingAmbientModePreferenceRepository()
        val repository = createRepository(ambientModePreferenceRepository = ambientPreferences)

        repository.dispatch(SettingsIntent.SetAmbientTimeoutSeconds(40))

        assertEquals(listOf(40), ambientPreferences.timeoutWrites)
        assertEquals(15, repository.settingsState.value.ambientTimeoutSeconds)
    }

    private fun createRepository(
        playbackRepository: BambooPlaybackRepository = RecordingPlaybackRepository(),
        themePreferenceCoordinator: ThemePreferenceCoordinator = RecordingThemePreferenceCoordinator(),
        ambientModePreferenceRepository: AmbientModePreferenceRepository = RecordingAmbientModePreferenceRepository(),
        visualizerPermissionRepository: VisualizerPermissionRepository = RecordingVisualizerPermissionRepository()
    ): InMemorySettingsRepository = InMemorySettingsRepository(
        playbackRepository = playbackRepository,
        themePreferenceCoordinator = themePreferenceCoordinator,
        ambientModePreferenceRepository = ambientModePreferenceRepository,
        visualizerPermissionRepository = visualizerPermissionRepository
    )
}

private class RecordingVisualizerPermissionRepository : VisualizerPermissionRepository {
    private val mutableState = MutableStateFlow<VisualizerPermissionState>(VisualizerPermissionState.Unknown)
    override val state: StateFlow<VisualizerPermissionState> = mutableState

    override suspend fun markRequestLaunched() = Unit

    override fun refresh(shouldShowRationale: Boolean) = Unit

    override fun onRequestResult(granted: Boolean, shouldShowRationale: Boolean) = Unit

    fun emit(state: VisualizerPermissionState) {
        mutableState.value = state
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

private class RecordingAmbientModePreferenceRepository : AmbientModePreferenceRepository {
    private val mutableState = MutableStateFlow<AmbientModePreferenceState>(
        AmbientModePreferenceState.Ready(AmbientModePreferences())
    )
    override val state: StateFlow<AmbientModePreferenceState> = mutableState
    val enabledWrites = mutableListOf<Boolean>()
    val timeoutWrites = mutableListOf<Int>()

    override suspend fun setEnabled(enabled: Boolean) {
        enabledWrites += enabled
    }

    override suspend fun setTimeoutSeconds(timeoutSeconds: Int) {
        timeoutWrites += timeoutSeconds
    }

    fun emit(preferences: AmbientModePreferences) {
        mutableState.value = AmbientModePreferenceState.Ready(preferences)
    }
}

private class RecordingPlaybackRepository : BambooPlaybackRepository {
    private val mutableState = MutableStateFlow(BambooPlaybackState())
    override val state: StateFlow<BambooPlaybackState> = mutableState

    override fun start() = Unit

    override fun dispatch(intent: BambooPlaybackIntent) = Unit

    override fun observe(listener: (BambooPlaybackState) -> Unit): AutoCloseable {
        listener(state.value)
        return AutoCloseable { }
    }

    override fun observeEffects(listener: (List<EngineEffect>) -> Unit): AutoCloseable = AutoCloseable { }

    fun emitSafety(safety: BambooVehicleSafetyState) {
        mutableState.value = mutableState.value.copy(vehicleSafety = safety)
    }

    override fun close() = Unit
}
