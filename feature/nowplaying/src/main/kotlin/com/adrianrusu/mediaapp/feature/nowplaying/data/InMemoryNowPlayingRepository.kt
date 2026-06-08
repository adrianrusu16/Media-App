package com.adrianrusu.mediaapp.feature.nowplaying.data

import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingEngineConnectionUiState
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingIntent
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingReducer
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingRepository
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingRestrictionState
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class InMemoryNowPlayingRepository(
    private val engine: EngineGateway,
    private val uxRestrictionObserver: AutomotiveUxRestrictionObserver
) : NowPlayingRepository {
    private val mutableState = MutableStateFlow(NowPlayingState())
    private var engineSnapshotSubscription: AutoCloseable? = null
    private var engineEventSubscription: AutoCloseable? = null

    override val state: StateFlow<NowPlayingState> = mutableState.asStateFlow()

    override fun start() {
        engineSnapshotSubscription?.close()
        engineEventSubscription?.close()
        engineSnapshotSubscription = engine.observeSnapshots { snapshot ->
            mutableState.update { current ->
                NowPlayingReducer.reduce(
                    state = current,
                    snapshot = snapshot
                )
            }
        }
        engineEventSubscription = engine.observeEngineEvents { event ->
            mutableState.update { current ->
                current.withEngineEvent(event)
            }
        }

        bootstrapEngine()

        uxRestrictionObserver.start { restrictions ->
            mutableState.update { current ->
                NowPlayingReducer.reduce(
                    state = current,
                    restriction = restrictions.toNowPlayingRestrictionState()
                )
            }
        }
    }

    override fun dispatch(intent: NowPlayingIntent) {
        when (intent) {
            NowPlayingIntent.Refresh -> refreshFromEngine()
            NowPlayingIntent.TogglePlayback -> togglePlayback()
        }
    }

    override fun close() {
        engineSnapshotSubscription?.close()
        engineSnapshotSubscription = null
        engineEventSubscription?.close()
        engineEventSubscription = null
        uxRestrictionObserver.close()
    }

    private fun bootstrapEngine() {
        val result = engine.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_BOOTSTRAP,
                payload = null
            )
        )

        mutableState.update { current ->
            NowPlayingReducer
                .reduce(
                    state = current,
                    snapshot = result.snapshot
                ).withEngineEvent(result.event)
        }
    }

    private fun refreshFromEngine() {
        if (!mutableState.value.canDispatchEngineCommands) {
            return
        }

        val snapshot = engine.snapshot()
        mutableState.update { current ->
            NowPlayingReducer.reduce(
                state = current,
                snapshot = snapshot
            )
        }
    }

    private fun togglePlayback() {
        if (!mutableState.value.canDispatchEngineCommands) {
            return
        }

        val commandType = when (mutableState.value.isPlaying) {
            true -> EngineCommand.TYPE_PAUSE
            false -> EngineCommand.TYPE_PLAY
        }
        val result = engine.dispatch(
            EngineCommand(
                type = commandType,
                payload = null
            )
        )

        mutableState.update { current ->
            NowPlayingReducer
                .reduce(
                    state = current,
                    snapshot = result.snapshot
                ).withEngineEvent(result.event)
        }
    }
}

private fun AutomotiveUxRestrictions.toNowPlayingRestrictionState(): NowPlayingRestrictionState {
    val label = when (source) {
        AutomotiveUxRestrictions.Source.AutomotivePlatform ->
            if (isRestricted) "Driver-safe mode" else "Unrestricted"

        AutomotiveUxRestrictions.Source.NotAutomotive ->
            "Standard device"

        AutomotiveUxRestrictions.Source.Unavailable ->
            "Safety status unavailable"
    }

    return NowPlayingRestrictionState(
        label = label,
        isRestricted = isRestricted
    )
}

private fun NowPlayingState.withEngineEvent(event: EngineEvent): NowPlayingState = copy(
    engineConnection = event.toConnectionUiState(current = engineConnection)
)

private fun EngineEvent.toConnectionUiState(
    current: NowPlayingEngineConnectionUiState
): NowPlayingEngineConnectionUiState = when (type) {
    EngineEvent.TYPE_COMMAND_APPLIED,
    EngineEvent.TYPE_LISTENER_REGISTERED,
    EngineEvent.TYPE_SERVICE_CONNECTED -> NowPlayingEngineConnectionUiState.Ready

    EngineEvent.TYPE_COMMAND_QUEUED -> NowPlayingEngineConnectionUiState.Connecting

    EngineEvent.TYPE_SERVICE_BINDING_DIED -> NowPlayingEngineConnectionUiState.Reconnecting

    EngineEvent.TYPE_GATEWAY_UNAVAILABLE,
    EngineEvent.TYPE_SERVICE_DISCONNECTED,
    EngineEvent.TYPE_SERVICE_NULL_BINDING -> NowPlayingEngineConnectionUiState.Unavailable

    else -> current
}
