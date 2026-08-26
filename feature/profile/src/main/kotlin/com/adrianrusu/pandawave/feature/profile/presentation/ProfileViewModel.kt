package com.adrianrusu.pandawave.feature.profile.presentation

import androidx.lifecycle.ViewModel
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.feature.profile.domain.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(private val repository: ProfileRepository) : ViewModel() {
    val state = repository.state
    val accountSessionsState = repository.accountSessionsState

    init {
        repository.start()
    }

    fun refresh() = repository.refresh()

    fun upsert(displayName: String?) = repository.upsert(displayName)

    fun updateDisplayName(displayName: String?) = repository.updateDisplayName(displayName)

    fun delete() = repository.delete()

    fun updateTheme(preference: PandaWaveThemePreference) = repository.updateTheme(preference)
    fun refreshAccountSessions() = repository.refreshAccountSessions()
    fun loadNextDeviceSessionsPage() = repository.loadNextDeviceSessionsPage()
    fun revokeDeviceSession(sessionId: String) = repository.revokeDeviceSession(sessionId)
    fun deleteAccount() = repository.deleteAccount()

    override fun onCleared() {
        repository.close()
    }
}
