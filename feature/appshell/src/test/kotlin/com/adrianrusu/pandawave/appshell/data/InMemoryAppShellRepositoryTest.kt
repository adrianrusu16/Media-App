package com.adrianrusu.pandawave.appshell.data

import com.adrianrusu.pandawave.appshell.domain.AppDestination
import com.adrianrusu.pandawave.appshell.domain.AppShellIntent
import com.adrianrusu.pandawave.core.playback.BambooEngineConnectionUiState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRestrictionState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackStatus
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
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
        assertEquals(0.325F, repository.state.value.miniPlayer.progressAt(nowMillis = 2_100L).fraction)
        assertEquals(BambooEngineConnectionUiState.Ready, repository.state.value.engineConnection)
    }

    @Test
    fun `restriction state does not change mini player presentation`() {
        val unrestrictedPlayback = BambooPlaybackState(
            mediaId = "track-1",
            title = "Quiet Cabin",
            artist = "PandaWave",
            playbackStatus = BambooPlaybackStatus.Playing,
            engineConnection = BambooEngineConnectionUiState.Ready,
            restriction = BambooPlaybackRestrictionState(
                isRestricted = false
            )
        )
        val restrictedPlayback = unrestrictedPlayback.copy(
            restriction = BambooPlaybackRestrictionState(
                isRestricted = true
            )
        )
        val unrestrictedRepository = InMemoryAppShellRepository(
            playbackRepository = RecordingPlaybackRepository(unrestrictedPlayback)
        )
        val restrictedRepository = InMemoryAppShellRepository(
            playbackRepository = RecordingPlaybackRepository(restrictedPlayback)
        )

        unrestrictedRepository.start()
        restrictedRepository.start()

        assertEquals(
            unrestrictedRepository.state.value.miniPlayer,
            restrictedRepository.state.value.miniPlayer
        )
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
        repository.dispatch(AppShellIntent.OpenNowPlaying)

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

    override fun observeEffects(listener: (List<EngineEffect>) -> Unit): AutoCloseable = AutoCloseable { }

    override fun close() = Unit
}
