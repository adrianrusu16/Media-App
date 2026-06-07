package com.adrianrusu.mediaapp.appshell.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AppShellReducerTest {
    @Test
    fun selectDestinationUpdatesCurrentDestination() {
        val state = AppShellReducer.reduce(
            state = AppShellState(),
            intent = AppShellIntent.SelectDestination(AppDestination.Settings),
        )

        assertEquals(AppDestination.Settings, state.selectedDestination)
    }

    @Test
    fun togglePlaybackDoesNotMutatePlaybackStateDirectly() {
        val state = AppShellReducer.reduce(
            state = AppShellState(),
            intent = AppShellIntent.TogglePlayback,
        )

        assertEquals(AppShellState(), state)
    }
}
