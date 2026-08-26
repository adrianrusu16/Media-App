package com.adrianrusu.pandawave.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.adrianrusu.pandawave.core.designsystem.theme.PandaWaveTheme
import com.adrianrusu.pandawave.feature.library.domain.LibraryPlaylist
import com.adrianrusu.pandawave.feature.library.domain.LibraryState
import com.adrianrusu.pandawave.feature.library.domain.LibraryTab
import com.adrianrusu.pandawave.feature.library.domain.LibraryTrack
import com.adrianrusu.pandawave.feature.library.domain.PlaylistConflict
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
                    track("saved-1", "Saved Song")
                ),
                likedTracks = listOf(track("liked-1", "Liked Song")),
                pendingMediaIds = setOf("pending-1"),
                hasSavedNextPage = true,
                isLoading = false
            )
        )
        compose.setContent {
            PandaWaveTheme(darkTheme = true) {
                LibraryRoute(
                    state = state,
                    onSelectTab = { tab -> state = state.copy(selectedTab = tab) },
                    onRefresh = { actions += "refresh" },
                    onLoadNext = { actions += "next" },
                    onPlay = { actions += "play:$it" },
                    onOpenNowPlaying = { actions += "open-now-playing" },
                    onSave = { actions += "save:$it" },
                    onRemoveSaved = { actions += "remove:$it" },
                    onLike = { actions += "like:$it" },
                    onUnlike = { actions += "unlike:$it" }
                )
            }
        }

        compose.onNodeWithText("Saved Song").assertIsDisplayed()
        compose.onNodeWithTag("library-pending-pending-1").assertIsDisplayed()
        compose.onNodeWithTag("library-remove-pending-1").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("library-like-pending-1").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("library-next-page").performScrollTo().performClick()
        compose.onNodeWithTag("library-track-saved-1").performScrollTo().performClick()
        compose.onNodeWithTag("library-remove-saved-1").performScrollTo().performClick()
        compose.onNodeWithTag("library-like-saved-1").performScrollTo().performClick()

        compose.onNodeWithTag("library-tab-liked").performScrollTo().performClick()
        compose.onNodeWithText("Liked Song").assertIsDisplayed()
        compose.onNodeWithTag("library-unlike-liked-1").performScrollTo().performClick()
        compose.onNodeWithTag("library-save-liked-1").performScrollTo().performClick()

        assertEquals(
            listOf(
                "next",
                "play:saved-1",
                "open-now-playing",
                "remove:saved-1",
                "like:saved-1",
                "unlike:liked-1",
                "save:liked-1"
            ),
            actions
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

    @Test
    fun playlistsExposeCrudMembershipAndExplicitConflictConfirmation() {
        val actions = mutableListOf<String>()
        val state = LibraryState(
            selectedTab = LibraryTab.PLAYLISTS,
            playlists = listOf(LibraryPlaylist("playlist-1", "Road trip", "For the drive", 7)),
            selectedPlaylistId = "playlist-1",
            playlistTracks = listOf(
                track(mediaId = "media-1", title = "First", relationshipId = "member-1"),
                track(mediaId = "media-2", title = "Second", relationshipId = "member-2")
            ),
            playlistConflict = PlaylistConflict(
                playlistId = "playlist-1",
                expectedRevision = 7,
                serverRevision = 8,
                serverMembershipIds = listOf("member-2", "member-1"),
                proposedMembershipIds = listOf("member-1", "member-2")
            ),
            isLoading = false
        )
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
                    onCreatePlaylist = { name, description -> actions += "create:$name:$description" },
                    onUpdatePlaylist = { id, name, description, revision ->
                        actions +=
                            "update:$id:$name:$description:$revision"
                    },
                    onDeletePlaylist = { actions += "delete:$it" },
                    onSelectPlaylist = { actions += "select:$it" },
                    onAddPlaylistTrack = { id, mediaId -> actions += "add:$id:$mediaId" },
                    onRemovePlaylistTrack = { id, mediaId -> actions += "remove:$id:$mediaId" },
                    onReorderPlaylist = { id, membershipIds, revision ->
                        actions +=
                            "reorder:$id:${membershipIds.joinToString(",")}:$revision"
                    }
                )
            }
        }

        compose.onNodeWithTag("library-playlist-name").performScrollTo().performTextInput("New mix")
        compose.onNodeWithTag("library-create-playlist").performScrollTo().performClick()
        compose.onNodeWithTag("library-playlist-name").performScrollTo().performTextInput("Edited mix")
        compose.onNodeWithTag("library-playlist-update").performScrollTo().performClick()
        compose.onNodeWithTag("library-playlist-track-id").performScrollTo().performTextInput("media-3")
        compose.onNodeWithTag("library-playlist-add-track").performScrollTo().performClick()
        compose.onNodeWithTag("library-playlist-remove-track-member-1").performScrollTo().performClick()
        compose.onNodeWithTag("library-playlist-select-playlist-1").performScrollTo().performClick()
        compose.onNodeWithTag("library-playlist-delete-playlist-1").performScrollTo().performClick()
        compose.onNodeWithTag("library-playlist-conflict").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Server order: member-2, member-1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Your order: member-1, member-2").performScrollTo().assertIsDisplayed()
        assertEquals(
            listOf(
                "create:New mix:",
                "update:playlist-1:Edited mix::7",
                "add:playlist-1:media-3",
                "remove:playlist-1:media-1",
                "select:playlist-1",
                "delete:playlist-1"
            ),
            actions
        )

        compose.onNodeWithTag("library-playlist-confirm-reorder").performScrollTo().performClick()
        assertEquals(
            listOf(
                "create:New mix:",
                "update:playlist-1:Edited mix::7",
                "add:playlist-1:media-3",
                "remove:playlist-1:media-1",
                "select:playlist-1",
                "delete:playlist-1",
                "reorder:playlist-1:member-1,member-2:8"
            ),
            actions
        )
    }

    @Test
    fun playlistReorderIsDisabledUntilEveryMembershipPageIsLoaded() {
        val reorders = mutableListOf<List<String>>()
        compose.setContent {
            PandaWaveTheme(darkTheme = true) {
                LibraryRoute(
                    state = LibraryState(
                        selectedTab = LibraryTab.PLAYLISTS,
                        playlists = listOf(LibraryPlaylist("playlist-1", "Mix", null, 7)),
                        selectedPlaylistId = "playlist-1",
                        playlistTracks = listOf(
                            track("media-1", "First", "member-1"),
                            track("media-2", "Second", "member-2")
                        ),
                        hasPlaylistTracksNextPage = true,
                        isLoading = false
                    ),
                    onSelectTab = {},
                    onRefresh = {},
                    onLoadNext = {},
                    onSave = {},
                    onRemoveSaved = {},
                    onLike = {},
                    onUnlike = {},
                    onReorderPlaylist = { _, ids, _ -> reorders += ids }
                )
            }
        }

        compose.onNodeWithTag("library-playlist-track-member-1")
            .performScrollTo()
            .performTouchInput { swipeDown() }

        assertEquals(emptyList<List<String>>(), reorders)
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
                    onUnlike = {}
                )
            }
        }
    }

    private fun track(mediaId: String, title: String, relationshipId: String = mediaId) = LibraryTrack(
        relationshipId = relationshipId,
        mediaId = mediaId,
        title = title,
        artist = "Artist",
        album = null,
        durationMillis = 120_000,
        explicit = false,
        artworkId = null,
        relationshipAtEpochMillis = 1_000
    )
}
