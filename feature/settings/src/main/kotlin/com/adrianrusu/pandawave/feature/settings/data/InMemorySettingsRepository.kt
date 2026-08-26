package com.adrianrusu.pandawave.feature.settings.data

import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionState
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceRepository
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceState
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferences
import com.adrianrusu.pandawave.core.preferences.ThemePreferenceCoordinator
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.feature.settings.domain.SettingsIntent
import com.adrianrusu.pandawave.feature.settings.domain.SettingsReducer
import com.adrianrusu.pandawave.feature.settings.domain.SettingsRepository
import com.adrianrusu.pandawave.feature.settings.domain.SettingsRestrictionState
import com.adrianrusu.pandawave.feature.settings.domain.SettingsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class InMemorySettingsRepository(
    private val playbackRepository: BambooPlaybackRepository,
    private val themePreferenceCoordinator: ThemePreferenceCoordinator,
    private val ambientModePreferenceRepository: AmbientModePreferenceRepository,
    private val visualizerPermissionRepository: VisualizerPermissionRepository,
    private val engineGateway: EngineGateway? = null
) : SettingsRepository {
    private val _settingsState = MutableStateFlow(
        SettingsState(
            themePreference = themePreferenceCoordinator.currentPreference(),
            ambientModeEnabled = ambientModePreferenceRepository.currentPreferences().enabled,
            ambientTimeoutSeconds = ambientModePreferenceRepository.currentPreferences().timeoutSeconds,
            visualizerPermissionState = visualizerPermissionRepository.state.value,
            restriction = playbackRepository.state.value.toSettingsRestrictionState()
        )
    )
    private var projectionJob: Job? = null
    private var historySnapshotSubscription: AutoCloseable? = null

    override val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    override fun start(scope: CoroutineScope) {
        if (projectionJob != null) return

        historySnapshotSubscription = engineGateway?.observeSnapshots(::projectHistory)
        dispatchHistory(EngineCommand(EngineCommand.TYPE_LOAD_HISTORY_SETTINGS, null))
        dispatchHistory(
            EngineCommand(
                EngineCommand.TYPE_LIST_HISTORY,
                EngineCommandPayloads.historyPage(DEFAULT_HISTORY_PAGE_SIZE)
            )
        )

        projectionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            combine(
                themePreferenceCoordinator.state,
                ambientModePreferenceRepository.state,
                playbackRepository.state,
                visualizerPermissionRepository.state
            ) { themeState, ambientState, playbackState, permissionState ->
                SettingsProjection(themeState, ambientState, playbackState, permissionState)
            }.collect { projection ->
                val current = _settingsState.value
                val ambientPreferences = projection.ambientState.readyPreferencesOr(current)
                _settingsState.value = current.copy(
                    themePreference = projection.themeState.readyPreferenceOr(current.themePreference),
                    ambientModeEnabled = ambientPreferences.enabled,
                    ambientTimeoutSeconds = ambientPreferences.timeoutSeconds,
                    visualizerPermissionState = projection.permissionState,
                    restriction = projection.playbackState.toSettingsRestrictionState()
                )
            }
        }
    }

    override suspend fun dispatch(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SetHistoryEnabled -> dispatchHistory(
                EngineCommand(
                    EngineCommand.TYPE_UPDATE_HISTORY_SETTINGS,
                    EngineCommandPayloads.historyEnabled(intent.enabled)
                )
            )

            SettingsIntent.ClearHistory -> dispatchHistory(EngineCommand(EngineCommand.TYPE_CLEAR_HISTORY, null))

            SettingsIntent.LoadNextHistoryPage ->
                dispatchHistory(EngineCommand(EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE, null))

            is SettingsIntent.DeleteHistoryEntry -> dispatchHistory(
                EngineCommand(
                    EngineCommand.TYPE_DELETE_HISTORY_ENTRY,
                    EngineCommandPayloads.historyEntry(intent.historyId)
                )
            )

            SettingsIntent.RefreshHistory -> dispatchHistory(
                EngineCommand(
                    EngineCommand.TYPE_LIST_HISTORY,
                    EngineCommandPayloads.historyPage(DEFAULT_HISTORY_PAGE_SIZE)
                )
            )

            is SettingsIntent.SetAmbientModeEnabled -> ambientModePreferenceRepository.setEnabled(intent.enabled)

            is SettingsIntent.SetAmbientTimeoutSeconds ->
                ambientModePreferenceRepository.setTimeoutSeconds(intent.timeoutSeconds)

            else -> reduceLocalState(intent)
        }
    }

    private fun dispatchHistory(command: EngineCommand) {
        engineGateway?.dispatch(command)?.snapshot?.let(::projectHistory)
    }

    private fun projectHistory(snapshot: EngineSnapshot) {
        val current = _settingsState.value
        _settingsState.value = current.copy(
            hasHistorySettings = snapshot.hasHistorySettings,
            historyEnabled = snapshot.hasHistorySettings && snapshot.historyEnabled,
            historyDeletedCount = if (snapshot.hasHistorySettings) snapshot.historyDeletedCount else 0L,
            historyEntriesCount = if (snapshot.hasHistorySettings) snapshot.historyEntriesCount else 0
        )
    }

    private suspend fun reduceLocalState(intent: SettingsIntent) {
        val current = _settingsState.value
        val next = SettingsReducer.reduce(current, intent)
        if (next.themePreference != current.themePreference) {
            themePreferenceCoordinator.select(next.themePreference)
        }
        _settingsState.value = next
    }

    override fun close() {
        projectionJob?.cancel()
        projectionJob = null
        historySnapshotSubscription?.close()
        historySnapshotSubscription = null
    }
}

private const val DEFAULT_HISTORY_PAGE_SIZE = 50

private data class SettingsProjection(
    val themeState: ThemePreferenceState,
    val ambientState: AmbientModePreferenceState,
    val playbackState: BambooPlaybackState,
    val permissionState: VisualizerPermissionState
)

private fun ThemePreferenceCoordinator.currentPreference(): PandaWaveThemePreference =
    (state.value as? ThemePreferenceState.Ready)?.preference ?: PandaWaveThemePreference.SystemDefault

private fun ThemePreferenceState.readyPreferenceOr(fallback: PandaWaveThemePreference): PandaWaveThemePreference =
    (this as? ThemePreferenceState.Ready)?.preference ?: fallback

private fun AmbientModePreferenceRepository.currentPreferences(): AmbientModePreferences =
    (state.value as? AmbientModePreferenceState.Ready)?.preferences ?: AmbientModePreferences()

private fun AmbientModePreferenceState.readyPreferencesOr(fallback: SettingsState): AmbientModePreferences =
    (this as? AmbientModePreferenceState.Ready)?.preferences ?: AmbientModePreferences(
        enabled = fallback.ambientModeEnabled,
        timeoutSeconds = fallback.ambientTimeoutSeconds
    )

private fun BambooPlaybackState.toSettingsRestrictionState(): SettingsRestrictionState = SettingsRestrictionState(
    isRestricted = !vehicleSafety.isUxUnrestricted
)
