package com.adrianrusu.mediaapp.feature.nowplaying

import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingPlaybackState
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingRestrictionState
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NowPlayingUiModelTest {
    @Test
    fun `drive restriction does not disable media controls`() {
        val model = NowPlayingState(
            playbackState = NowPlayingPlaybackState.Paused,
            engineConnection = BambooEngineConnectionUiState.Ready,
            restriction = NowPlayingRestrictionState(
                label = "Driver-safe mode",
                isRestricted = true
            )
        ).toNowPlayingUiModel(volume = 45F)

        assertTrue(model.controlsEnabled)
        assertTrue(model.isDriveRestricted)
    }

    @Test
    fun `play button uses panda paw icon when paused`() {
        val model = NowPlayingState(
            playbackState = NowPlayingPlaybackState.Paused,
            engineConnection = BambooEngineConnectionUiState.Ready
        ).toNowPlayingUiModel(volume = 45F)

        assertEquals(NowPlayingPrimaryControlIcon.PandaPaw, model.primaryControlIcon)
    }

    @Test
    fun `play button uses pause icon when playing`() {
        val model = NowPlayingState(
            playbackState = NowPlayingPlaybackState.Playing,
            engineConnection = BambooEngineConnectionUiState.Ready
        ).toNowPlayingUiModel(volume = 45F)

        assertEquals(NowPlayingPrimaryControlIcon.Pause, model.primaryControlIcon)
    }

    @Test
    fun `engine unavailable disables media controls without exposing engine copy`() {
        val model = NowPlayingState(
            playbackState = NowPlayingPlaybackState.Paused,
            engineConnection = BambooEngineConnectionUiState.Connecting
        ).toNowPlayingUiModel(volume = 45F)

        assertFalse(model.controlsEnabled)
        assertEquals("Controls unavailable", model.availabilityLabel)
    }

    @Test
    fun `volume is clamped to zero to one hundred`() {
        assertEquals(0F, NowPlayingVolumeUiModel.from(-20F).value)
        assertEquals(100F, NowPlayingVolumeUiModel.from(120F).value)
    }
}
