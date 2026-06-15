package com.adrianrusu.mediaapp.core.playback

import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.mediaapp.core.telemetry.TelemetryLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DefaultBambooPlaybackRepository(
    private val engine: EngineGateway,
    private val uxRestrictionObserver: AutomotiveUxRestrictionObserver,
    private val telemetryLogger: TelemetryLogger
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
                BambooPlaybackStateProjector.fromEngineSnapshot(
                    current = current,
                    snapshot = snapshot
                )
            }
        }
        engineEventSubscription = engine.observeEngineEvents { event ->
            updateState { current ->
                BambooPlaybackStateProjector.fromEngineEvent(
                    current = current,
                    event = event
                )
            }
        }

        bootstrapEngine()

        uxRestrictionObserver.start { restrictions ->
            updateState { current ->
                BambooPlaybackStateProjector.fromUxRestrictions(
                    current = current,
                    restrictions = restrictions
                )
            }
        }
    }

    override fun dispatch(intent: BambooPlaybackIntent) {
        telemetryLogger.debug(
            name = PlaybackTelemetryEvents.INTENT_RECEIVED,
            attributes = mapOf(
                "intent" to intent.telemetryName,
                "engine_status" to state.value.engineConnection.status.name
            )
        )

        when (intent) {
            BambooPlaybackIntent.Refresh -> refreshFromEngine()

            BambooPlaybackIntent.Play -> dispatchEngineCommand(
                commandType = EngineCommand.TYPE_PLAY,
                sourceIntent = intent
            )

            BambooPlaybackIntent.Pause -> dispatchEngineCommand(
                commandType = EngineCommand.TYPE_PAUSE,
                sourceIntent = intent
            )

            BambooPlaybackIntent.TogglePlayback -> togglePlayback()

            BambooPlaybackIntent.SkipPrevious -> dispatchEngineCommand(
                commandType = EngineCommand.TYPE_SKIP_PREVIOUS,
                sourceIntent = intent
            )

            BambooPlaybackIntent.SkipNext -> dispatchEngineCommand(
                commandType = EngineCommand.TYPE_SKIP_NEXT,
                sourceIntent = intent
            )

            is BambooPlaybackIntent.SeekTo -> dispatchEngineCommand(
                commandType = EngineCommand.TYPE_SEEK,
                payload = intent.positionMillis.coerceAtLeast(0L).toString(),
                sourceIntent = intent
            )

            is BambooPlaybackIntent.SetSpeed -> dispatchEngineCommand(
                commandType = EngineCommand.TYPE_SET_SPEED,
                payload = intent.speed.coerceAtLeast(0F).toString(),
                sourceIntent = intent
            )

            is BambooPlaybackIntent.PlatformEvent -> dispatchPlatformEvent(intent)
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
            current.fromEngineResult(result)
        }
    }

    private fun refreshFromEngine() {
        if (!state.value.canDispatchEngineCommands) {
            logBlockedIntent(BambooPlaybackIntent.Refresh)
            return
        }

        telemetryLogger.debug(
            name = PlaybackTelemetryEvents.ENGINE_SNAPSHOT_REQUESTED,
            attributes = mapOf("intent" to BambooPlaybackIntent.Refresh.telemetryName)
        )
        updateState { current ->
            BambooPlaybackStateProjector.fromEngineSnapshot(
                current = current,
                snapshot = engine.snapshot()
            )
        }
    }

    private fun togglePlayback() {
        val commandType = when (state.value.isPlaying) {
            true -> EngineCommand.TYPE_PAUSE
            false -> EngineCommand.TYPE_PLAY
        }

        dispatchEngineCommand(
            commandType = commandType,
            sourceIntent = BambooPlaybackIntent.TogglePlayback
        )
    }

    private fun dispatchEngineCommand(
        commandType: String,
        payload: String? = null,
        sourceIntent: BambooPlaybackIntent
    ) {
        if (!state.value.canDispatchEngineCommands) {
            logBlockedIntent(sourceIntent)
            return
        }

        telemetryLogger.info(
            name = PlaybackTelemetryEvents.ENGINE_COMMAND_DISPATCHED,
            attributes = mapOf(
                "intent" to sourceIntent.telemetryName,
                "command_type" to commandType
            )
        )

        val result = engine.dispatch(
            EngineCommand(
                type = commandType,
                payload = payload
            )
        )

        updateState { current ->
            current.fromEngineResult(result)
        }
    }

    private fun dispatchPlatformEvent(intent: BambooPlaybackIntent.PlatformEvent) {
        if (!state.value.canDispatchEngineCommands) {
            logBlockedIntent(intent)
            return
        }

        telemetryLogger.info(
            name = PlaybackTelemetryEvents.PLATFORM_EVENT_DISPATCHED,
            attributes = mapOf(
                "intent" to intent.telemetryName,
                "event_type" to intent.type
            )
        )

        val result = engine.dispatchPlatformEvent(
            com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent(
                type = intent.type,
                payload = intent.payload
            )
        )

        updateState { current ->
            current.fromEngineResult(result)
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

    private fun logBlockedIntent(intent: BambooPlaybackIntent) {
        telemetryLogger.info(
            name = PlaybackTelemetryEvents.INTENT_BLOCKED,
            attributes = mapOf(
                "intent" to intent.telemetryName,
                "engine_status" to state.value.engineConnection.status.name
            )
        )
    }
}

internal object PlaybackTelemetryEvents {
    const val INTENT_RECEIVED = "playback.intent.received"
    const val INTENT_BLOCKED = "playback.intent.blocked"
    const val ENGINE_COMMAND_DISPATCHED = "playback.engine.command.dispatched"
    const val PLATFORM_EVENT_DISPATCHED = "playback.platform_event.dispatched"
    const val ENGINE_SNAPSHOT_REQUESTED = "playback.engine.snapshot.requested"
}

private val BambooPlaybackIntent.telemetryName: String
    get() = when (this) {
        BambooPlaybackIntent.Refresh -> "refresh"
        BambooPlaybackIntent.Play -> "play"
        BambooPlaybackIntent.Pause -> "pause"
        BambooPlaybackIntent.TogglePlayback -> "toggle_playback"
        BambooPlaybackIntent.SkipPrevious -> "skip_previous"
        BambooPlaybackIntent.SkipNext -> "skip_next"
        is BambooPlaybackIntent.SeekTo -> "seek_to"
        is BambooPlaybackIntent.SetSpeed -> "set_speed"
        is BambooPlaybackIntent.PlatformEvent -> "platform_event"
    }

private fun BambooPlaybackState.fromEngineResult(result: EngineDispatchResult): BambooPlaybackState =
    BambooPlaybackStateProjector.fromEngineEvent(
        current = BambooPlaybackStateProjector.fromEngineSnapshot(
            current = this,
            snapshot = result.snapshot
        ),
        event = result.event
    )
