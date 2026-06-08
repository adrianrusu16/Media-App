package com.adrianrusu.mediaapp.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppThemeViewModel @Inject constructor(
    observeThemePreference: ObserveThemePreferenceUseCase,
    private val setThemePreference: SetThemePreferenceUseCase
) : ViewModel() {
    val preference: StateFlow<PandaWaveThemePreference> = observeThemePreference()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PandaWaveThemePreference.SystemDefault
        )

    fun select(preference: PandaWaveThemePreference) {
        setThemePreference(preference)
    }
}
