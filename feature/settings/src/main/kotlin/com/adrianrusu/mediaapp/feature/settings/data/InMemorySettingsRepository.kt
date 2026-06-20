package com.adrianrusu.mediaapp.feature.settings.data

import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.model.theme.ThemePreferenceRepository
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsIntent
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsReducer
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsRepository
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsRestrictionState
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class InMemorySettingsRepository(
    private val uxRestrictionObserver: AutomotiveUxRestrictionObserver,
    private val themePreferenceRepository: ThemePreferenceRepository
) : SettingsRepository {
    private val _settingsState = MutableStateFlow(
        SettingsState(themePreference = themePreferenceRepository.preference.value)
    )

    override val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    override fun start() {
        uxRestrictionObserver.start { restrictions ->
            _settingsState.update { current ->
                current.copy(restriction = restrictions.toSettingsRestrictionState())
            }
        }
    }

    override fun dispatch(intent: SettingsIntent) {
        _settingsState.update { current ->
            val next = SettingsReducer.reduce(current, intent)
            if (next.themePreference != current.themePreference) {
                themePreferenceRepository.setPreference(next.themePreference)
            }
            next
        }
    }

    override fun close() {
        uxRestrictionObserver.close()
    }
}

private fun AutomotiveUxRestrictions.toSettingsRestrictionState(): SettingsRestrictionState {
    val label = when (source) {
        AutomotiveUxRestrictions.Source.AutomotivePlatform ->
            if (isRestricted) "Parked required" else "Ready"

        AutomotiveUxRestrictions.Source.NotAutomotive ->
            "Standard device"

        AutomotiveUxRestrictions.Source.Unavailable ->
            "Safety status unavailable"
    }

    return SettingsRestrictionState(
        label = label,
        isRestricted = isRestricted
    )
}
