package com.adrianrusu.pandawave.core.playback

import com.adrianrusu.pandawave.core.automotive.driving.AutomotiveDrivingState
import com.adrianrusu.pandawave.core.automotive.driving.AutomotiveDrivingStateObserver
import com.adrianrusu.pandawave.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.pandawave.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DefaultBambooPlaybackRepository(
    private val engine: EngineGateway,
    private val uxRestrictionObserver: AutomotiveUxRestrictionObserver,
    telemetryLogger: TelemetryLogger,
    private val drivingStateObserver: AutomotiveDrivingStateObserver = AutomotiveDrivingStateObserver.Unavailable
) : BambooPlaybackRepository {
    private val telemetryLogger = telemetryLogger.forModule(TelemetryModule.Playback)
    private val automotiveTelemetryLogger = telemetryLogger.forModule(TelemetryModule.Automotive)
    private val mutableState = MutableStateFlow(BambooPlaybackState())
    private val listeners = mutableSetOf<(BambooPlaybackState) -> Unit>()
    private val effectListeners = mutableSetOf<(List<EngineEffect>) -> Unit>()
    private var engineSnapshotSubscription: AutoCloseable? = null
    private var engineEventSubscription: AutoCloseable? = null
    private var lastDrivingState = AutomotiveDrivingState.Unknown
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
            dispatchPlatformEvent(
                BambooPlaybackIntent.PlatformEvent(
                    type = EnginePlatformEvent.TYPE_UX_RESTRICTIONS_CHANGED,
                    payload = restrictions.toEnginePayload()
                )
            )
        }
        drivingStateObserver.start { drivingState ->
            if (drivingState == lastDrivingState) {
                return@start
            }
            val previousState = lastDrivingState
            lastDrivingState = drivingState
            logDrivingStateChanged(previousState = previousState, currentState = drivingState)
            dispatchPlatformEvent(
                BambooPlaybackIntent.PlatformEvent(
                    type = EnginePlatformEvent.TYPE_VEHICLE_DRIVING_STATE_CHANGED,
                    payload = drivingState.toEnginePayload()
                )
            )
        }
    }

    override fun dispatch(intent: BambooPlaybackIntent) {
        telemetryLogger.debug(
            name = PlaybackTelemetryEvents.INTENT_RECEIVED,
            attributes = mapOf(
                BambooPlaybackTelemetryAttributes.INTENT to intent.telemetryName,
                BambooPlaybackTelemetryAttributes.ENGINE_STATUS to state.value.engineConnection.status.name
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
                payload = EngineCommandPayloads.seekPositionMillis(intent.positionMillis),
                sourceIntent = intent
            )

            is BambooPlaybackIntent.SetSpeed -> dispatchEngineCommand(
                commandType = EngineCommand.TYPE_SET_SPEED,
                payload = EngineCommandPayloads.playbackSpeed(intent.speed),
                sourceIntent = intent
            )

            is BambooPlaybackIntent.PlayMedia -> dispatchEngineCommand(
                commandType = EngineCommand.TYPE_PLAY_MEDIA_BY_ID,
                payload = EngineCommandPayloads.mediaId(intent.mediaId),
                sourceIntent = intent
            )

            is BambooPlaybackIntent.SearchCatalog -> dispatchEngineCommand(
                commandType = EngineCommand.TYPE_SEARCH,
                payload = EngineCommandPayloads.searchQuery(intent.query),
                sourceIntent = intent
            )

            is BambooPlaybackIntent.BrowseCatalog -> dispatchEngineCommand(
                commandType = EngineCommand.TYPE_BROWSE,
                payload = EngineCommandPayloads.browseParentId(intent.parentId),
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

    override fun observeEffects(listener: (List<EngineEffect>) -> Unit): AutoCloseable {
        effectListeners += listener

        return AutoCloseable {
            effectListeners -= listener
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
        effectListeners.clear()
        uxRestrictionObserver.close()
        drivingStateObserver.close()
        lastDrivingState = AutomotiveDrivingState.Unknown
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
        notifyEffects(result.effects)
    }

    private fun refreshFromEngine() {
        if (!state.value.canDispatchEngineCommands) {
            logBlockedIntent(BambooPlaybackIntent.Refresh)
            return
        }

        telemetryLogger.debug(
            name = PlaybackTelemetryEvents.ENGINE_SNAPSHOT_REQUESTED,
            attributes = mapOf(BambooPlaybackTelemetryAttributes.INTENT to BambooPlaybackIntent.Refresh.telemetryName)
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
                BambooPlaybackTelemetryAttributes.INTENT to sourceIntent.telemetryName,
                BambooPlaybackTelemetryAttributes.COMMAND_TYPE to commandType
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
        notifyEffects(result.effects)
    }

    private fun dispatchPlatformEvent(intent: BambooPlaybackIntent.PlatformEvent) {
        telemetryLogger.info(
            name = PlaybackTelemetryEvents.PLATFORM_EVENT_DISPATCHED,
            attributes = mapOf(
                BambooPlaybackTelemetryAttributes.INTENT to intent.telemetryName,
                BambooPlaybackTelemetryAttributes.EVENT_TYPE to intent.type
            )
        )

        val result = engine.dispatchPlatformEvent(
            EnginePlatformEvent(
                type = intent.type,
                payload = intent.payload
            )
        )

        updateState { current ->
            current.fromEngineResult(result)
        }
        notifyEffects(result.effects)
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

    private fun notifyEffects(effects: List<EngineEffect>) {
        if (effects.isEmpty()) {
            return
        }

        effectListeners.toList().forEach { listener ->
            listener(effects)
        }
    }

    private fun logBlockedIntent(intent: BambooPlaybackIntent) {
        telemetryLogger.info(
            name = PlaybackTelemetryEvents.INTENT_BLOCKED,
            attributes = mapOf(
                BambooPlaybackTelemetryAttributes.INTENT to intent.telemetryName,
                BambooPlaybackTelemetryAttributes.ENGINE_STATUS to state.value.engineConnection.status.name
            )
        )
    }

    private fun logDrivingStateChanged(previousState: AutomotiveDrivingState, currentState: AutomotiveDrivingState) {
        val attributes = mapOf(
            AutomotiveTelemetryAttributes.PREVIOUS_STATE to previousState.name,
            AutomotiveTelemetryAttributes.CURRENT_STATE to currentState.name
        )
        if (currentState == AutomotiveDrivingState.Unknown) {
            automotiveTelemetryLogger.warning(
                name = AutomotiveTelemetryEvents.DRIVING_STATE_CHANGED,
                attributes = attributes
            )
        } else {
            automotiveTelemetryLogger.info(
                name = AutomotiveTelemetryEvents.DRIVING_STATE_CHANGED,
                attributes = attributes
            )
        }
    }
}

private fun AutomotiveUxRestrictions.toEnginePayload(): String = when {
    source != AutomotiveUxRestrictions.Source.AutomotivePlatform -> EnginePlatformEvent.PAYLOAD_UNKNOWN

    requiresDistractionOptimization || activeRestrictions != AutomotiveUxRestrictions.NO_RESTRICTIONS ->
        EnginePlatformEvent.PAYLOAD_RESTRICTED

    else -> EnginePlatformEvent.PAYLOAD_UNRESTRICTED
}

private fun AutomotiveDrivingState.toEnginePayload(): String = when (this) {
    AutomotiveDrivingState.Parked -> EnginePlatformEvent.PAYLOAD_PARKED
    AutomotiveDrivingState.Idling -> EnginePlatformEvent.PAYLOAD_IDLING
    AutomotiveDrivingState.Moving -> EnginePlatformEvent.PAYLOAD_MOVING
    AutomotiveDrivingState.Unknown -> EnginePlatformEvent.PAYLOAD_UNKNOWN
}

internal object PlaybackTelemetryEvents {
    const val INTENT_RECEIVED = "playback.intent.received"
    const val INTENT_BLOCKED = "playback.intent.blocked"
    const val ENGINE_COMMAND_DISPATCHED = "playback.engine.command.dispatched"
    const val PLATFORM_EVENT_DISPATCHED = "playback.platform_event.dispatched"
    const val ENGINE_SNAPSHOT_REQUESTED = "playback.engine.snapshot.requested"
}

internal object AutomotiveTelemetryEvents {
    const val DRIVING_STATE_CHANGED = "automotive.driving_state.changed"
}

internal object AutomotiveTelemetryAttributes {
    const val PREVIOUS_STATE = "previous_state"
    const val CURRENT_STATE = "current_state"
}

private fun BambooPlaybackState.fromEngineResult(result: EngineDispatchResult): BambooPlaybackState =
    BambooPlaybackStateProjector.fromEngineEvent(
        current = BambooPlaybackStateProjector.fromEngineSnapshot(
            current = this,
            snapshot = result.snapshot
        ),
        event = result.event
    )
