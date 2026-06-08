package com.adrianrusu.mediaapp.core.playback

import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DefaultBambooPlaybackRepository(
    private val engine: EngineGateway,
    private val uxRestrictionObserver: AutomotiveUxRestrictionObserver
) : BambooPlaybackRepository {
    private val mutableState = MutableStateFlow(BambooPlaybackState())
    private val listeners = mutableSetOf<(BambooPlaybackState) -> Unit>()
    private var engineSnapshotSubscription: AutoCloseable? = null
    private var engineEventSubscription: AutoCloseable? = null
    private var startCount = 0

    override val state: StateFlow<BambooPlaybackState> = mutableState.asStateFlow()

    override fun start() {
        startCount += 1

        if (startCount > 1) {
            return
        }

        engineSnapshotSubscription = engine.observeSnapshots { snapshot ->
            updateState { current ->
                current.withEngineSnapshot(snapshot)
            }
        }
        engineEventSubscription = engine.observeEngineEvents { event ->
            updateState { current ->
                current.withEngineEvent(event)
            }
        }

        bootstrapEngine()

        uxRestrictionObserver.start { restrictions ->
            updateState { current ->
                current.copy(restriction = restrictions.toPlaybackRestrictionState())
            }
        }
    }

    override fun dispatch(intent: BambooPlaybackIntent) {
        when (intent) {
            BambooPlaybackIntent.Refresh -> refreshFromEngine()
            BambooPlaybackIntent.Play -> dispatchEngineCommand(EngineCommand.TYPE_PLAY)
            BambooPlaybackIntent.Pause -> dispatchEngineCommand(EngineCommand.TYPE_PAUSE)
            BambooPlaybackIntent.TogglePlayback -> togglePlayback()
            BambooPlaybackIntent.SkipPrevious -> dispatchEngineCommand(EngineCommand.TYPE_SKIP_PREVIOUS)
            BambooPlaybackIntent.SkipNext -> dispatchEngineCommand(EngineCommand.TYPE_SKIP_NEXT)
        }
    }

    override fun observe(listener: (BambooPlaybackState) -> Unit): AutoCloseable {
        listeners += listener
        listener(state.value)

        return AutoCloseable {
            listeners -= listener
        }
    }

    override fun close() {
        if (startCount == 0) {
            return
        }

        startCount -= 1

        if (startCount > 0) {
            return
        }

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

        updateState { current ->
            current
                .withEngineSnapshot(result.snapshot)
                .withEngineEvent(result.event)
        }
    }

    private fun refreshFromEngine() {
        if (!state.value.canDispatchEngineCommands) {
            return
        }

        updateState { current ->
            current.withEngineSnapshot(engine.snapshot())
        }
    }

    private fun togglePlayback() {
        val commandType = when (state.value.isPlaying) {
            true -> EngineCommand.TYPE_PAUSE
            false -> EngineCommand.TYPE_PLAY
        }

        dispatchEngineCommand(commandType)
    }

    private fun dispatchEngineCommand(commandType: String) {
        if (!state.value.canDispatchEngineCommands) {
            return
        }

        val result = engine.dispatch(
            EngineCommand(
                type = commandType,
                payload = null
            )
        )

        updateState { current ->
            current
                .withEngineSnapshot(result.snapshot)
                .withEngineEvent(result.event)
        }
    }

    private fun updateState(reducer: (BambooPlaybackState) -> BambooPlaybackState) {
        mutableState.update(reducer)
        notifyStateChanged()
    }

    private fun notifyStateChanged() {
        val current = state.value
        listeners.toList().forEach { listener ->
            listener(current)
        }
    }
}

private fun BambooPlaybackState.withEngineSnapshot(snapshot: EngineSnapshot): BambooPlaybackState = copy(
    mediaId = snapshot.mediaId,
    title = snapshot.title ?: titleFor(snapshot.playbackState),
    artist = snapshot.artist ?: artistFor(snapshot.playbackState),
    playbackStatus = snapshot.playbackState.toPlaybackStatus(),
    updatedAtEpochMillis = snapshot.updatedAtEpochMillis
)

private fun BambooPlaybackState.withEngineEvent(event: EngineEvent): BambooPlaybackState = copy(
    engineConnection = event.toConnectionUiState(current = engineConnection)
)

private fun String.toPlaybackStatus(): BambooPlaybackStatus = when (this) {
    EngineSnapshot.PLAYBACK_PLAYING -> BambooPlaybackStatus.Playing
    EngineSnapshot.PLAYBACK_PAUSED -> BambooPlaybackStatus.Paused
    else -> BambooPlaybackStatus.Idle
}

private fun titleFor(playbackState: String): String = when (playbackState) {
    EngineSnapshot.PLAYBACK_PLAYING -> BambooPlaybackText.FALLBACK_PLAYING_TITLE
    EngineSnapshot.PLAYBACK_PAUSED -> BambooPlaybackText.FALLBACK_PAUSED_TITLE
    else -> BambooPlaybackText.FALLBACK_IDLE_TITLE
}

private fun artistFor(playbackState: String): String = when (playbackState) {
    EngineSnapshot.PLAYBACK_PLAYING -> BambooPlaybackText.FALLBACK_PLAYING_SUBTITLE
    EngineSnapshot.PLAYBACK_PAUSED -> BambooPlaybackText.FALLBACK_PAUSED_SUBTITLE
    else -> BambooPlaybackText.FALLBACK_IDLE_SUBTITLE
}

private fun EngineEvent.toConnectionUiState(current: BambooEngineConnectionUiState): BambooEngineConnectionUiState =
    when (type) {
        EngineEvent.TYPE_COMMAND_APPLIED,
        EngineEvent.TYPE_LISTENER_REGISTERED,
        EngineEvent.TYPE_SERVICE_CONNECTED -> BambooEngineConnectionUiState.Ready

        EngineEvent.TYPE_COMMAND_QUEUED -> BambooEngineConnectionUiState.Connecting

        EngineEvent.TYPE_SERVICE_BINDING_DIED -> BambooEngineConnectionUiState.Reconnecting

        EngineEvent.TYPE_GATEWAY_UNAVAILABLE,
        EngineEvent.TYPE_SERVICE_DISCONNECTED,
        EngineEvent.TYPE_SERVICE_NULL_BINDING -> BambooEngineConnectionUiState.Unavailable

        else -> current
    }

private fun AutomotiveUxRestrictions.toPlaybackRestrictionState(): BambooPlaybackRestrictionState {
    val label = when (source) {
        AutomotiveUxRestrictions.Source.AutomotivePlatform ->
            if (isRestricted) "Driver-safe mode" else "Unrestricted"

        AutomotiveUxRestrictions.Source.NotAutomotive ->
            "Standard device"

        AutomotiveUxRestrictions.Source.Unavailable ->
            "Safety status unavailable"
    }

    return BambooPlaybackRestrictionState(
        label = label,
        isRestricted = isRestricted
    )
}
