package com.adrianrusu.mediaapp.appshell.data

import com.adrianrusu.mediaapp.appshell.domain.AppDestination
import com.adrianrusu.mediaapp.appshell.domain.AppShellIntent
import com.adrianrusu.mediaapp.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackIntent
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRestrictionState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackState
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InMemoryAppShellRepositoryTest {
    @Test
    fun `start projects shared playback state into mini player`() {
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
                updatedAtEpochMillis = 100L,
                positionMillis = 10_000L,
                durationMillis = 40_000L,
                playbackSpeed = 1.5F
            )
        )
        val repository = InMemoryAppShellRepository(playbackRepository = playback)

        repository.start()

        assertEquals("Quiet Cabin", repository.state.value.miniPlayer.title)
        assertEquals("PandaWave", repository.state.value.miniPlayer.subtitle)
        assertTrue(repository.state.value.miniPlayer.isPlaying)
        assertTrue(repository.state.value.miniPlayer.isRestricted)
        assertEquals(0.325F, repository.state.value.miniPlayer.progressAt(nowMillis = 2_100L).fraction)
        assertEquals(BambooEngineConnectionUiState.Ready, repository.state.value.engineConnection)
        assertEquals("Driver-safe mode", repository.state.value.restriction.label)
    }

    @Test
    fun `playback intents forward to shared playback repository`() {
        val playback = RecordingPlaybackRepository()
        val repository = InMemoryAppShellRepository(playbackRepository = playback)

        repository.start()
        repository.dispatch(AppShellIntent.TogglePlayback)
        repository.dispatch(AppShellIntent.SkipPrevious)
        repository.dispatch(AppShellIntent.SkipNext)

        assertEquals(
            listOf(
                BambooPlaybackIntent.TogglePlayback,
                BambooPlaybackIntent.SkipPrevious,
                BambooPlaybackIntent.SkipNext
            ),
            playback.intents
        )
    }

    @Test
    fun `now playing destination hides mini player`() {
        val repository = InMemoryAppShellRepository(playbackRepository = RecordingPlaybackRepository())

        repository.start()
        repository.dispatch(AppShellIntent.SelectDestination(AppDestination.NowPlaying))

        assertFalse(repository.state.value.shouldShowMiniPlayer)
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
