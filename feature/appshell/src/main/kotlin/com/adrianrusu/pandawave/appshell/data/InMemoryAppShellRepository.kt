package com.adrianrusu.pandawave.appshell.data

import com.adrianrusu.pandawave.appshell.domain.AppShellIntent
import com.adrianrusu.pandawave.appshell.domain.AppShellRepository
import com.adrianrusu.pandawave.appshell.domain.AppShellState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.ui.miniplayer.MiniPlayerProgressAnchor
import com.adrianrusu.pandawave.core.ui.miniplayer.MiniPlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class InMemoryAppShellRepository(private val playbackRepository: BambooPlaybackRepository) :
    AppShellRepository {
    private val mutableState = MutableStateFlow(AppShellState())
    private var playbackSubscription: AutoCloseable? = null

    override val state: StateFlow<AppShellState> = mutableState.asStateFlow()

    override fun start() {
        playbackSubscription?.close()
        playbackSubscription = playbackRepository.observe { playback ->
            mutableState.value = mutableState.value.withPlaybackState(playback)
        }
        playbackRepository.start()
    }

    override fun dispatch(intent: AppShellIntent) {
        when (intent) {
            AppShellIntent.TogglePlayback -> playbackRepository.dispatch(BambooPlaybackIntent.TogglePlayback)
            AppShellIntent.SkipPrevious -> playbackRepository.dispatch(BambooPlaybackIntent.SkipPrevious)
            AppShellIntent.SkipNext -> playbackRepository.dispatch(BambooPlaybackIntent.SkipNext)
        }
    }

    override fun close() {
        playbackSubscription?.close()
        playbackSubscription = null
        playbackRepository.close()
    }
}

internal fun AppShellState.withPlaybackState(playback: BambooPlaybackState): AppShellState = copy(
    engineConnection = playback.engineConnection,
    miniPlayer = playback.toMiniPlayerState()
)

private fun BambooPlaybackState.toMiniPlayerState(): MiniPlayerState = MiniPlayerState(
    title = title,
    subtitle = artist,
    isPlaying = isPlaying,
    progressAnchor = MiniPlayerProgressAnchor(
        positionMillis = positionMillis,
        durationMillis = durationMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        playbackSpeed = playbackSpeed,
        isPlaying = isPlaying
    )
)
