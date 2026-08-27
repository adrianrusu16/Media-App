package com.adrianrusu.pandawave.core.playback

import com.adrianrusu.pandawave.core.automotive.driving.AutomotiveDrivingState
import com.adrianrusu.pandawave.core.automotive.driving.AutomotiveDrivingStateObserver
import com.adrianrusu.pandawave.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.pandawave.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import com.adrianrusu.pandawave.core.telemetry.TelemetrySeverity
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import com.adrianrusu.pandawave.core.testing.RecordingTelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultBambooPlaybackRepositoryTest {
    @Test
    fun `safety events bypass readiness and remain unknown until engine confirmation`() {
        val engine = RecordingEngineGateway(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 1L),
            dispatchEventType = EngineEvent.TYPE_COMMAND_QUEUED,
            platformDispatchEventType = EngineEvent.TYPE_PLATFORM_EVENT_QUEUED
        )
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                AutomotiveUxRestrictions.unrestricted(AutomotiveUxRestrictions.Source.AutomotivePlatform)
            ),
            telemetryLogger = testTelemetryLogger(),
            drivingStateObserver = FakeDrivingStateObserver(AutomotiveDrivingState.Parked)
        )

        repository.start()

        assertEquals(
            listOf(
                EnginePlatformEvent.TYPE_UX_RESTRICTIONS_CHANGED,
                EnginePlatformEvent.TYPE_VEHICLE_DRIVING_STATE_CHANGED
            ),
            engine.platformEvents.map { it.type }
        )
        assertFalse(repository.state.value.vehicleSafety.isParked)

        engine.pushSnapshot(
            EngineSnapshot.idle(nowMillis = 2L).copy(
                restrictionState = EngineSnapshot.RESTRICTION_UNRESTRICTED,
                drivingState = EngineSnapshot.DRIVING_PARKED
            )
        )

        assertTrue(repository.state.value.vehicleSafety.isParked)
        assertTrue(repository.state.value.vehicleSafety.isUxUnrestricted)
    }

    @Test
    fun `driving changes are deduplicated logged and dispatched to the engine`() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val drivingObserver = FakeDrivingStateObserver(AutomotiveDrivingState.Parked)
        val telemetrySink = RecordingTelemetrySink()
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                AutomotiveUxRestrictions.unrestricted(AutomotiveUxRestrictions.Source.AutomotivePlatform)
            ),
            telemetryLogger = testTelemetryLogger(telemetrySink),
            drivingStateObserver = drivingObserver
        )

        repository.start()
        drivingObserver.emit(AutomotiveDrivingState.Parked)
        drivingObserver.emit(AutomotiveDrivingState.Moving)
        drivingObserver.emit(AutomotiveDrivingState.Moving)
        drivingObserver.emit(AutomotiveDrivingState.Unknown)

        val drivingEvents = engine.platformEvents.filter {
            it.type == EnginePlatformEvent.TYPE_VEHICLE_DRIVING_STATE_CHANGED
        }
        assertEquals(
            listOf(
                EnginePlatformEvent.PAYLOAD_PARKED,
                EnginePlatformEvent.PAYLOAD_MOVING,
                EnginePlatformEvent.PAYLOAD_UNKNOWN
            ),
            drivingEvents.map { it.payload }
        )

        val telemetryEvents = telemetrySink.events.filter {
            it.name == AutomotiveTelemetryEvents.DRIVING_STATE_CHANGED
        }
        assertEquals(3, telemetryEvents.size)
        assertEquals(setOf(TelemetryModule.Automotive), telemetryEvents.mapTo(mutableSetOf()) { it.module })
        assertEquals(
            AutomotiveDrivingState.Moving.name,
            telemetryEvents.last().attributes[AutomotiveTelemetryAttributes.PREVIOUS_STATE]
        )
        assertEquals(
            AutomotiveDrivingState.Unknown.name,
            telemetryEvents.last().attributes[AutomotiveTelemetryAttributes.CURRENT_STATE]
        )
        assertEquals(TelemetrySeverity.Warning, telemetryEvents.last().severity)
    }

    @Test
    fun `start bootstraps engine snapshot and restriction state`() {
        val repository = DefaultBambooPlaybackRepository(
            engine = RecordingEngineGateway(
                initialSnapshot = EngineSnapshot(
                    playbackState = EngineSnapshot.PLAYBACK_PAUSED,
                    mediaId = "track-1",
                    title = "Quiet Cabin",
                    artist = "PandaWave",
                    sourceUri = "https://cdn.pandawave.test/audio/track-1.mp3",
                    mimeType = "audio/mpeg",
                    userId = null,
                    restrictionState = EngineSnapshot.RESTRICTION_UNKNOWN,
                    updatedAtEpochMillis = 100L
                )
            ),
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()

        assertEquals("Quiet Cabin", repository.state.value.title)
        assertEquals("PandaWave", repository.state.value.artist)
        assertEquals("https://cdn.pandawave.test/audio/track-1.mp3", repository.state.value.sourceUri)
        assertEquals("audio/mpeg", repository.state.value.mimeType)
        assertEquals(BambooPlaybackStatus.Paused, repository.state.value.playbackStatus)
        assertEquals(BambooEngineConnectionUiState.Ready, repository.state.value.engineConnection)
        assertFalse(repository.state.value.restriction.isRestricted)
    }

    @Test
    fun `start opens a guest engine session after bootstrap`() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )
        val effects = mutableListOf<List<EngineEffect>>()

        repository.observeEffects { emittedEffects -> effects += emittedEffects }
        repository.start()

        assertEquals(STARTUP_COMMAND_TYPES, engine.commands.map { it.type })
        assertEquals(EngineCommandPayloads.DEFAULT_SESSION_USER_ID, engine.commands[1].payload)
        assertTrue(repository.state.value.hasActiveSession)
        assertEquals(
            listOf(listOf(EngineEffect(type = EngineEffect.TYPE_SESSION_STARTED))),
            effects
        )
    }

    @Test
    fun `queued bootstrap keeps engine commands disabled`() {
        val engine = RecordingEngineGateway(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 1L),
            dispatchEventType = EngineEvent.TYPE_COMMAND_QUEUED
        )
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        repository.dispatch(BambooPlaybackIntent.TogglePlayback)

        assertFalse(repository.state.value.canDispatchEngineCommands)
        assertEquals(
            STARTUP_COMMAND_TYPES,
            engine.commands.map { it.type }
        )
    }

    @Test
    fun `ready engine allows playback and skip commands`() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        repository.dispatch(BambooPlaybackIntent.TogglePlayback)
        repository.dispatch(BambooPlaybackIntent.SkipNext)

        assertTrue(repository.state.value.canDispatchEngineCommands)
        assertEquals(
            STARTUP_COMMAND_TYPES + listOf(
                EngineCommand.TYPE_PLAY,
                EngineCommand.TYPE_SKIP_NEXT
            ),
            engine.commands.map { it.type }
        )
        assertEquals(BambooPlaybackStatus.Playing, repository.state.value.playbackStatus)
    }

    @Test
    fun `ready engine allows explicit play pause commands`() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        repository.dispatch(BambooPlaybackIntent.Play)
        repository.dispatch(BambooPlaybackIntent.Pause)

        assertEquals(
            STARTUP_COMMAND_TYPES + listOf(
                EngineCommand.TYPE_PLAY,
                EngineCommand.TYPE_PAUSE
            ),
            engine.commands.map { it.type }
        )
        assertEquals(BambooPlaybackStatus.Paused, repository.state.value.playbackStatus)
    }

    @Test
    fun `applied commands publish engine effects`() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )
        val effects = mutableListOf<List<EngineEffect>>()

        repository.observeEffects { emittedEffects -> effects += emittedEffects }
        repository.start()
        repository.dispatch(BambooPlaybackIntent.Play)

        assertEquals(
            listOf(
                listOf(EngineEffect(type = EngineEffect.TYPE_SESSION_STARTED)),
                listOf(
                    EngineEffect(type = EngineEffect.TYPE_REQUEST_AUDIO_FOCUS),
                    EngineEffect(type = EngineEffect.TYPE_PLAY)
                )
            ),
            effects
        )
    }

    @Test
    fun `applied command effects are replayed to the first late effect observer`() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        repository.dispatch(BambooPlaybackIntent.Play)

        val firstLateObserverEffects = mutableListOf<List<EngineEffect>>()
        repository.observeEffects { emittedEffects -> firstLateObserverEffects += emittedEffects }

        assertEquals(
            listOf(
                listOf(
                    EngineEffect(type = EngineEffect.TYPE_REQUEST_AUDIO_FOCUS),
                    EngineEffect(type = EngineEffect.TYPE_PLAY)
                )
            ),
            firstLateObserverEffects
        )

        val secondLateObserverEffects = mutableListOf<List<EngineEffect>>()
        repository.observeEffects { emittedEffects -> secondLateObserverEffects += emittedEffects }

        assertEquals(emptyList(), secondLateObserverEffects)
    }

    @Test
    fun `ready engine allows seek and speed commands with typed payloads`() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        repository.dispatch(BambooPlaybackIntent.SeekTo(positionMillis = 12_345L))
        repository.dispatch(BambooPlaybackIntent.SetSpeed(speed = 1.25F))

        assertEquals(
            STARTUP_COMMAND_TYPES + listOf(
                EngineCommand.TYPE_SEEK,
                EngineCommand.TYPE_SET_SPEED
            ),
            engine.commands.map { it.type }
        )
        assertEquals(EngineCommandPayloads.seekPositionMillis(12_345L), engine.commands[2].payload)
        assertEquals(EngineCommandPayloads.playbackSpeed(1.25F), engine.commands[3].payload)
    }

    @Test
    fun `ready engine allows catalog browse and search commands with typed payloads`() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        repository.dispatch(
            BambooPlaybackIntent.BrowseCatalog(parentId = EngineCommandPayloads.DEFAULT_BROWSE_PARENT_ID)
        )
        repository.dispatch(BambooPlaybackIntent.SearchCatalog(query = "Rust"))
        repository.dispatch(BambooPlaybackIntent.LoadNextCatalogPage(operationId = "catalog-1"))

        assertEquals(
            STARTUP_COMMAND_TYPES + listOf(
                EngineCommand.TYPE_BROWSE,
                EngineCommand.TYPE_SEARCH,
                EngineCommand.TYPE_LOAD_NEXT_CATALOG_PAGE
            ),
            engine.commands.map { it.type }
        )
        assertEquals(
            EngineCommandPayloads.browseParentId(EngineCommandPayloads.DEFAULT_BROWSE_PARENT_ID),
            engine.commands[2].payload
        )
        assertEquals(EngineCommandPayloads.searchQuery("Rust"), engine.commands[3].payload)
        assertEquals(
            EngineCommandPayloads.loadNextCatalogPage("catalog-1"),
            engine.commands[4].payload
        )
    }

    @Test
    fun `ready engine allows play media command with typed payload`() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        repository.dispatch(BambooPlaybackIntent.PlayMedia(mediaId = " track-1 "))

        assertEquals(
            STARTUP_COMMAND_TYPES + listOf(
                EngineCommand.TYPE_PLAY_MEDIA_BY_ID
            ),
            engine.commands.map { it.type }
        )
        assertEquals(EngineCommandPayloads.mediaId(" track-1 "), engine.commands[2].payload)
    }

    @Test
    fun `play from context with siblings becomes an engine play queue`() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        repository.dispatch(
            BambooPlaybackIntent.PlayFromContext(
                context = PandaPlaybackContext.ForYou,
                selectedMediaId = "b",
                mediaIds = listOf("a", "b", "c")
            )
        )

        assertEquals(
            STARTUP_COMMAND_TYPES + listOf(EngineCommand.TYPE_PLAY_QUEUE),
            engine.commands.map { it.type }
        )
        assertEquals(EngineCommandPayloads.playQueue(listOf("a", "b", "c"), startIndex = 1), engine.commands[2].payload)
    }

    @Test
    fun `engine events update connection state and gate commands`() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        engine.pushEvent(EngineEvent(type = EngineEvent.TYPE_SERVICE_BINDING_DIED, message = null))
        repository.dispatch(BambooPlaybackIntent.TogglePlayback)

        assertEquals(BambooEngineConnectionUiState.Reconnecting, repository.state.value.engineConnection)
        assertEquals(
            STARTUP_COMMAND_TYPES,
            engine.commands.map { it.type }
        )

        engine.pushEvent(EngineEvent(type = EngineEvent.TYPE_SERVICE_CONNECTED, message = null))
        repository.dispatch(BambooPlaybackIntent.TogglePlayback)

        assertEquals(BambooEngineConnectionUiState.Ready, repository.state.value.engineConnection)
        assertEquals(
            STARTUP_COMMAND_TYPES + listOf(
                EngineCommand.TYPE_PLAY
            ),
            engine.commands.map { it.type }
        )
    }

    @Test
    fun `start and close are reference counted`() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val observer = FakeUxRestrictionObserver(
            restrictions = AutomotiveUxRestrictions.unrestricted(
                AutomotiveUxRestrictions.Source.NotAutomotive
            )
        )
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = observer,
            telemetryLogger = testTelemetryLogger()
        )

        repository.start()
        repository.start()
        repository.close()
        engine.pushSnapshot(
            EngineSnapshot(
                playbackState = EngineSnapshot.PLAYBACK_PLAYING,
                mediaId = "track-1",
                title = "Still observed",
                artist = "PandaWave",
                userId = null,
                restrictionState = EngineSnapshot.RESTRICTION_UNKNOWN,
                updatedAtEpochMillis = 2L
            )
        )

        assertEquals("Still observed", repository.state.value.title)
        assertEquals(0, observer.closeCount)

        repository.close()
        engine.pushSnapshot(
            EngineSnapshot(
                playbackState = EngineSnapshot.PLAYBACK_PAUSED,
                mediaId = "track-2",
                title = "Not observed",
                artist = "PandaWave",
                userId = null,
                restrictionState = EngineSnapshot.RESTRICTION_UNKNOWN,
                updatedAtEpochMillis = 3L
            )
        )

        assertEquals("Still observed", repository.state.value.title)
        assertEquals(1, observer.closeCount)
        assertEquals(
            STARTUP_COMMAND_TYPES + listOf(EngineCommand.TYPE_END_SESSION),
            engine.commands.map { it.type }
        )
    }

    @Test
    fun `close ends engine session only after final reference is released`() {
        val engine = RecordingEngineGateway(initialSnapshot = EngineSnapshot.idle(nowMillis = 1L))
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger()
        )
        val effects = mutableListOf<List<EngineEffect>>()

        repository.observeEffects { emittedEffects -> effects += emittedEffects }
        repository.start()
        repository.start()
        repository.close()

        assertEquals(STARTUP_COMMAND_TYPES, engine.commands.map { it.type })
        assertTrue(repository.state.value.hasActiveSession)

        repository.close()

        assertEquals(
            STARTUP_COMMAND_TYPES + listOf(EngineCommand.TYPE_END_SESSION),
            engine.commands.map { it.type }
        )
        assertFalse(repository.state.value.hasActiveSession)
        assertEquals(
            listOf(
                listOf(EngineEffect(type = EngineEffect.TYPE_SESSION_STARTED)),
                listOf(EngineEffect(type = EngineEffect.TYPE_SESSION_ENDED))
            ),
            effects
        )
    }

    @Test
    fun `telemetry records received blocked and dispatched playback intents`() {
        val telemetrySink = RecordingTelemetrySink()
        val engine = RecordingEngineGateway(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 1L),
            dispatchEventType = EngineEvent.TYPE_COMMAND_QUEUED
        )
        val repository = DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = FakeUxRestrictionObserver(
                restrictions = AutomotiveUxRestrictions.unrestricted(
                    AutomotiveUxRestrictions.Source.NotAutomotive
                )
            ),
            telemetryLogger = testTelemetryLogger(telemetrySink)
        )

        repository.start()
        repository.dispatch(BambooPlaybackIntent.Play)
        engine.pushEvent(EngineEvent(type = EngineEvent.TYPE_SERVICE_CONNECTED, message = null))
        repository.dispatch(BambooPlaybackIntent.SkipNext)

        val intentEvents = telemetrySink.events.filterNot { event ->
            event.name == PlaybackTelemetryEvents.PLATFORM_EVENT_DISPATCHED
        }
        assertEquals(
            listOf(
                PlaybackTelemetryEvents.INTENT_RECEIVED,
                PlaybackTelemetryEvents.INTENT_BLOCKED,
                PlaybackTelemetryEvents.INTENT_RECEIVED,
                PlaybackTelemetryEvents.ENGINE_COMMAND_DISPATCHED
            ),
            intentEvents.map { it.name }
        )
        assertEquals(
            BambooPlaybackIntentNames.PLAY,
            intentEvents[0].attributes[BambooPlaybackTelemetryAttributes.INTENT]
        )
        assertEquals(
            BambooEngineConnectionStatus.Connecting.name,
            intentEvents[1].attributes[BambooPlaybackTelemetryAttributes.ENGINE_STATUS]
        )
        assertEquals(
            BambooPlaybackIntentNames.SKIP_NEXT,
            intentEvents[3].attributes[BambooPlaybackTelemetryAttributes.INTENT]
        )
        assertEquals(
            EngineCommand.TYPE_SKIP_NEXT,
            intentEvents[3].attributes[BambooPlaybackTelemetryAttributes.COMMAND_TYPE]
        )
        assertEquals(
            setOf(TelemetryModule.Playback),
            telemetrySink.events.mapTo(mutableSetOf()) { it.module }
        )
    }
}

