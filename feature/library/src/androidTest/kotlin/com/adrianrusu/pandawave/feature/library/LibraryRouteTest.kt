package com.adrianrusu.pandawave.feature.library


import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.adrianrusu.pandawave.core.designsystem.theme.PandaWaveTheme
import com.adrianrusu.pandawave.feature.library.domain.LibraryState
import com.adrianrusu.pandawave.feature.library.domain.LibraryTab
import com.adrianrusu.pandawave.feature.library.domain.LibraryTrack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryRouteTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun savedAndLikedCollectionsAreReachableWithPendingPaginationAndMutations() {
        val actions = mutableListOf<String>()
        var state by mutableStateOf(
            LibraryState(
                savedTracks = listOf(
                    track("pending-1", "Pending Song"),
                    track("saved-1", "Saved Song"),
                ),
                likedTracks = listOf(track("liked-1", "Liked Song")),
                pendingMediaIds = setOf("pending-1"),
                hasSavedNextPage = true,
                isLoading = false,
            )
        )
        compose.setContent {
            PandaWaveTheme(darkTheme = true) {
                LibraryRoute(
                    state = state,
                    onSelectTab = { tab -> state = state.copy(selectedTab = tab) },
                    onRefresh = { actions += "refresh" },
                    onLoadNext = { actions += "next" },
                    onSave = { actions += "save:$it" },
                    onRemoveSaved = { actions += "remove:$it" },
                    onLike = { actions += "like:$it" },
                    onUnlike = { actions += "unlike:$it" },
                )
            }
        }

        compose.onNodeWithText("Saved Song").assertIsDisplayed()
        compose.onNodeWithTag("library-pending-pending-1").assertIsDisplayed()
        compose.onNodeWithTag("library-remove-pending-1").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("library-like-pending-1").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("library-next-page").performScrollTo().performClick()
        compose.onNodeWithTag("library-remove-saved-1").performScrollTo().performClick()
        compose.onNodeWithTag("library-like-saved-1").performScrollTo().performClick()

        compose.onNodeWithTag("library-tab-liked").performScrollTo().performClick()
        compose.onNodeWithText("Liked Song").assertIsDisplayed()
        compose.onNodeWithTag("library-unlike-liked-1").performScrollTo().performClick()
        compose.onNodeWithTag("library-save-liked-1").performScrollTo().performClick()

        assertEquals(
            listOf("next", "remove:saved-1", "like:saved-1", "unlike:liked-1", "save:liked-1"),
            actions,
        )
    }

    @Test
    fun libraryRouteRendersSignedOutLoadingEmptyAndTypedErrorStates() {
        setRoute(LibraryState(isSignedOut = true, isLoading = false))
        compose.onNodeWithTag("library-signed-out").assertIsDisplayed()

        setRoute(LibraryState(isLoading = true))
        compose.onNodeWithTag("library-loading").assertIsDisplayed()

        setRoute(LibraryState(isLoading = false))
        compose.onNodeWithTag("library-empty").assertIsDisplayed()

        setRoute(LibraryState(isLoading = false, errorType = "network", isRetryableError = true))
        compose.onNodeWithTag("library-error").assertIsDisplayed()
        compose.onNodeWithTag("library-retry").assertIsDisplayed()

        setRoute(LibraryState(isLoading = false, errorType = "unknown", isRetryableError = false))
        compose.onNodeWithTag("library-error").assertIsDisplayed()
    }

    private fun setRoute(state: LibraryState) {
        compose.setContent {
            PandaWaveTheme(darkTheme = true) {
                LibraryRoute(
                    state = state,
                    onSelectTab = {},
                    onRefresh = {},
                    onLoadNext = {},
                    onSave = {},
                    onRemoveSaved = {},
                    onLike = {},
                    onUnlike = {},
                )
            }
        }
    }

    private fun track(mediaId: String, title: String) = LibraryTrack(
        relationshipId = mediaId,
        mediaId = mediaId,
        title = title,
        artist = "Artist",
        album = null,
        durationMillis = 120_000,
        explicit = false,
        artworkId = null,
        relationshipAtEpochMillis = 1_000,
    )
}
