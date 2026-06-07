package com.adrianrusu.mediaapp.feature.nowplaying.domain

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingReducerTest {
    @Test
    fun mapsPlayingEngineSnapshotToNowPlayingState() {
        val result = NowPlayingReducer.reduce(
            state = NowPlayingState(),
            snapshot = EngineSnapshot(
                playbackState = EngineSnapshot.PLAYBACK_PLAYING,
                mediaId = "station-1",
                title = "Night Drive",
                artist = "AAOS Radio",
                userId = "user-1",
                restrictionState = EngineSnapshot.RESTRICTION_UNKNOWN,
                updatedAtEpochMillis = 42L
            )
        )

        assertEquals("station-1", result.mediaId)
        assertEquals("Night Drive", result.title)
        assertEquals("AAOS Radio", result.artist)
        assertEquals(NowPlayingPlaybackState.Playing, result.playbackState)
        assertEquals(42L, result.updatedAtEpochMillis)
    }

    @Test
    fun preservesDriverSafeRestrictionWhenSnapshotChanges() {
        val restricted = NowPlayingState(
            restriction = NowPlayingRestrictionState(
                label = "Driver-safe mode",
                isRestricted = true
            )
        )

        val result = NowPlayingReducer.reduce(
            state = restricted,
            snapshot = EngineSnapshot.idle(nowMillis = 7L)
        )

        assertEquals(restricted.restriction, result.restriction)
        assertEquals("Driver-safe metadata", result.detailLabel)
    }
}
