package com.adrianrusu.mediaapp.appshell.data

import com.adrianrusu.mediaapp.appshell.domain.AppShellIntent
import com.adrianrusu.mediaapp.appshell.domain.AppShellReducer
import com.adrianrusu.mediaapp.appshell.domain.AppShellRepository
import com.adrianrusu.mediaapp.appshell.domain.AppShellState
import com.adrianrusu.mediaapp.appshell.domain.RestrictionUiState
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.engine.RustEngine
import com.adrianrusu.mediaapp.core.ui.miniplayer.MiniPlayerState
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class InMemoryAppShellRepository(
    private val uxRestrictionObserver: AutomotiveUxRestrictionObserver,
    private val engine: RustEngine
) : AppShellRepository {
    private val mutableState = MutableStateFlow(AppShellState())

    override val state: StateFlow<AppShellState> = mutableState.asStateFlow()

    override fun start() {
        val bootstrapSnapshot = engine.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_BOOTSTRAP,
                payload = null
            )
        ).snapshot

        mutableState.update { current ->
            current.withEngineSnapshot(bootstrapSnapshot)
        }

        uxRestrictionObserver.start { restrictions ->
            mutableState.update { current ->
                current.copy(
                    restriction = restrictions.toUiState(),
                    miniPlayer = current.miniPlayer.copy(
                        isRestricted = restrictions.isRestricted
                    )
                )
            }
        }
    }

    override fun dispatch(intent: AppShellIntent) {
        when (intent) {
            AppShellIntent.TogglePlayback -> dispatchPlaybackCommand()

            AppShellIntent.SkipPrevious -> dispatchEngineCommand(EngineCommand.TYPE_SKIP_PREVIOUS)

            AppShellIntent.SkipNext -> dispatchEngineCommand(EngineCommand.TYPE_SKIP_NEXT)

            is AppShellIntent.SelectDestination -> {
                mutableState.update { current ->
                    AppShellReducer.reduce(current, intent)
                }
            }
        }
    }

    override fun close() {
        uxRestrictionObserver.close()
    }

    private fun dispatchPlaybackCommand() {
        val commandType = when (mutableState.value.miniPlayer.isPlaying) {
            true -> EngineCommand.TYPE_PAUSE
            false -> EngineCommand.TYPE_PLAY
        }

        dispatchEngineCommand(commandType)
    }

    private fun dispatchEngineCommand(commandType: String) {
        val snapshot = engine.dispatch(
            EngineCommand(
                type = commandType,
                payload = null
            )
        ).snapshot

        mutableState.update { current ->
            current.withEngineSnapshot(snapshot)
        }
    }
}

internal fun AppShellState.withEngineSnapshot(snapshot: EngineSnapshot): AppShellState = copy(
    miniPlayer = snapshot.toMiniPlayerState(
        isRestricted = miniPlayer.isRestricted
    )
)

private fun EngineSnapshot.toMiniPlayerState(isRestricted: Boolean): MiniPlayerState {
    val isPlaying = playbackState == EngineSnapshot.PLAYBACK_PLAYING
    val title = title ?: when (playbackState) {
        EngineSnapshot.PLAYBACK_PLAYING -> BambooPlaybackText.FALLBACK_PLAYING_TITLE
        EngineSnapshot.PLAYBACK_PAUSED -> BambooPlaybackText.FALLBACK_PAUSED_TITLE
        else -> MiniPlayerState.Empty.title
    }
    val subtitle = artist ?: when (playbackState) {
        EngineSnapshot.PLAYBACK_PLAYING -> BambooPlaybackText.FALLBACK_PLAYING_SUBTITLE
        EngineSnapshot.PLAYBACK_PAUSED -> BambooPlaybackText.FALLBACK_PAUSED_SUBTITLE
        else -> MiniPlayerState.Empty.subtitle
    }

    return MiniPlayerState(
        title = title,
        subtitle = subtitle,
        isPlaying = isPlaying,
        isRestricted = isRestricted
    )
}

private fun AutomotiveUxRestrictions.toUiState(): RestrictionUiState {
    val label = when (source) {
        AutomotiveUxRestrictions.Source.AutomotivePlatform ->
            if (isRestricted) "Driver-safe mode" else "Unrestricted"

        AutomotiveUxRestrictions.Source.NotAutomotive ->
            "Standard device"

        AutomotiveUxRestrictions.Source.Unavailable ->
            "Safety status unavailable"
    }

    return RestrictionUiState(
        label = label,
        isRestricted = isRestricted
    )
}
