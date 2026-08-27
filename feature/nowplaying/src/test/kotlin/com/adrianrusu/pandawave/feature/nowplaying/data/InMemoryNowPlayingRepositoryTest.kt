package com.adrianrusu.pandawave.feature.nowplaying.data

import com.adrianrusu.pandawave.core.playback.BambooDrivingState
import com.adrianrusu.pandawave.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRestrictionState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackStatus
import com.adrianrusu.pandawave.core.playback.BambooRestrictionState
import com.adrianrusu.pandawave.core.playback.BambooVehicleSafetyState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingIntent
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingPlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryNowPlayingRepositoryTest {
    @Test
    fun `start projects shared playback state into now playing state`() {
        val playback = RecordingPlaybackRepository(
            initialState = BambooPlaybackState(
                mediaId = "track-1",
                artworkUri = "content://pandawave/art/track-1",
                title = "Quiet Cabin",
                artist = "PandaWave",
                playbackStatus = BambooPlaybackStatus.Playing,
                engineConnection = BambooEngineConnectionUiState.Ready,
                restriction = BambooPlaybackRestrictionState(
                    isRestricted = true
                ),
                vehicleSafety = BambooVehicleSafetyState(
                    drivingState = BambooDrivingState.Parked,
                    restrictionState = BambooRestrictionState.Unrestricted
                ),
                updatedAtEpochMillis = 100L,
                positionMillis = 10_000L,
                durationMillis = 40_000L,
                playbackSpeed = 1.5F,
                volume = 0.72F,
                hasError = true
            )
        )
        val repository = InMemoryNowPlayingRepository(playbackRepository = playback)

        repository.start()

        assertEquals("track-1", repository.state.value.mediaId)
        assertEquals("content://pandawave/art/track-1", repository.state.value.artworkUri)
        assertEquals("Quiet Cabin", repository.state.value.title)
        assertEquals("PandaWave", repository.state.value.artist)
        assertEquals(NowPlayingPlaybackState.Playing, repository.state.value.playbackState)
        assertEquals(BambooEngineConnectionUiState.Ready, repository.state.value.engineConnection)
        assertTrue(repository.state.value.restriction.isRestricted)
        assertTrue(repository.state.value.isParked)
        assertTrue(repository.state.value.isUxUnrestricted)
        assertTrue(repository.state.value.hasPlaybackError)
        assertEquals(0.72F, repository.state.value.volume)
        assertEquals(13_000L, repository.state.value.progressAt(nowMillis = 2_100L).positionMillis)
        assertEquals(0.325F, repository.state.value.progressAt(nowMillis = 2_100L).fraction)
    }

    @Test
    fun `ended playback keeps now playing metadata`() {
        val playback = RecordingPlaybackRepository(
            initialState = BambooPlaybackState(
                mediaId = "track-1",
                title = "The Emptiness Machine",
                artist = "Linkin Park",
                playbackStatus = BambooPlaybackStatus.Ended,
                engineConnection = BambooEngineConnectionUiState.Ready,
                durationMillis = 200_000L,
                positionMillis = 200_000L
            )
        )
        val repository = InMemoryNowPlayingRepository(playbackRepository = playback)

        repository.start()

        assertEquals("The Emptiness Machine", repository.state.value.title)
        assertEquals("Linkin Park", repository.state.value.artist)
        assertEquals(NowPlayingPlaybackState.Ended, repository.state.value.playbackState)
    }

    @Test
    fun `now playing intents forward to shared playback repository`() {
        val playback = RecordingPlaybackRepository()
        val repository = InMemoryNowPlayingRepository(playbackRepository = playback)

        repository.start()
        repository.dispatch(NowPlayingIntent.Refresh)
        repository.dispatch(NowPlayingIntent.TogglePlayback)
        repository.dispatch(NowPlayingIntent.SkipPrevious)
        repository.dispatch(NowPlayingIntent.SkipNext)
        repository.dispatch(NowPlayingIntent.SetVolume(0.25F))

        assertEquals(
            listOf(
                BambooPlaybackIntent.Refresh,
                BambooPlaybackIntent.TogglePlayback,
                BambooPlaybackIntent.SkipPrevious,
                BambooPlaybackIntent.SkipNext,
                BambooPlaybackIntent.SetVolume(0.25F)
            ),
            playback.intents
        )
    }
}

private class RecordingPlaybackRepository(initialState: BambooPlaybackState = BambooPlaybackState()) :
    BambooPlaybackRepository {
    private var currentState = initialState
    private val listeners = mutableSetOf<(BambooPlaybackState) -> Unit>()

    val intents = mutableListOf<BambooPlaybackIntent>()

    override val state = kotlinx.coroutines.flow.MutableStateFlow(initialState)

    override fun start() = Unit

    override fun dispatch(intent: BambooPlaybackIntent) {
        intents += intent
    }

    override fun observe(listener: (BambooPlaybackState) -> Unit): AutoCloseable {
        listeners += listener
        listener(currentState)

        return AutoCloseable {
            listeners -= listener
        }
    }

    override fun observeEffects(listener: (List<EngineEffect>) -> Unit): AutoCloseable = AutoCloseable { }

    override fun close() = Unit
}
