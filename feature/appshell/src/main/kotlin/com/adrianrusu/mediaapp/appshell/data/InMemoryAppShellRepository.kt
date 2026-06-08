package com.adrianrusu.mediaapp.appshell.data

import com.adrianrusu.mediaapp.appshell.domain.AppShellIntent
import com.adrianrusu.mediaapp.appshell.domain.AppShellReducer
import com.adrianrusu.mediaapp.appshell.domain.AppShellRepository
import com.adrianrusu.mediaapp.appshell.domain.AppShellState
import com.adrianrusu.mediaapp.appshell.domain.EngineConnectionUiState
import com.adrianrusu.mediaapp.appshell.domain.RestrictionUiState
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.mediaapp.core.ui.miniplayer.MiniPlayerState
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class InMemoryAppShellRepository(
    private val uxRestrictionObserver: AutomotiveUxRestrictionObserver,
    private val engine: EngineGateway
) : AppShellRepository {
    private val mutableState = MutableStateFlow(AppShellState())
    private var engineSnapshotSubscription: AutoCloseable? = null
    private var engineEventSubscription: AutoCloseable? = null

    override val state: StateFlow<AppShellState> = mutableState.asStateFlow()

    override fun start() {
        engineSnapshotSubscription?.close()
        engineEventSubscription?.close()
        engineSnapshotSubscription = engine.observeSnapshots { snapshot ->
            mutableState.update { current ->
                current.withEngineSnapshot(snapshot)
            }
        }
        engineEventSubscription = engine.observeEngineEvents { event ->
            mutableState.update { current ->
                current.withEngineEvent(event)
            }
        }

        val bootstrapResult = engine.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_BOOTSTRAP,
                payload = null
            )
        )

        mutableState.update { current ->
            current
                .withEngineSnapshot(bootstrapResult.snapshot)
                .withEngineEvent(bootstrapResult.event)
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
        engineSnapshotSubscription?.close()
        engineSnapshotSubscription = null
        engineEventSubscription?.close()
        engineEventSubscription = null
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
        if (!mutableState.value.canDispatchEngineCommands) {
            return
        }

        val result = engine.dispatch(
            EngineCommand(
                type = commandType,
                payload = null
            )
        )

        mutableState.update { current ->
            current
                .withEngineSnapshot(result.snapshot)
                .withEngineEvent(result.event)
        }
    }
}

internal fun AppShellState.withEngineSnapshot(snapshot: EngineSnapshot): AppShellState = copy(
    miniPlayer = snapshot.toMiniPlayerState(
        isRestricted = miniPlayer.isRestricted
    )
)

internal fun AppShellState.withEngineEvent(event: EngineEvent): AppShellState = copy(
    engineConnection = event.toConnectionUiState(current = engineConnection)
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

private fun EngineEvent.toConnectionUiState(current: EngineConnectionUiState): EngineConnectionUiState = when (type) {
    EngineEvent.TYPE_COMMAND_APPLIED,
    EngineEvent.TYPE_LISTENER_REGISTERED,
    EngineEvent.TYPE_SERVICE_CONNECTED -> EngineConnectionUiState.Ready

    EngineEvent.TYPE_COMMAND_QUEUED -> EngineConnectionUiState.Connecting

    EngineEvent.TYPE_SERVICE_BINDING_DIED -> EngineConnectionUiState.Reconnecting

    EngineEvent.TYPE_GATEWAY_UNAVAILABLE,
    EngineEvent.TYPE_SERVICE_DISCONNECTED,
    EngineEvent.TYPE_SERVICE_NULL_BINDING -> EngineConnectionUiState.Unavailable

    else -> current
}
