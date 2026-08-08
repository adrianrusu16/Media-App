package com.adrianrusu.pandawave.feature.profile.presentation

import androidx.lifecycle.ViewModel
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.feature.profile.domain.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository
) : ViewModel() {
    val state = repository.state

    init {
        repository.start()
    }

    fun refresh() = repository.refresh()

    fun upsert(displayName: String?) = repository.upsert(displayName)

    fun updateDisplayName(displayName: String?) = repository.updateDisplayName(displayName)

    fun delete() = repository.delete()

    fun updateTheme(preference: PandaWaveThemePreference) = repository.updateTheme(preference)

    override fun onCleared() {
        repository.close()
    }
}