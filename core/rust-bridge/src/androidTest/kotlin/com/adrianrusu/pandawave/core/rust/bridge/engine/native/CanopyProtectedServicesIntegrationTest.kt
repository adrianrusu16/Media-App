package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.secure.storage.keystore.AndroidKeystoreSecureSecretProtector
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in only: requires an operator-managed backend and runtime-supplied throwaway credentials. */
@RunWith(AndroidJUnit4::class)
class CanopyProtectedServicesIntegrationTest {
    @Test(timeout = 90_000L)
    fun protectedAccountAndDeviceSessionSequence() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Pass canopyProtected=true with canopyProtectedEmail and canopyProtectedPassword",
            arguments.getString("canopyProtected").toBoolean()
        )
        val email = requireRuntimeArgument("canopyProtectedEmail")
        val password = requireRuntimeArgument("canopyProtectedPassword")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testDirectory = File(context.noBackupFilesDir, "canopy-protected-services")
        check(testDirectory.parentFile?.canonicalFile == context.noBackupFilesDir.canonicalFile)
        testDirectory.deleteRecursively()

        try {
            val protector = AndroidKeystoreSecureSecretProtector()
            PandaEngine.create(File(testDirectory, "primary.bin"), protector).use { primary ->
                PandaEngine.create(File(testDirectory, "secondary.bin"), protector).use { secondary ->
                    primary.configureBackend(connectionConfig(), isDevelopment = true)
                    secondary.configureBackend(connectionConfig(), isDevelopment = true)
                    login(primary, email, password, "Panda protected primary")
                    login(secondary, email, password, "Panda protected secondary")

                    val displayName = "Panda protected ${System.currentTimeMillis()}"
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_UPSERT_PROFILE,
                        EngineCommandPayloads.upsertProfile(displayName)
                    )
                    val profile = dispatchSuccess(primary, EngineCommand.TYPE_GET_PROFILE)
                    assertEquals(displayName, profile.profile?.displayName)
                    val updatedDisplayName = "$displayName updated"
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_UPDATE_PROFILE,
                        EngineCommandPayloads.updateProfileDisplayName(updatedDisplayName)
                    )
                    assertEquals(
                        updatedDisplayName,
                        dispatchSuccess(primary, EngineCommand.TYPE_GET_PROFILE).profile?.displayName
                    )
                    val futurePreferenceKey = "acceptance_future_${System.currentTimeMillis()}"
                    val futurePreferenceValue = "preserve-me"
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_UPDATE_PROFILE_PREFERENCES,
                        EngineCommandPayloads.updateProfilePreferences(
                            mapOf(futurePreferenceKey to futurePreferenceValue)
                        )
                    )
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_UPDATE_PROFILE_PREFERENCES,
                        EngineCommandPayloads.updateProfileTheme("forest_tech_dark")
                    )
                    val preferences = dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_LOAD_PROFILE_PREFERENCES
                    )
                    assertEquals("forest_tech_dark", preferences.themePreference.themeId)
                    assertEquals(futurePreferenceValue, primary.profilePreferenceValue(futurePreferenceKey))

                    var discovery = dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_LOAD_DISCOVERY_FEED,
                        EngineCommandPayloads.discoveryFeed(pageSize = 1)
                    )
                    assertTrue(discovery.discoveryResultsCount > 0)
                    val firstDiscoveryId = requireNotNull(primary.discoveryResult(0)).mediaId
                    assertTrue("Discovery page size 1 must expose a continuation", discovery.hasDiscoveryNextPage)
                    discovery = dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_LOAD_NEXT_DISCOVERY_PAGE,
                        EngineCommandPayloads.loadNextDiscoveryPage()
                    )
                    assertTrue(discovery.discoveryResultsCount > 1)
                    val trackId = firstDiscoveryId
                    val secondTrackId = requireNotNull(primary.discoveryResult(1)).mediaId

                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_UPDATE_HISTORY_SETTINGS,
                        EngineCommandPayloads.historyEnabled(true)
                    )
                    val history = dispatchSuccess(primary, EngineCommand.TYPE_LOAD_HISTORY_SETTINGS)
                    assertTrue(history.hasHistorySettings)
                    assertTrue(history.historyEnabled)
                    primary.dispatchPlatformEvent(
                        com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent(
                            com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent.TYPE_PLAYBACK_COMPLETED,
                            EngineCommandPayloads.playbackCompleted(trackId, 60_000L, 1.0)
                        )
                    ).snapshot.also { assertFalse(it.hasError) }
                    primary.dispatchPlatformEvent(
                        com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent(
                            com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent.TYPE_PLAYBACK_COMPLETED,
                            EngineCommandPayloads.playbackCompleted(secondTrackId, 45_000L, 1.0)
                        )
                    ).snapshot.also { assertFalse(it.hasError) }
                    var historyEntries = dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_LIST_HISTORY,
                        EngineCommandPayloads.historyPage(1)
                    )
                    assertEquals(1, historyEntries.historyEntriesCount)
                    assertTrue(
                        "Two seeded history events must expose a continuation",
                        historyEntries.hasHistoryNextPage
                    )
                    val firstHistoryPageCount = historyEntries.historyEntriesCount
                    historyEntries = dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE
                    )
                    assertEquals(firstHistoryPageCount, historyEntries.historyEntriesCount)
                    assertNotNull(primary.historyEntry(0))
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_UPDATE_HISTORY_SETTINGS,
                        EngineCommandPayloads.historyEnabled(false)
                    )
                    historyEntries = dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_LIST_HISTORY,
                        EngineCommandPayloads.historyPage(10)
                    )
                    assertEquals(0, historyEntries.historyEntriesCount)

                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_SAVE_TRACK,
                        EngineCommandPayloads.libraryTrack(trackId)
                    )
                    val saved = dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_LIST_SAVED_TRACKS,
                        EngineCommandPayloads.libraryPage(1)
                    )
                    assertTrue(saved.savedTracksCount > 0)
                    assertEquals(trackId, primary.savedTrack(0)?.mediaId)
                    if (saved.hasSavedTracksNextPage) {
                        dispatchSuccess(primary, EngineCommand.TYPE_LOAD_NEXT_SAVED_TRACKS_PAGE)
                    }
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_REMOVE_SAVED_TRACK,
                        EngineCommandPayloads.libraryTrack(trackId)
                    )

                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_LIKE_TRACK,
                        EngineCommandPayloads.libraryTrack(trackId)
                    )
                    val liked = dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_LIST_LIKED_TRACKS,
                        EngineCommandPayloads.libraryPage(1)
                    )
                    assertTrue(liked.likedTracksCount > 0)
                    assertEquals(trackId, primary.likedTrack(0)?.mediaId)
                    if (liked.hasLikedTracksNextPage) {
                        dispatchSuccess(primary, EngineCommand.TYPE_LOAD_NEXT_LIKED_TRACKS_PAGE)
                    }
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_UNLIKE_TRACK,
                        EngineCommandPayloads.libraryTrack(trackId)
                    )

                    val playlistName = "Protected ${System.currentTimeMillis()}"
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_CREATE_PLAYLIST,
                        EngineCommandPayloads.playlistDetails(null, playlistName, "acceptance")
                    )
                    var playlists = dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_LIST_PLAYLISTS,
                        EngineCommandPayloads.playlistPage(50)
                    )
                    if (playlists.hasPlaylistsNextPage) {
                        playlists = dispatchSuccess(primary, EngineCommand.TYPE_LOAD_NEXT_PLAYLISTS_PAGE)
                    }
                    val playlist = (0 until playlists.playlistsCount)
                        .mapNotNull(primary::playlist)
                        .first { item -> item.name == playlistName }
                    val updatedPlaylistName = "$playlistName updated"
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_UPDATE_PLAYLIST,
                        EngineCommandPayloads.playlistDetails(
                            playlist.id,
                            updatedPlaylistName,
                            "acceptance updated",
                            playlist.revision
                        )
                    )
                    val targetedPlaylist = dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_LIST_PLAYLISTS,
                        EngineCommandPayloads.playlistPage(1, playlist.id)
                    )
                    val updatedPlaylist = (0 until targetedPlaylist.playlistsCount)
                        .mapNotNull(primary::playlist)
                        .first { item -> item.id == playlist.id }
                    assertEquals(updatedPlaylistName, updatedPlaylist.name)
                    playlists = listPlaylistsUntil(primary) { item -> item.id == playlist.id }
                    assertTrue(
                        (0 until playlists.playlistsCount)
                            .mapNotNull(primary::playlist)
                            .any { item -> item.id == playlist.id }
                    )
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_ADD_PLAYLIST_TRACK,
                        EngineCommandPayloads.playlistTrack(playlist.id, trackId)
                    )
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_ADD_PLAYLIST_TRACK,
                        EngineCommandPayloads.playlistTrack(playlist.id, secondTrackId)
                    )
                    val tracks = dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_LIST_PLAYLIST_TRACKS,
                        EngineCommandPayloads.playlistPage(50, playlist.id)
                    )
                    assertTrue(tracks.playlistTracksCount > 0)
                    val memberships = (0 until tracks.playlistTracksCount)
                        .mapNotNull(primary::playlistTrack)
                        .map { item -> item.membershipId }
                    assertTrue(memberships.size >= 2)
                    val refreshedPlaylists = dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_LIST_PLAYLISTS,
                        EngineCommandPayloads.playlistPage(50)
                    )
                    val revision = (0 until refreshedPlaylists.playlistsCount)
                        .mapNotNull(primary::playlist)
                        .first { item -> item.id == playlist.id }
                        .revision
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_REORDER_PLAYLIST_TRACKS,
                        EngineCommandPayloads.playlistReorder(playlist.id, memberships.reversed(), revision)
                    )
                    val conflict = primary.dispatch(
                        EngineCommand(
                            EngineCommand.TYPE_REORDER_PLAYLIST_TRACKS,
                            EngineCommandPayloads.playlistReorder(
                                playlist.id,
                                memberships.reversed(),
                                Long.MAX_VALUE
                            )
                        )
                    ).snapshot
                    assertTrue(conflict.hasError)
                    assertTrue(conflict.hasPlaylistReconciliation)
                    assertNotNull(primary.playlistReconciliation())
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_REMOVE_PLAYLIST_TRACK,
                        EngineCommandPayloads.playlistTrack(playlist.id, trackId)
                    )
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_REMOVE_PLAYLIST_TRACK,
                        EngineCommandPayloads.playlistTrack(playlist.id, secondTrackId)
                    )
                    dispatchSuccess(
                        primary,
                        EngineCommand.TYPE_DELETE_PLAYLIST,
                        EngineCommandPayloads.playlistId(playlist.id)
                    )

                    val account = primary.dispatch(EngineCommand(EngineCommand.TYPE_GET_ACCOUNT, null)).snapshot
                    assertFalse(account.hasError)
                    assertEquals(email, account.protectedAccount?.primaryEmail)

                    val sessions = listAllSessions(primary)

                    val revocable = sessions.deviceSessions.firstOrNull { session -> !session.current }
                    assertNotNull("Two logins must expose a non-current session", revocable)
                    val revoked = primary.dispatch(
                        EngineCommand(
                            EngineCommand.TYPE_REVOKE_DEVICE_SESSION,
                            EngineCommandPayloads.revokeDeviceSession(requireNotNull(revocable).id)
                        )
                    ).snapshot
                    assertFalse(revoked.hasError)
                    assertTrue(revoked.deviceSessions.none { session -> session.id == revocable.id })

                    if (arguments.getString("canopyProtectedDelete").toBoolean()) {
                        val deleted = primary.dispatch(
                            EngineCommand(EngineCommand.TYPE_DELETE_ACCOUNT, null)
                        ).snapshot
                        assertFalse(deleted.hasError)
                        assertEquals(null, deleted.protectedAccount)
                        assertTrue(deleted.deviceSessions.isEmpty())
                    }
                }
            }
        } finally {
            if (testDirectory.exists()) testDirectory.deleteRecursively()
        }
    }

    private fun login(engine: PandaEngine, email: String, password: String, deviceLabel: String) {
        val secret = password.encodeToByteArray()
        val result = try {
            engine.loginPassword(email, secret, deviceLabel)
        } finally {
            secret.fill(0)
        }
        assertTrue(secret.all { byte -> byte == 0.toByte() })
        assertEquals(EngineAuthOperationResult.STATUS_AUTHENTICATED, result.status)
    }

    private fun dispatchSuccess(engine: PandaEngine, type: String, payload: String? = null): EngineSnapshot {
        val snapshot = engine.dispatch(EngineCommand(type, payload)).snapshot
        assertFalse("$type failed with ${snapshot.errorType}", snapshot.hasError)
        return snapshot
    }

    private fun listPlaylistsUntil(
        engine: PandaEngine,
        predicate: (com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem) -> Boolean
    ): EngineSnapshot {
        var snapshot = dispatchSuccess(
            engine,
            EngineCommand.TYPE_LIST_PLAYLISTS,
            EngineCommandPayloads.playlistPage(10)
        )
        repeat(20) {
            if ((0 until snapshot.playlistsCount).mapNotNull(engine::playlist).any(predicate)) return snapshot
            if (!snapshot.hasPlaylistsNextPage) return snapshot
            snapshot = dispatchSuccess(engine, EngineCommand.TYPE_LOAD_NEXT_PLAYLISTS_PAGE)
        }
        return snapshot
    }

    private fun listAllSessions(engine: PandaEngine): EngineSnapshot {
        var snapshot = dispatchSuccess(
            engine,
            EngineCommand.TYPE_LIST_DEVICE_SESSIONS,
            EngineCommandPayloads.deviceSessionsPage(1)
        )
        assertEquals(1, snapshot.deviceSessionsCount)
        assertTrue("Two authenticated devices must expose a session continuation", snapshot.hasDeviceSessionsNextPage)
        val firstPageCount = snapshot.deviceSessionsCount
        var continuationRequests = 0
        while (snapshot.hasDeviceSessionsNextPage && continuationRequests < 20) {
            snapshot = dispatchSuccess(engine, EngineCommand.TYPE_LOAD_NEXT_DEVICE_SESSIONS_PAGE)
            continuationRequests += 1
        }
        assertTrue("Acceptance must execute at least one session continuation", continuationRequests >= 1)
        assertTrue(snapshot.deviceSessionsCount > firstPageCount)
        assertFalse(
            "Session pagination must complete within 20 continuation requests",
            snapshot.hasDeviceSessionsNextPage
        )
        return snapshot
    }

    private fun requireRuntimeArgument(name: String): String =
        requireNotNull(InstrumentationRegistry.getArguments().getString(name)).takeIf(String::isNotBlank)
            ?: error("Missing runtime instrumentation argument: $name")

    private fun connectionConfig(): String = InstrumentationRegistry.getInstrumentation()
        .context.assets.open("client-connection.json")
        .bufferedReader().use { reader -> reader.readText() }
}
