package com.adrianrusu.mediaapp.feature.nowplaying.data

import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.EngineGateway
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

    override val state: StateFlow<NowPlayingState> = mutableState.asStateFlow()

    override fun start() {
        engineSnapshotSubscription?.close()
        engineSnapshotSubscription = engine.observeSnapshots { snapshot ->
            mutableState.update { current ->
                NowPlayingReducer.reduce(
                    state = current,
                    snapshot = snapshot
                )
            }
        }

        refreshFromEngine()

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
        uxRestrictionObserver.close()
    }

    private fun refreshFromEngine() {
        val snapshot = engine.snapshot()
        mutableState.update { current ->
            NowPlayingReducer.reduce(
                state = current,
                snapshot = snapshot
            )
        }
    }

    private fun togglePlayback() {
        val commandType = when (mutableState.value.isPlaying) {
            true -> EngineCommand.TYPE_PAUSE
            false -> EngineCommand.TYPE_PLAY
        }
        val snapshot = engine.dispatch(
            EngineCommand(
                type = commandType,
                payload = null
            )
        ).snapshot

        mutableState.update { current ->
            NowPlayingReducer.reduce(
                state = current,
                snapshot = snapshot
            )
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
