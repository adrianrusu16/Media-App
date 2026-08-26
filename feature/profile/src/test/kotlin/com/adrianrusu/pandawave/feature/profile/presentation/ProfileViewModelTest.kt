package com.adrianrusu.pandawave.feature.profile.presentation

import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.feature.profile.domain.AccountSessionsState
import com.adrianrusu.pandawave.feature.profile.domain.ProfileRepository
import com.adrianrusu.pandawave.feature.profile.domain.ProfileState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ProfileViewModelTest {
    @Test
    fun `view model starts repository and exposes its state`() {
        val repository = RecordingProfileRepository()

        val viewModel = ProfileViewModel(repository)

        assertEquals(1, repository.startCount)
        assertTrue(viewModel.state.value is ProfileState.Loading)
    }

    @Test
    fun `profile actions are forwarded without credentials`() {
        val repository = RecordingProfileRepository()
        val viewModel = ProfileViewModel(repository)

        viewModel.refresh()
        viewModel.upsert(null)
        viewModel.updateDisplayName("")
        viewModel.delete()
        viewModel.updateTheme(PandaWaveThemePreference.ForestTechDark)

        assertEquals(
            listOf(
                "refresh",
                "upsert:null",
                "update:",
                "delete",
                "theme:forest_tech_dark"
            ),
            repository.actions
        )
    }

    @Test
    fun `account session failure and actions are exposed without transformation`() {
        val repository = RecordingProfileRepository()
        val viewModel = ProfileViewModel(repository)
        repository.mutableAccountSessionsState.value =
            AccountSessionsState.Failure("network", retryable = true)

        viewModel.refreshAccountSessions()
        viewModel.loadNextDeviceSessionsPage()
        viewModel.revokeDeviceSession("session-other")
        viewModel.deleteAccount()

        assertEquals(
            AccountSessionsState.Failure("network", retryable = true),
            viewModel.accountSessionsState.value
        )
        assertEquals(
            listOf("sessions:refresh", "sessions:next", "sessions:revoke:session-other", "account:delete"),
            repository.actions
        )
    }
}

private class RecordingProfileRepository : ProfileRepository {
    override val state: StateFlow<ProfileState> = MutableStateFlow(ProfileState.Loading)
    val mutableAccountSessionsState = MutableStateFlow<AccountSessionsState>(AccountSessionsState.Loading)
    override val accountSessionsState: StateFlow<AccountSessionsState> = mutableAccountSessionsState
    var startCount = 0
    val actions = mutableListOf<String>()

    override fun start() {
        startCount += 1
    }

    override fun refresh() {
        actions += "refresh"
    }

    override fun upsert(displayName: String?) {
        actions += "upsert:$displayName"
    }

    override fun updateDisplayName(displayName: String?) {
        actions += "update:$displayName"
    }

    override fun delete() {
        actions += "delete"
    }

    override fun updateTheme(preference: PandaWaveThemePreference) {
        actions += "theme:${preference.wireValue}"
    }

    override fun refreshAccountSessions() {
        actions += "sessions:refresh"
    }
    override fun loadNextDeviceSessionsPage() {
        actions += "sessions:next"
    }
    override fun revokeDeviceSession(sessionId: String) {
        actions += "sessions:revoke:$sessionId"
    }
    override fun deleteAccount() {
        actions += "account:delete"
    }

    override fun close() = Unit
}
