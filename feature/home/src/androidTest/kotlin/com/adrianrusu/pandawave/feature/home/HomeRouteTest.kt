package com.adrianrusu.pandawave.feature.home

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.adrianrusu.pandawave.core.designsystem.theme.PandaWaveTheme
import com.adrianrusu.pandawave.feature.home.domain.HomeState
import com.adrianrusu.pandawave.feature.home.domain.HomeTrack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeRouteTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tapping_a_home_song_starts_playback_and_opens_now_playing() {
        val actions = mutableListOf<String>()
        compose.setContent {
            PandaWaveTheme(darkTheme = true) {
                HomeRoute(
                    state = HomeState(
                        forYou = listOf(HomeTrack("track-1", "Song", "Artist", "Album")),
                    ),
                    onPlay = { id, _, _ -> actions += "play:$id" },
                    onOpenNowPlaying = { actions += "open-now-playing" },
                )
            }
        }

        compose.onNodeWithTag("home-for-you-track-1").performClick()

        assertEquals(listOf("play:track-1", "open-now-playing"), actions)
    }

    @Test
    fun empty_home_feeds_still_show_section_headers() {
        compose.setContent {
            PandaWaveTheme(darkTheme = true) {
                HomeRoute(
                    state = HomeState(),
                    onPlay = { _, _, _ -> },
                )
            }
        }

        compose.onNodeWithTag("home-for-you-empty").assertExists()
        compose.onNodeWithTag("home-recommendations-empty").assertExists()
        compose.onNodeWithTag("home-discover-empty").assertExists()
    }
}
