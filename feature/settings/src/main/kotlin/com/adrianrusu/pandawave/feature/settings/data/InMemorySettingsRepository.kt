package com.adrianrusu.pandawave.feature.settings.data

import com.adrianrusu.pandawave.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.pandawave.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceState
import com.adrianrusu.pandawave.core.preferences.ThemePreferenceCoordinator
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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class InMemorySettingsRepository(
    private val uxRestrictionObserver: AutomotiveUxRestrictionObserver,
    private val themePreferenceCoordinator: ThemePreferenceCoordinator
) : SettingsRepository {
    private val _settingsState = MutableStateFlow(
        SettingsState(themePreference = themePreferenceCoordinator.currentPreference())
    )
    private var themePreferenceJob: Job? = null

    override val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    override fun start(scope: CoroutineScope) {
        if (themePreferenceJob != null) return

        uxRestrictionObserver.start { restrictions ->
            _settingsState.update { current ->
                current.copy(restriction = restrictions.toSettingsRestrictionState())
            }
        }
        themePreferenceJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            themePreferenceCoordinator.state
                .filterIsInstance<ThemePreferenceState.Ready>()
                .collect { ready ->
                    _settingsState.update { current -> current.copy(themePreference = ready.preference) }
                }
        }
    }

    override suspend fun dispatch(intent: SettingsIntent) {
        val current = _settingsState.value
        val next = SettingsReducer.reduce(current, intent)
        if (next.themePreference != current.themePreference) {
            themePreferenceCoordinator.select(next.themePreference)
        }
        _settingsState.value = next
    }

    override fun close() {
        themePreferenceJob?.cancel()
        themePreferenceJob = null
        uxRestrictionObserver.close()
    }
}

private fun ThemePreferenceCoordinator.currentPreference(): PandaWaveThemePreference =
    (state.value as? ThemePreferenceState.Ready)?.preference ?: PandaWaveThemePreference.SystemDefault

private fun AutomotiveUxRestrictions.toSettingsRestrictionState(): SettingsRestrictionState = SettingsRestrictionState(
    isRestricted = isRestricted
)
