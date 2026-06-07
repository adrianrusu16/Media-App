package com.adrianrusu.mediaapp.appshell.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun togglePlaybackFlipsMiniPlayerPlaybackState() {
        val playingState = AppShellReducer.reduce(
            state = AppShellState(),
            intent = AppShellIntent.TogglePlayback,
        )

        assertTrue(playingState.miniPlayer.isPlaying)

        val pausedState = AppShellReducer.reduce(
            state = playingState,
            intent = AppShellIntent.TogglePlayback,
        )

        assertFalse(pausedState.miniPlayer.isPlaying)
    }
}
