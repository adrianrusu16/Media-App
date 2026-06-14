package com.adrianrusu.mediaapp.core.playback

import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText
import kotlin.test.Test
import kotlin.test.assertEquals

class BambooPlaybackStateProjectorTest {
    @Test
    fun `engine snapshot projects now playing metadata`() {
        val snapshot = EngineSnapshot(
            playbackState = EngineSnapshot.PLAYBACK_PLAYING,
            mediaId = "track-1",
            title = "Quiet Cabin",
            artist = "PandaWave",
            userId = null,
            restrictionState = EngineSnapshot.RESTRICTION_UNKNOWN,
            updatedAtEpochMillis = 100L
        )

        val state = BambooPlaybackStateProjector.fromEngineSnapshot(
            current = BambooPlaybackState(),
            snapshot = snapshot
        )

        assertEquals("track-1", state.mediaId)
        assertEquals("Quiet Cabin", state.title)
        assertEquals("PandaWave", state.artist)
        assertEquals(BambooPlaybackStatus.Playing, state.playbackStatus)
        assertEquals(100L, state.updatedAtEpochMillis)
    }

    @Test
    fun `engine snapshot uses playback fallbacks when metadata is missing`() {
        val snapshot = EngineSnapshot.idle(nowMillis = 1L).copy(
            playbackState = EngineSnapshot.PLAYBACK_PAUSED
        )

        val state = BambooPlaybackStateProjector.fromEngineSnapshot(
            current = BambooPlaybackState(),
            snapshot = snapshot
        )

        assertEquals(BambooPlaybackText.FALLBACK_PAUSED_TITLE, state.title)
        assertEquals(BambooPlaybackText.FALLBACK_PAUSED_SUBTITLE, state.artist)
        assertEquals(BambooPlaybackStatus.Paused, state.playbackStatus)
    }

    @Test
    fun `engine events project connection readiness`() {
        val queuedState = BambooPlaybackStateProjector.fromEngineEvent(
            current = BambooPlaybackState(engineConnection = BambooEngineConnectionUiState.Ready),
            event = EngineEvent(
                type = EngineEvent.TYPE_PLATFORM_EVENT_QUEUED,
                message = null
            )
        )
        val readyState = BambooPlaybackStateProjector.fromEngineEvent(
            current = queuedState,
            event = EngineEvent(
                type = EngineEvent.TYPE_PLATFORM_EVENT_APPLIED,
                message = null
            )
        )

        assertEquals(BambooEngineConnectionUiState.Connecting, queuedState.engineConnection)
        assertEquals(BambooEngineConnectionUiState.Ready, readyState.engineConnection)
    }

    @Test
    fun `ux restrictions project driver safe state`() {
        val state = BambooPlaybackStateProjector.fromUxRestrictions(
            current = BambooPlaybackState(),
            restrictions = AutomotiveUxRestrictions(
                source = AutomotiveUxRestrictions.Source.AutomotivePlatform,
                requiresDistractionOptimization = true,
                activeRestrictions = AutomotiveUxRestrictions.NO_RESTRICTIONS,
                maxContentDepth = 1,
                maxCumulativeContentItems = 1,
                maxRestrictedStringLength = 24
            )
        )

        assertEquals("Driver-safe mode", state.restriction.label)
        assertEquals(true, state.restriction.isRestricted)
    }
}
