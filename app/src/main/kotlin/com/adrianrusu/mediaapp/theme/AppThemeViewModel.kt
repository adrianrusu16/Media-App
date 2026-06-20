package com.adrianrusu.mediaapp.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.mediaapp.core.model.theme.ThemePreferenceState
import com.adrianrusu.mediaapp.core.preferences.ThemePreferenceCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppThemeViewModel @Inject constructor(private val coordinator: ThemePreferenceCoordinator) : ViewModel() {
    val preference: StateFlow<PandaWaveThemePreference> = coordinator.state
        .map { state ->
            (state as? ThemePreferenceState.Ready)?.preference
                ?: PandaWaveThemePreference.SystemDefault
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PandaWaveThemePreference.SystemDefault
        )

    fun select(preference: PandaWaveThemePreference) {
        viewModelScope.launch { coordinator.select(preference) }
    }
}
