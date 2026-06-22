package com.adrianrusu.pandawave.appshell.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AppShellReducerTest {
    @Test
    fun `rail exposes only primary destinations`() {
        assertEquals(
            listOf(
                AppDestination.Home,
                AppDestination.Library,
                AppDestination.Search,
                AppDestination.Profile
            ),
            AppShellState().destinations
        )
    }

    @Test
    fun `select destination updates current primary destination`() {
        val state = AppShellReducer.reduce(
            state = AppShellState(),
            intent = AppShellIntent.SelectDestination(AppDestination.Library)
        )

        assertEquals(AppDestination.Library, state.selectedDestination)
    }

    @Test
    fun `open now playing remembers current primary destination`() {
        val state = AppShellReducer.reduce(
            state = AppShellState(selectedDestination = AppDestination.Library),
            intent = AppShellIntent.OpenNowPlaying
        )

        assertEquals(AppDestination.NowPlaying, state.selectedDestination)
        assertEquals(AppDestination.Library, state.previousPrimaryDestination)
    }

    @Test
    fun `back from now playing returns to previous primary destination`() {
        val state = AppShellReducer.reduce(
            state = AppShellState(
                selectedDestination = AppDestination.NowPlaying,
                previousPrimaryDestination = AppDestination.Search
            ),
            intent = AppShellIntent.NavigateBack
        )

        assertEquals(AppDestination.Search, state.selectedDestination)
    }

    @Test
    fun `now playing has no selected rail destination`() {
        val state = AppShellState(selectedDestination = AppDestination.NowPlaying)

        assertEquals(null, state.selectedRailDestination)
    }

    @Test
    fun `open profile settings selects internal settings destination`() {
        val state = AppShellReducer.reduce(
            state = AppShellState(selectedDestination = AppDestination.Profile),
            intent = AppShellIntent.OpenProfileSettings
        )

        assertEquals(AppDestination.ProfileSettings, state.selectedDestination)
    }

    @Test
    fun `back from profile settings returns to profile`() {
        val state = AppShellReducer.reduce(
            state = AppShellState(selectedDestination = AppDestination.ProfileSettings),
            intent = AppShellIntent.NavigateBack
        )

        assertEquals(AppDestination.Profile, state.selectedDestination)
    }

    @Test
    fun `profile settings keeps profile selected in rail`() {
        val state = AppShellState(selectedDestination = AppDestination.ProfileSettings)

        assertEquals(AppDestination.Profile, state.selectedRailDestination)
    }

    @Test
    fun `toggle playback does not mutate playback state directly`() {
        val state = AppShellReducer.reduce(
            state = AppShellState(),
            intent = AppShellIntent.TogglePlayback
        )

        assertEquals(AppShellState(), state)
    }
}
