package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
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
                4L
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
    }

    @Test
    fun `short native snapshots are rejected`() {
        val result = runCatching {
            PandaEngineNativeSnapshotMapper.toEngineSnapshot(longArrayOf(PLAYBACK_IDLE.toLong()))
        }

        assertTrue(result.isFailure)
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
