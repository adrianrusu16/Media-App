package com.adrianrusu.pandawave.feature.profile.domain

import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import kotlinx.coroutines.flow.StateFlow

interface ProfileRepository : AutoCloseable {
    val state: StateFlow<ProfileState>
    val accountSessionsState: StateFlow<AccountSessionsState>

    fun start()
    fun refresh()
    fun upsert(displayName: String?)
    fun updateDisplayName(displayName: String?)
    fun delete()
    fun updateTheme(preference: PandaWaveThemePreference)
    fun refreshAccountSessions()
    fun loadNextDeviceSessionsPage()
    fun revokeDeviceSession(sessionId: String)
    fun deleteAccount()
}
