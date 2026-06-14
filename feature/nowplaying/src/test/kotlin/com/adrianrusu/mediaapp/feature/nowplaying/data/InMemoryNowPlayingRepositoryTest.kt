package com.adrianrusu.mediaapp.feature.nowplaying.data

import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackIntent
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRestrictionState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackStatus
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingIntent
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingPlaybackState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryNowPlayingRepositoryTest {
    @Test
    fun `start projects shared playback state into now playing state`() {
        val playback = RecordingPlaybackRepository(
            initialState = BambooPlaybackState(
                mediaId = "track-1",
                title = "Quiet Cabin",
                artist = "PandaWave",
                playbackStatus = BambooPlaybackStatus.Playing,
                engineConnection = BambooEngineConnectionUiState.Ready,
                restriction = BambooPlaybackRestrictionState(
                    label = "Driver-safe mode",
                    isRestricted = true
                ),
                updatedAtEpochMillis = 100L
            )
        )
        val repository = InMemoryNowPlayingRepository(playbackRepository = playback)

        repository.start()

        assertEquals("track-1", repository.state.value.mediaId)
        assertEquals("Quiet Cabin", repository.state.value.title)
        assertEquals("PandaWave", repository.state.value.artist)
        assertEquals(NowPlayingPlaybackState.Playing, repository.state.value.playbackState)
        assertEquals(BambooEngineConnectionUiState.Ready, repository.state.value.engineConnection)
        assertEquals("Driver-safe mode", repository.state.value.restriction.label)
        assertTrue(repository.state.value.restriction.isRestricted)
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

        assertEquals(
            listOf(
                BambooPlaybackIntent.Refresh,
                BambooPlaybackIntent.TogglePlayback,
                BambooPlaybackIntent.SkipPrevious,
                BambooPlaybackIntent.SkipNext
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

    override fun close() = Unit
}
