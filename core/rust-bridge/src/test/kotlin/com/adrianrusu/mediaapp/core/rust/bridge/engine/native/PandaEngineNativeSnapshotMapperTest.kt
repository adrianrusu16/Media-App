package com.adrianrusu.mediaapp.core.rust.bridge.engine.native

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
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
                7L
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
    }

    @Test
    fun `short native snapshots are rejected`() {
        val result = runCatching {
            PandaEngineNativeSnapshotMapper.toEngineSnapshot(longArrayOf(PLAYBACK_IDLE.toLong()))
        }

        assertTrue(result.isFailure)
    }

    private fun Boolean.toLong(): Long = if (this) 1L else 0L

    private companion object {
        const val PLAYBACK_IDLE = 0
        const val PLAYBACK_PLAYING = 1
        const val RESTRICTION_UNKNOWN = 0
        const val ERROR_NETWORK = 2
    }
}
