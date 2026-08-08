package com.adrianrusu.pandawave.feature.profile.presentation

import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
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
}

private class RecordingProfileRepository : ProfileRepository {
    override val state: StateFlow<ProfileState> = MutableStateFlow(ProfileState.Loading)
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

    override fun close() = Unit
}
