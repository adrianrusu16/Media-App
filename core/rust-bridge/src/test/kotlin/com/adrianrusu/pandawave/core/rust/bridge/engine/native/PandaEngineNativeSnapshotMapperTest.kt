package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthSession
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineBackendAvailability
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PandaEngineNativeSnapshotMapperTest {
    @Test
    fun `native values map to rich engine snapshot`() {
        val projection = PandaEngineNativeSnapshotMapper.toProjection(
            longArrayOf(
                PLAYBACK_PLAYING.toLong(),
                RESTRICTION_UNKNOWN.toLong(),
                42L,
                true.toLong(),
                true.toLong(),
                ERROR_NETWORK.toLong(),
                3L,
                1.25F.toBits().toLong(),
                9_000L,
                true.toLong(),
                false.toLong(),
                true.toLong(),
                true.toLong(),
                true.toLong(),
                true.toLong(),
                false.toLong(),
                false.toLong(),
                false.toLong(),
                true.toLong(),
                false.toLong(),
                false.toLong(),
                true.toLong(),
                5L,
                7L,
                222_000L,
                THEME_FOREST_TECH_DARK.toLong(),
                PREFERENCE_SOURCE_REMOTE_PROFILE.toLong(),
                8L,
                true.toLong(),
                DRIVING_PARKED.toLong(),
                true.toLong(),
                true.toLong(),
                1_725_000_000_000L,
                2L,
                1_750_000_000_250L,
                AUTH_AUTHENTICATED.toLong(),
                true.toLong(),
                true.toLong(),
                3L,
                4L,
                3L,
                4L,
                1L,
                1L,
                0L,
                2L,
                3L,
                1L,
                0L,
                1L,
                1L,
                2L,
                1L,
                6L,
                1L,
                1L,
                8L,
                9L,
                2L,
                3L,
                11L,
                77L,
                12L,
                4L,
                3L
            )
        )
        val snapshot = projection.snapshot

        assertEquals(EngineSnapshot.PLAYBACK_PLAYING, snapshot.playbackState)
        assertEquals(EngineSnapshot.RESTRICTION_UNKNOWN, snapshot.restrictionState)
        assertEquals(42L, snapshot.updatedAtEpochMillis)
        assertTrue(snapshot.hasActiveSession)
        assertTrue(snapshot.hasError)
        assertEquals(EngineSnapshot.ERROR_NETWORK, snapshot.errorType)
        assertEquals(3, snapshot.searchResultsCount)
        assertEquals(1.25F, snapshot.playbackSpeed)
        assertEquals(9_000L, snapshot.positionMillis)
        assertTrue(snapshot.isBusy)
        assertFalse(snapshot.canDispatch)
        assertTrue(snapshot.controls.playPause.isVisible)
        assertTrue(snapshot.controls.playPause.isEnabled)
        assertTrue(snapshot.controls.playPause.isActive)
        assertTrue(snapshot.controls.skipNext.isVisible)
        assertFalse(snapshot.controls.skipNext.isEnabled)
        assertFalse(snapshot.controls.skipNext.isActive)
        assertFalse(snapshot.controls.skipPrevious.isVisible)
        assertTrue(snapshot.controls.skipPrevious.isEnabled)
        assertFalse(snapshot.controls.skipPrevious.isActive)
        assertFalse(snapshot.controls.showPlayIcon)
        assertTrue(snapshot.hasVoiceHypothesis)
        assertEquals(5, snapshot.browseResultsCount)
        assertEquals(7L, projection.metadataRevision)
        assertEquals(222_000L, snapshot.durationMillis)
        assertEquals(1_750_000_000_250L, snapshot.playbackExpiresAtEpochMillis)
        assertEquals("forest_tech_dark", snapshot.themePreference.themeId)
        assertEquals("remote_profile", snapshot.themePreference.source)
        assertEquals(8L, snapshot.themePreference.revision)
        assertTrue(snapshot.themePreference.initialized)
        assertEquals(EngineSnapshot.DRIVING_PARKED, snapshot.drivingState)
        assertEquals(
            NativeBackendStatusProjection(
                healthy = true,
                checkedAtEpochMillis = 1_725_000_000_000L,
                dependencyCount = 2
            ),
            projection.backendStatus
        )
        assertEquals(EngineAuthState.AUTHENTICATED, snapshot.authState.state)
        assertTrue(snapshot.hasHistorySettings)
        assertTrue(snapshot.historyEnabled)
        assertEquals(3L, snapshot.historyDeletedCount)
        assertEquals(4, snapshot.historyEntriesCount)
        assertEquals(3, snapshot.savedTracksCount)
        assertEquals(4, snapshot.likedTracksCount)
        assertEquals(1, snapshot.libraryPendingCount)
        assertTrue(snapshot.hasSavedTracksNextPage)
        assertFalse(snapshot.hasLikedTracksNextPage)
        assertEquals(2, snapshot.playlistsCount)
        assertEquals(3, snapshot.playlistTracksCount)
        assertTrue(snapshot.hasPlaylistsNextPage)
        assertFalse(snapshot.hasPlaylistTracksNextPage)
        assertTrue(snapshot.hasPlaylistReconciliation)
        assertEquals(null, snapshot.protectedAccount)
        assertEquals(2, snapshot.deviceSessionsCount)
        assertTrue(snapshot.hasDeviceSessionsNextPage)
        assertEquals(6, snapshot.discoveryResultsCount)
        assertTrue(snapshot.hasDiscoveryNextPage)
        assertTrue(snapshot.hasHistoryNextPage)
        assertEquals(8, snapshot.forYouResultsCount)
        assertEquals(9, snapshot.recommendationsResultsCount)
        assertEquals(EngineBackendAvailability.UNAVAILABLE, snapshot.backendAvailability.status)
        assertEquals(EngineBackendAvailability.REASON_TIMEOUT, snapshot.backendAvailability.reason)
        assertEquals(11L, snapshot.historyGeneration)
        assertEquals(77L, snapshot.lastProgressTickEpochMillis)
        assertTrue(snapshot.queueAvailable)
        assertEquals(12, snapshot.queueSize)
        assertEquals(4, snapshot.queueCurrentIndex)
        assertEquals(3L, snapshot.queueGeneration)
    }

    @Test
    fun `short native snapshots are rejected`() {
        val result = runCatching {
            PandaEngineNativeSnapshotMapper.toEngineSnapshot(longArrayOf(PLAYBACK_IDLE.toLong()))
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun `available native backend projection is surfaced as available`() {
        val nativeValues = LongArray(62)
        nativeValues[58] = 1L

        val snapshot = PandaEngineNativeSnapshotMapper.toProjection(nativeValues).snapshot

        assertEquals(EngineBackendAvailability.AVAILABLE, snapshot.backendAvailability.status)
        assertEquals(null, snapshot.backendAvailability.reason)
    }

    @Test
    fun `network outage native backend projection preserves its reason`() {
        val nativeValues = LongArray(62)
        nativeValues[58] = 2L
        nativeValues[59] = 1L

        val snapshot = PandaEngineNativeSnapshotMapper.toProjection(nativeValues).snapshot

        assertEquals(EngineBackendAvailability.UNAVAILABLE, snapshot.backendAvailability.status)
        assertEquals(
            EngineBackendAvailability.REASON_NETWORK_UNAVAILABLE,
            snapshot.backendAvailability.reason
        )
    }

    @Test
    fun `backend status values map atomically to domain status`() {
        val status = PandaEngineNativeBackendStatusMapper.toDomain(
            arrayOf(
                "1",
                "0.2.0",
                "ready",
                "1750000000250",
                "1",
                "catalog",
                "healthy",
                "available"
            )
        )

        assertTrue(status.healthy)
        assertEquals("0.2.0", status.version)
        assertEquals("ready", status.status)
        assertEquals(1_750_000_000_250L, status.checkedAtEpochMillis)
        assertEquals("catalog", status.dependencies.single().name)
        assertEquals("healthy", status.dependencies.single().status)
        assertEquals("available", status.dependencies.single().message)
    }

    @Test
    fun `sanitized authenticated values map to generic account and session`() {
        val authState = PandaEngineNativeAuthStateMapper.toDomain(
            arrayOf(
                "authenticated", "account-1", "driver@example.com", "active", "10",
                "session-1", "PandaEmulatorNoStore", "20", "30", "40", "1"
            )
        )

        assertEquals(EngineAuthState.AUTHENTICATED, authState.state)
        assertEquals("driver@example.com", authState.account?.primaryEmail)
        assertEquals("PandaEmulatorNoStore", authState.session?.deviceLabel)
        assertEquals(40L, authState.session?.expiresAtEpochMillis)
        assertTrue(authState.session?.current == true)
    }

    @Test
    fun `auth projection fails closed when one atomic sample is malformed`() {
        val missingSession = PandaEngineNativeAuthStateMapper.toDomain(
            arrayOf("authenticated", "account-1", "driver@example.com", "active", "10")
        )
        val contradictoryAnonymous = PandaEngineNativeAuthStateMapper.toDomain(
            arrayOf("anonymous", "unexpected-second-sample-data")
        )

        assertEquals(EngineAuthState.LOGIN_REQUIRED, missingSession.state)
        assertEquals(EngineAuthState.LOGIN_REQUIRED, contradictoryAnonymous.state)
        assertEquals(null, missingSession.account)
        assertEquals(null, missingSession.session)
    }

    @Test
    fun `saved and liked item payloads round trip without credentials`() {
        val saved = PandaEngineNativeLibraryItemMapper.toDomain(
            arrayOf(
                "saved-1", "track-1", "Saved", "artist-1", "Artist", "Album", "120000", "1",
                "https://example.com/art-1", "1000", "art-1", "hash-1"
            )
        )
        val liked = PandaEngineNativeLibraryItemMapper.toDomain(
            arrayOf(
                "liked-1", "track-2", "Liked", "artist-2", "Other", "", "240000", "0",
                "", "2000", "", ""
            )
        )

        assertEquals("saved-1", saved?.relationshipId)
        assertEquals("track-1", saved?.mediaId)
        assertEquals("Saved", saved?.title)
        assertEquals("Album", saved?.album)
        assertEquals(120_000L, saved?.durationMillis)
        assertTrue(saved?.explicit == true)
        assertEquals("https://example.com/art-1", saved?.artworkUri)
        assertEquals("art-1", saved?.artworkId)
        assertEquals("hash-1", saved?.artworkVersion)
        assertEquals("liked-1", liked?.relationshipId)
        assertEquals("track-2", liked?.mediaId)
        assertEquals(null, liked?.album)
        assertFalse(liked?.explicit == true)
        assertEquals(null, liked?.artworkId)
        assertEquals(null, liked?.artworkUri)
        assertEquals(null, liked?.artworkVersion)

        val publicSurface = com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem::class.java
            .declaredFields.joinToString(" ") { it.name }.lowercase()
        assertFalse(publicSurface.contains("token"))
        assertFalse(publicSurface.contains("credential"))
    }

    @Test
    fun `history item payloads expose bounded service neutral rows`() {
        val item = PandaEngineNativeHistoryItemMapper.toDomain(
            arrayOf(
                "history-1", "track-1", "Played Track", "Artist", "Album", "art-1",
                "1234", "90000", "0.75", "1", "art-id-1", "hash-1"
            )
        )
        val unavailable = PandaEngineNativeHistoryItemMapper.toDomain(
            arrayOf(
                "history-2", "", "Unavailable track", "", "", "",
                "", "1000", "1", "0", "", ""
            )
        )

        assertEquals("history-1", item?.historyId)
        assertEquals("track-1", item?.mediaId)
        assertEquals("Played Track", item?.title)
        assertEquals("Artist", item?.artist)
        assertEquals("Album", item?.album)
        assertEquals("art-1", item?.artworkUri)
        assertEquals("art-id-1", item?.artworkId)
        assertEquals("hash-1", item?.artworkVersion)
        assertEquals(1_234L, item?.playedAtEpochMillis)
        assertEquals(90_000L, item?.listenedDurationMillis)
        assertEquals(0.75F, item?.completionRatio)
        assertTrue(item?.playable == true)
        assertEquals(null, unavailable?.mediaId)
        assertFalse(unavailable?.playable == true)

        val packedPage = PandaEngineNativeHistoryItemMapper.toPage(
            arrayOf(
                "4",
                "history-1", "track-1", "Played Track", "Artist", "Album", "art-1",
                "1234", "90000", "0.75", "1", "art-id-1", "hash-1",
                "history-2", "", "Unavailable track", "", "", "",
                "", "1000", "1", "0", "", ""
            ),
            requestedGeneration = 4L
        )
        assertEquals(4L, packedPage.generation)
        assertEquals(listOf("history-1", "history-2"), packedPage.items.map { it.historyId })
        assertEquals("track-1", packedPage.items[0].mediaId)
        assertFalse(packedPage.items[1].playable)

        val stalePage = PandaEngineNativeHistoryItemMapper.toPage(arrayOf("9"), requestedGeneration = 8L)
        assertEquals(9L, stalePage.generation)
        assertEquals(emptyList<EngineHistoryItem>(), stalePage.items)

        val malformed = PandaEngineNativeHistoryItemMapper.toPage(
            arrayOf("4", "history-1"),
            requestedGeneration = 4L
        )
        assertEquals(4L, malformed.generation)
        assertEquals(emptyList<EngineHistoryItem>(), malformed.items)

        val missing = PandaEngineNativeHistoryItemMapper.toPage(null, requestedGeneration = 3L)
        assertEquals(3L, missing.generation)
        assertEquals(emptyList<EngineHistoryItem>(), missing.items)

        val publicSurface = com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem::class.java
            .declaredFields.joinToString(" ") { it.name }.lowercase()
        assertFalse(publicSurface.contains("token"))
        assertFalse(publicSurface.contains("cursor"))
        assertFalse(publicSurface.contains("credential"))
    }

    @Test
    fun `catalog item payloads map optional fields and item type atomically`() {
        val item = PandaEngineNativeCatalogItemMapper.toDomain(
            arrayOf("track-1", "Catalog Track", "Artist", "", "", "canopy://track-1", "", "2", "", "")
        )

        assertEquals("track-1", item?.mediaId)
        assertEquals("Catalog Track", item?.title)
        assertEquals("Artist", item?.artist)
        assertEquals(null, item?.album)
        assertEquals(null, item?.artworkUri)
        assertEquals(null, item?.artworkId)
        assertEquals(null, item?.artworkVersion)
        assertEquals("canopy://track-1", item?.sourceUri)
        assertEquals(EngineCatalogItem.TYPE_ALBUM, item?.itemType)

        val page = PandaEngineNativeCatalogItemMapper.toPage(
            arrayOf(
                "track-1", "Catalog Track", "Artist", "", "", "canopy://track-1", "", "2", "", "",
                "track-2", "Second", "Other", "Album", "art", "canopy://track-2", "audio/mpeg", "0",
                "art-id-2", "hash-2"
            )
        )
        assertEquals(listOf("track-1", "track-2"), page.map { it.mediaId })
        assertEquals(EngineCatalogItem.TYPE_TRACK, page[1].itemType)
        assertEquals("art-id-2", page[1].artworkId)
        assertEquals("hash-2", page[1].artworkVersion)
        assertEquals(emptyList<EngineCatalogItem>(), PandaEngineNativeCatalogItemMapper.toPage(arrayOf("track-1")))
        assertEquals(emptyList<EngineCatalogItem>(), PandaEngineNativeCatalogItemMapper.toPage(null))
    }

    @Test
    fun `library item pages unpack repeating field groups`() {
        val page = PandaEngineNativeLibraryItemMapper.toPage(
            arrayOf(
                "rel-1", "track-1", "Saved", "artist-1", "Artist", "Album", "180000", "1",
                "art-1", "10", "art-id-1", "hash-1",
                "rel-2", "track-2", "Liked", "artist-2", "Other", "", "90000", "0",
                "", "20", "", ""
            )
        )
        assertEquals(listOf("rel-1", "rel-2"), page.map { it.relationshipId })
        assertEquals("Album", page[0].album)
        assertEquals(null, page[1].album)
        assertTrue(page[0].explicit)
        assertFalse(page[1].explicit)
        assertEquals("art-1", page[0].artworkUri)
        assertEquals("art-id-1", page[0].artworkId)
        assertEquals("hash-1", page[0].artworkVersion)
        assertEquals(emptyList<EngineLibraryItem>(), PandaEngineNativeLibraryItemMapper.toPage(arrayOf("rel-1")))
    }

    @Test
    fun `device session pages unpack repeating field groups`() {
        val sessions = PandaEngineNativeAuthStateMapper.toSessions(
            arrayOf(
                "session-1", "Car", "1", "2", "3", "1",
                "session-2", "Phone", "4", "5", "6", "0"
            )
        )
        assertEquals(listOf("session-1", "session-2"), sessions.map { it.id })
        assertTrue(sessions[0].current)
        assertFalse(sessions[1].current)
        assertEquals(emptyList<EngineAuthSession>(), PandaEngineNativeAuthStateMapper.toSessions(arrayOf("session-1")))
    }

    @Test
    fun `profile mapper preserves absent display name distinctly from empty text`() {
        val absent = PandaEngineNativeProfileMapper.toDomain(
            arrayOf("profile-1", "account-1", "0", "", "100", "")
        )
        val empty = PandaEngineNativeProfileMapper.toDomain(
            arrayOf("profile-1", "account-1", "1", "", "100", "200")
        )

        assertEquals(null, absent?.displayName)
        assertEquals("", empty?.displayName)
        assertEquals(100L, absent?.createdAtEpochMillis)
        assertEquals(null, absent?.updatedAtEpochMillis)
        assertEquals(200L, empty?.updatedAtEpochMillis)
    }

    @Test
    fun `malformed profile projection fails closed`() {
        assertEquals(
            null,
            PandaEngineNativeProfileMapper.toDomain(
                arrayOf("", "account-1", "0", "", "100", "200")
            )
        )
        assertEquals(null, PandaEngineNativeProfileMapper.toDomain(arrayOf("profile-1")))
    }

    private fun Boolean.toLong(): Long = if (this) 1L else 0L

    private companion object {
        const val PLAYBACK_IDLE = 0
        const val PLAYBACK_PLAYING = 1
        const val RESTRICTION_UNKNOWN = 0
        const val ERROR_NETWORK = 2
        const val THEME_FOREST_TECH_DARK = 4
        const val PREFERENCE_SOURCE_REMOTE_PROFILE = 3
        const val DRIVING_PARKED = 1
        const val AUTH_AUTHENTICATED = 1
    }
}
