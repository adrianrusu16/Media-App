package com.adrianrusu.pandawave.appshell.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PandaWaveDestinationTest {
    @Test
    fun `rail exposes only primary destinations`() {
        assertEquals(
            listOf(
                HomeDestination,
                LibraryDestination,
                SearchDestination,
                ProfileDestination
            ),
            primaryDestinations
        )
    }

    @Test
    fun `primary destinations expose unique stable navigation ids`() {
        assertEquals(
            listOf("home", "library", "search", "profile"),
            primaryDestinations.map(PandaWaveDestination::navigationId)
        )
    }

    @Test
    fun `preferences keeps profile selected in the rail`() {
        assertEquals(ProfileDestination, PreferencesDestination.selectedRailDestination)
    }

    @Test
    fun `now playing has no selected rail destination`() {
        assertNull(NowPlayingDestination.selectedRailDestination)
    }

    @Test
    fun `mini player is hidden only on now playing`() {
        assertFalse(NowPlayingDestination.shouldShowMiniPlayer)
        assertTrue(HomeDestination.shouldShowMiniPlayer)
        assertTrue(PreferencesDestination.shouldShowMiniPlayer)
    }
}
