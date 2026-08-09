package com.adrianrusu.pandawave.feature.settings.data

import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionState
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceRepository
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceState
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferences
import com.adrianrusu.pandawave.core.preferences.ThemePreferenceCoordinator
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.feature.settings.domain.SettingsIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest

class HistorySettingsRepositoryTest {
    @Test
    fun `settings loads server consent and disabling accepts purge projection`() = runTest {
        val engine = HistoryRecordingEngineGateway()
        val repository = InMemorySettingsRepository(
            playbackRepository = HistoryPlaybackRepository(),
            themePreferenceCoordinator = HistoryThemeCoordinator(),
            ambientModePreferenceRepository = HistoryAmbientRepository(),
            visualizerPermissionRepository = HistoryPermissionRepository(),
            engineGateway = engine
        )

        repository.start(backgroundScope)

        assertEquals(
            listOf(EngineCommand.TYPE_LOAD_HISTORY_SETTINGS, EngineCommand.TYPE_LIST_HISTORY),
            engine.commands.take(2).map { it.type },
        )
        assertTrue(repository.settingsState.value.historyEnabled)
        assertEquals(2, repository.settingsState.value.historyEntriesCount)

        repository.dispatch(SettingsIntent.SetHistoryEnabled(false))

        assertEquals(EngineCommand.TYPE_UPDATE_HISTORY_SETTINGS, engine.commands.last().type)
        assertFalse(repository.settingsState.value.historyEnabled)
        assertEquals(2L, repository.settingsState.value.historyDeletedCount)
        assertEquals(0, repository.settingsState.value.historyEntriesCount)
    }
}

private class HistoryRecordingEngineGateway : EngineGateway {
    private var current = EngineSnapshot.idle(0).copy(
        hasHistorySettings = true,
        historyEnabled = true,
        historyEntriesCount = 0
    )
    val commands = mutableListOf<EngineCommand>()
    private val listeners = mutableListOf<(EngineSnapshot) -> Unit>()

    override fun snapshot() = current
    override fun browseResult(index: Int): EngineCatalogItem? = null
    override fun searchResult(index: Int): EngineCatalogItem? = null
    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commands += command
        if (command.type == EngineCommand.TYPE_LIST_HISTORY) {
            current = current.copy(historyEntriesCount = 2)
            listeners.forEach { it(current) }
        } else if (command.type == EngineCommand.TYPE_UPDATE_HISTORY_SETTINGS) {
            current = current.copy(
                hasHistorySettings = true,
                historyEnabled = false,
                historyDeletedCount = 2,
                historyEntriesCount = 0
            )
            listeners.forEach { it(current) }
        }
        return EngineDispatchResult(current, EngineEvent(EngineEvent.TYPE_COMMAND_APPLIED, command.type), emptyList())
    }
    override fun dispatchPlatformEvent(event: EnginePlatformEvent) =
        EngineDispatchResult(current, EngineEvent(EngineEvent.TYPE_PLATFORM_EVENT_APPLIED, event.type), emptyList())
    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        listeners += listener
        listener(current)
        return AutoCloseable { listeners -= listener }
    }
    override fun observeEngineEvents(listener: (EngineEvent) -> Unit) = AutoCloseable { }
}

private class HistoryPlaybackRepository : BambooPlaybackRepository {
    override val state: StateFlow<BambooPlaybackState> = MutableStateFlow(BambooPlaybackState())
    override fun start() = Unit
    override fun dispatch(intent: BambooPlaybackIntent) = Unit
    override fun observe(listener: (BambooPlaybackState) -> Unit) = AutoCloseable { }
    override fun observeEffects(listener: (List<EngineEffect>) -> Unit) = AutoCloseable { }
    override fun close() = Unit
}

private class HistoryThemeCoordinator : ThemePreferenceCoordinator {
    override val state: StateFlow<ThemePreferenceState> = MutableStateFlow(ThemePreferenceState.Ready(PandaWaveThemePreference.SystemDefault))
    override fun start() = Unit
    override suspend fun select(preference: PandaWaveThemePreference) = Unit
    override fun close() = Unit
}

private class HistoryAmbientRepository : AmbientModePreferenceRepository {
    override val state: StateFlow<AmbientModePreferenceState> = MutableStateFlow(AmbientModePreferenceState.Ready(AmbientModePreferences()))
    override suspend fun setEnabled(enabled: Boolean) = Unit
    override suspend fun setTimeoutSeconds(timeoutSeconds: Int) = Unit
}

private class HistoryPermissionRepository : VisualizerPermissionRepository {
    override val state: StateFlow<VisualizerPermissionState> = MutableStateFlow(VisualizerPermissionState.Unknown)
    override suspend fun markRequestLaunched() = Unit
    override fun refresh(shouldShowRationale: Boolean) = Unit
    override fun onRequestResult(granted: Boolean, shouldShowRationale: Boolean) = Unit
}
