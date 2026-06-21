package com.adrianrusu.mediaapp.core.playback

import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineControlState
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlayerControls
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
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
            album = "Canopy Sessions",
            durationMillis = 222_000L,
            artworkUri = "content://pandawave/art/track-1",
            sourceUri = "https://cdn.pandawave.test/audio/track-1.mp3",
            mimeType = "audio/mpeg",
            userId = null,
            restrictionState = EngineSnapshot.RESTRICTION_UNKNOWN,
            updatedAtEpochMillis = 100L,
            hasActiveSession = true,
            searchResultsCount = 2,
            playbackSpeed = 1.25F,
            positionMillis = 9_000L,
            isBusy = true,
            canDispatch = false,
            controls = EnginePlayerControls(
                playPause = EngineControlState(
                    isVisible = true,
                    isEnabled = true,
                    isActive = true
                ),
                skipNext = EngineControlState(
                    isVisible = true,
                    isEnabled = false,
                    isActive = false
                ),
                skipPrevious = EngineControlState(
                    isVisible = false,
                    isEnabled = false,
                    isActive = false
                ),
                showPlayIcon = false
            ),
            browseResultsCount = 3
        )

        val state = BambooPlaybackStateProjector.fromEngineSnapshot(
            current = BambooPlaybackState(engineConnection = BambooEngineConnectionUiState.Ready),
            snapshot = snapshot
        )

        assertEquals("track-1", state.mediaId)
        assertEquals("Quiet Cabin", state.title)
        assertEquals("PandaWave", state.artist)
        assertEquals("Canopy Sessions", state.album)
        assertEquals(222_000L, state.durationMillis)
        assertEquals("content://pandawave/art/track-1", state.artworkUri)
        assertEquals("https://cdn.pandawave.test/audio/track-1.mp3", state.sourceUri)
        assertEquals("audio/mpeg", state.mimeType)
        assertEquals(BambooPlaybackStatus.Playing, state.playbackStatus)
        assertEquals(100L, state.updatedAtEpochMillis)
        assertEquals(true, state.hasActiveSession)
        assertEquals(2, state.searchResultsCount)
        assertEquals(1.25F, state.playbackSpeed)
        assertEquals(9_000L, state.positionMillis)
        assertEquals(true, state.isBusy)
        assertEquals(false, state.canDispatch)
        assertEquals(false, state.canDispatchEngineCommands)
        assertEquals(true, state.controls.playPause.isVisible)
        assertEquals(true, state.controls.playPause.isEnabled)
        assertEquals(true, state.controls.playPause.isActive)
        assertEquals(true, state.controls.skipNext.isVisible)
        assertEquals(false, state.controls.skipNext.isEnabled)
        assertEquals(false, state.controls.skipPrevious.isVisible)
        assertEquals(false, state.controls.showPlayIcon)
        assertEquals(3, state.browseResultsCount)
    }

    @Test
    fun `engine snapshot keeps missing metadata empty for presentation localization`() {
        val snapshot = EngineSnapshot.idle(nowMillis = 1L).copy(
            playbackState = EngineSnapshot.PLAYBACK_PAUSED
        )

        val state = BambooPlaybackStateProjector.fromEngineSnapshot(
            current = BambooPlaybackState(),
            snapshot = snapshot
        )

        assertEquals("", state.title)
        assertEquals("", state.artist)
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

        assertEquals(true, state.restriction.isRestricted)
    }
}
