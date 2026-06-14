package com.adrianrusu.mediaapp.appshell.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AppShellReducerTest {
    @Test
    fun `select destination updates current destination`() {
        val state = AppShellReducer.reduce(
            state = AppShellState(),
            intent = AppShellIntent.SelectDestination(AppDestination.Settings)
        )

        assertEquals(AppDestination.Settings, state.selectedDestination)
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