private class FakeUxRestrictionObserver(private val restrictions: AutomotiveUxRestrictions) :
    AutomotiveUxRestrictionObserver {
    var closeCount = 0

    override fun current(): AutomotiveUxRestrictions = restrictions

    override fun start(onChanged: (AutomotiveUxRestrictions) -> Unit) {
        onChanged(restrictions)
    }

    override fun close() {
        closeCount += 1
    }
}

private class FakeDrivingStateObserver(private val drivingState: AutomotiveDrivingState) :
    AutomotiveDrivingStateObserver {
    private var listener: ((AutomotiveDrivingState) -> Unit)? = null

    override fun current(): AutomotiveDrivingState = drivingState

    override fun start(onChanged: (AutomotiveDrivingState) -> Unit) {
        listener = onChanged
        onChanged(drivingState)
    }

    fun emit(state: AutomotiveDrivingState) {
        listener?.invoke(state)
    }

    override fun close() {
        listener = null
    }
}

private class RecordingEngineGateway(
    initialSnapshot: EngineSnapshot,
    private val dispatchEventType: String = EngineEvent.TYPE_COMMAND_APPLIED,
    private val platformDispatchEventType: String = if (dispatchEventType == EngineEvent.TYPE_COMMAND_QUEUED) {
        EngineEvent.TYPE_PLATFORM_EVENT_QUEUED
    } else {
        EngineEvent.TYPE_PLATFORM_EVENT_APPLIED
    }
) : EngineGateway {
    private var currentSnapshot = initialSnapshot
    private val snapshotListeners = mutableSetOf<(EngineSnapshot) -> Unit>()
    private val eventListeners = mutableSetOf<(EngineEvent) -> Unit>()

    val commands = mutableListOf<EngineCommand>()
    val platformEvents = mutableListOf<EnginePlatformEvent>()

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commands += command
        currentSnapshot = when (command.type) {
            EngineCommand.TYPE_PLAY -> currentSnapshot.copy(
                playbackState = EngineSnapshot.PLAYBACK_PLAYING,
                updatedAtEpochMillis = currentSnapshot.updatedAtEpochMillis + 1
            )

            EngineCommand.TYPE_PAUSE -> currentSnapshot.copy(
                playbackState = EngineSnapshot.PLAYBACK_PAUSED,
                updatedAtEpochMillis = currentSnapshot.updatedAtEpochMillis + 1
            )

            EngineCommand.TYPE_START_SESSION -> currentSnapshot.copy(
                hasActiveSession = true,
                updatedAtEpochMillis = currentSnapshot.updatedAtEpochMillis + 1
            )

            EngineCommand.TYPE_END_SESSION -> currentSnapshot.copy(
                hasActiveSession = false,
                updatedAtEpochMillis = currentSnapshot.updatedAtEpochMillis + 1
            )

            else -> currentSnapshot
        }

        return EngineDispatchResult(
            snapshot = currentSnapshot,
            event = EngineEvent(
                type = dispatchEventType,
                message = command.type
            ),
            effects = effectsFor(command)
        )
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult {
        platformEvents += event
        if (platformDispatchEventType == EngineEvent.TYPE_PLATFORM_EVENT_APPLIED) {
            currentSnapshot = when (event.type) {
                EnginePlatformEvent.TYPE_UX_RESTRICTIONS_CHANGED -> currentSnapshot.copy(
                    restrictionState = event.payload ?: EngineSnapshot.RESTRICTION_UNKNOWN
                )

                EnginePlatformEvent.TYPE_VEHICLE_DRIVING_STATE_CHANGED -> currentSnapshot.copy(
                    drivingState = event.payload ?: EngineSnapshot.DRIVING_UNKNOWN
                )

                else -> currentSnapshot
            }
        }
        return EngineDispatchResult(
            snapshot = currentSnapshot,
            event = EngineEvent(
                type = platformDispatchEventType,
                message = event.type
            )
        )
    }

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        snapshotListeners += listener
        listener(currentSnapshot)

        return AutoCloseable {
            snapshotListeners -= listener
        }
    }

    override fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable {
        eventListeners += listener

        return AutoCloseable {
            eventListeners -= listener
        }
    }

    private fun effectsFor(command: EngineCommand): List<EngineEffect> = when (command.type) {
        EngineCommand.TYPE_PLAY -> listOf(
            EngineEffect(type = EngineEffect.TYPE_REQUEST_AUDIO_FOCUS),
            EngineEffect(type = EngineEffect.TYPE_PLAY)
        )

        EngineCommand.TYPE_PAUSE -> listOf(EngineEffect(type = EngineEffect.TYPE_PAUSE))

        EngineCommand.TYPE_START_SESSION -> listOf(EngineEffect(type = EngineEffect.TYPE_SESSION_STARTED))

        EngineCommand.TYPE_END_SESSION -> listOf(EngineEffect(type = EngineEffect.TYPE_SESSION_ENDED))

        EngineCommand.TYPE_SEEK -> listOf(
            EngineEffect(
                type = EngineEffect.TYPE_SEEK,
                positionMillis = EngineCommandPayloads.parseSeekPositionMillis(command.payload)
            )
        )

        else -> emptyList()
    }

    fun pushSnapshot(snapshot: EngineSnapshot) {
        currentSnapshot = snapshot
        snapshotListeners.toList().forEach { listener ->
            listener(snapshot)
        }
    }

    fun pushEvent(event: EngineEvent) {
        eventListeners.toList().forEach { listener ->
            listener(event)
        }
    }
}

private fun testTelemetryLogger(sink: TelemetrySink = TelemetrySink { }): TelemetryLogger = TelemetryLogger(
    sink = sink,
    clock = { 42L }
)

private val STARTUP_COMMAND_TYPES = listOf(
    EngineCommand.TYPE_BOOTSTRAP,
    EngineCommand.TYPE_START_SESSION
)
