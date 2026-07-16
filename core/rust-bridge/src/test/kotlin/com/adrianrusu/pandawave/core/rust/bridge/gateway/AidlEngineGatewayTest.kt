package com.adrianrusu.pandawave.core.rust.bridge.gateway

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AidlEngineGatewayTest {
    @Test
    fun `login is never queued and wipes password while disconnected`() {
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(service = null),
            clock = { 25L }
        )
        val password = "super-secret".encodeToByteArray()

        val result = gateway.loginPassword("driver@example.com", password, "PandaWave")

        assertEquals(EngineAuthOperationResult.unavailable(), result)
        assertEquals(List(password.size) { 0.toByte() }, password.toList())
    }

    @Test
    fun `login reaches connected service before password is wiped`() {
        val service = RecordingEngineService(EngineSnapshot.idle(nowMillis = 10L))
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(service = service),
            clock = { 25L }
        )
        val password = "super-secret".encodeToByteArray()

        val result = gateway.loginPassword("driver@example.com", password, "PandaWave")

        assertEquals(EngineAuthOperationResult.authenticated(), result)
        assertEquals("super-secret", service.lastPassword)
        assertEquals(List(password.size) { 0.toByte() }, password.toList())
    }

    @Test
    fun `snapshot returns service snapshot when connected`() {
        val service = RecordingEngineService(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 10L)
        )
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(service = service),
            clock = { 1L }
        )

        assertEquals(EngineSnapshot.idle(nowMillis = 10L), gateway.snapshot())
    }

    @Test
    fun `dispatch sends command and refreshes snapshot when connected`() {
        val service = RecordingEngineService(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 10L)
        )
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(service = service),
            clock = { 1L }
        )

        val result = gateway.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_PLAY,
                payload = null
            )
        )

        assertEquals(listOf(EngineCommand.TYPE_PLAY), service.commandTypes)
        assertEquals(EngineSnapshot.PLAYBACK_PLAYING, result.snapshot.playbackState)
        assertEquals(EngineEvent.TYPE_COMMAND_APPLIED, result.event.type)
    }

    @Test
    fun `dispatch returns service effects when connected`() {
        val service = RecordingEngineService(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 10L)
        )
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(service = service),
            clock = { 1L }
        )

        val result = gateway.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_PLAY,
                payload = null
            )
        )

        assertEquals(
            listOf(
                EngineEffect(type = EngineEffect.TYPE_REQUEST_AUDIO_FOCUS),
                EngineEffect(type = EngineEffect.TYPE_PLAY)
            ),
            result.effects
        )
    }

    @Test
    fun `dispatch platform event sends event and refreshes snapshot when connected`() {
        val service = RecordingEngineService(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 10L)
        )
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(service = service),
            clock = { 1L }
        )

        val result = gateway.dispatchPlatformEvent(
            EnginePlatformEvent(
                type = EnginePlatformEvent.TYPE_SUSPEND_TO_RAM,
                payload = null
            )
        )

        assertEquals(listOf(EnginePlatformEvent.TYPE_SUSPEND_TO_RAM), service.platformEventTypes)
        assertEquals(11L, result.snapshot.updatedAtEpochMillis)
        assertEquals(EngineEvent.TYPE_PLATFORM_EVENT_APPLIED, result.event.type)
        assertEquals(EnginePlatformEvent.TYPE_SUSPEND_TO_RAM, result.event.message)
    }

    @Test
    fun `dispatch telemetry includes status and no payload`() {
        val sink = RecordingTelemetrySink()
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(
                service = RecordingEngineService(
                    initialSnapshot = EngineSnapshot.idle(nowMillis = 10L)
                )
            ),
            telemetryLogger = TelemetryLogger(
                sink = sink,
                clock = { 1L }
            )
        )

        gateway.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_PLAY,
                payload = "artist=secret"
            )
        )

        val event = sink.events.single()
        assertEquals("engine_gateway.command", event.name)
        assertEquals(EngineCommand.TYPE_PLAY, event.attributes["command_type"])
        assertEquals("applied", event.attributes["status"])
        assertEquals("0", event.attributes["pending_count"])
        assertFalse(event.attributes.containsKey("payload"))
    }

    @Test
    fun `dispatch platform event telemetry includes status and no payload`() {
        val sink = RecordingTelemetrySink()
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(
                service = RecordingEngineService(
                    initialSnapshot = EngineSnapshot.idle(nowMillis = 10L)
                )
            ),
            telemetryLogger = TelemetryLogger(
                sink = sink,
                clock = { 1L }
            )
        )

        gateway.dispatchPlatformEvent(
            EnginePlatformEvent(
                type = EnginePlatformEvent.TYPE_UX_RESTRICTIONS_CHANGED,
                payload = "speed=secret"
            )
        )

        val event = sink.events.single()
        assertEquals("engine_gateway.platform_event", event.name)
        assertEquals(EnginePlatformEvent.TYPE_UX_RESTRICTIONS_CHANGED, event.attributes["platform_event_type"])
        assertEquals("applied", event.attributes["status"])
        assertEquals("0", event.attributes["pending_count"])
        assertFalse(event.attributes.containsKey("payload"))
    }

    @Test
    fun `dispatch queues command while disconnected`() {
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(service = null),
            clock = { 25L }
        )

        val result = gateway.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_PLAY,
                payload = null
            )
        )

        assertEquals(EngineSnapshot.idle(nowMillis = 25L), result.snapshot)
        assertEquals(EngineEvent.TYPE_COMMAND_QUEUED, result.event.type)
        assertEquals(EngineCommand.TYPE_PLAY, result.event.message)
    }

    @Test
    fun `dispatch queues platform event while disconnected`() {
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(service = null),
            clock = { 25L }
        )

        val result = gateway.dispatchPlatformEvent(
            EnginePlatformEvent(
                type = EnginePlatformEvent.TYPE_APP_BACKGROUNDED,
                payload = null
            )
        )

        assertEquals(EngineSnapshot.idle(nowMillis = 25L), result.snapshot)
        assertEquals(EngineEvent.TYPE_PLATFORM_EVENT_QUEUED, result.event.type)
        assertEquals(EnginePlatformEvent.TYPE_APP_BACKGROUNDED, result.event.message)
    }

    @Test
    fun `queued commands replay when service connects`() {
        val connection = FakeEngineServiceConnection(service = null)
        val sink = RecordingTelemetrySink()
        val gateway = AidlEngineGateway(
            connection = connection,
            telemetryLogger = TelemetryLogger(
                sink = sink,
                clock = { 25L }
            ),
            clock = { 25L }
        )
        val service = RecordingEngineService(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 100L)
        )

        gateway.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_PLAY,
                payload = null
            )
        )
        gateway.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_PAUSE,
                payload = null
            )
        )
        connection.connectService(service)

        assertEquals(
            listOf(
                EngineCommand.TYPE_PLAY,
                EngineCommand.TYPE_PAUSE
            ),
            service.commandTypes
        )
        assertEquals(EngineSnapshot.PLAYBACK_PAUSED, gateway.snapshot().playbackState)
        assertEquals(
            listOf("queued", "queued", "replayed", "replayed"),
            sink.events.map { event -> event.attributes.getValue("status") }
        )
    }

    @Test
    fun `queued platform events replay when service connects`() {
        val connection = FakeEngineServiceConnection(service = null)
        val sink = RecordingTelemetrySink()
        val gateway = AidlEngineGateway(
            connection = connection,
            telemetryLogger = TelemetryLogger(
                sink = sink,
                clock = { 25L }
            ),
            clock = { 25L }
        )
        val service = RecordingEngineService(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 100L)
        )

        gateway.dispatchPlatformEvent(
            EnginePlatformEvent(
                type = EnginePlatformEvent.TYPE_SUSPEND_TO_RAM,
                payload = null
            )
        )
        gateway.dispatchPlatformEvent(
            EnginePlatformEvent(
                type = EnginePlatformEvent.TYPE_RESUME_FROM_RAM,
                payload = null
            )
        )
        connection.connectService(service)

        assertEquals(
            listOf(
                EnginePlatformEvent.TYPE_SUSPEND_TO_RAM,
                EnginePlatformEvent.TYPE_RESUME_FROM_RAM
            ),
            service.platformEventTypes
        )
        assertEquals(102L, gateway.snapshot().updatedAtEpochMillis)
        assertEquals(
            listOf("queued", "queued", "replayed", "replayed"),
            sink.events.map { event -> event.attributes.getValue("status") }
        )
    }

    @Test
    fun `dispatch returns unavailable event after gateway is closed`() {
        val sink = RecordingTelemetrySink()
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(service = null),
            telemetryLogger = TelemetryLogger(
                sink = sink,
                clock = { 25L }
            ),
            clock = { 25L }
        )

        gateway.close()
        val result = gateway.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_PLAY,
                payload = null
            )
        )

        assertEquals(EngineEvent.TYPE_GATEWAY_UNAVAILABLE, result.event.type)
        assertEquals(EngineCommand.TYPE_PLAY, result.event.message)
        assertEquals("unavailable", sink.events.single().attributes["status"])
    }

    @Test
    fun `dispatch platform event returns unavailable event after gateway is closed`() {
        val sink = RecordingTelemetrySink()
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(service = null),
            telemetryLogger = TelemetryLogger(
                sink = sink,
                clock = { 25L }
            ),
            clock = { 25L }
        )

        gateway.close()
        val result = gateway.dispatchPlatformEvent(
            EnginePlatformEvent(
                type = EnginePlatformEvent.TYPE_APP_FOREGROUNDED,
                payload = null
            )
        )

        assertEquals(EngineEvent.TYPE_GATEWAY_UNAVAILABLE, result.event.type)
        assertEquals(EnginePlatformEvent.TYPE_APP_FOREGROUNDED, result.event.message)
        assertEquals("unavailable", sink.events.single().attributes["status"])
    }

    @Test
    fun `listener snapshot is used while service is unavailable`() {
        val connection = FakeEngineServiceConnection(service = null)
        val gateway = AidlEngineGateway(
            connection = connection,
            clock = { 1L }
        )
        val pushedSnapshot = EngineSnapshot(
            playbackState = EngineSnapshot.PLAYBACK_PAUSED,
            mediaId = "track-1",
            title = "Quiet Cabin",
            artist = "PandaWave",
            userId = null,
            restrictionState = EngineSnapshot.RESTRICTION_UNKNOWN,
            updatedAtEpochMillis = 50L
        )

        connection.pushSnapshot(pushedSnapshot)

        assertEquals(pushedSnapshot, gateway.snapshot())
    }

    @Test
    fun `observers receive listener snapshots`() {
        val connection = FakeEngineServiceConnection(service = null)
        val gateway = AidlEngineGateway(
            connection = connection,
            clock = { 1L }
        )
        val observedSnapshots = mutableListOf<EngineSnapshot>()
        val pushedSnapshot = EngineSnapshot(
            playbackState = EngineSnapshot.PLAYBACK_PLAYING,
            mediaId = "track-1",
            title = "Quiet Cabin",
            artist = "PandaWave",
            userId = null,
            restrictionState = EngineSnapshot.RESTRICTION_UNKNOWN,
            updatedAtEpochMillis = 50L
        )

        gateway.observeSnapshots { snapshot ->
            observedSnapshots += snapshot
        }
        connection.pushSnapshot(pushedSnapshot)

        assertEquals(
            listOf(
                EngineSnapshot.idle(nowMillis = 1L),
                pushedSnapshot
            ),
            observedSnapshots
        )
    }

    @Test
    fun `observers receive engine events`() {
        val connection = FakeEngineServiceConnection(service = null)
        val gateway = AidlEngineGateway(
            connection = connection,
            clock = { 1L }
        )
        val observedEvents = mutableListOf<EngineEvent>()
        val event = EngineEvent(
            type = EngineEvent.TYPE_LISTENER_REGISTERED,
            message = "registered"
        )

        gateway.observeEngineEvents { engineEvent ->
            observedEvents += engineEvent
        }
        connection.pushEvent(event)

        assertEquals(listOf(event), observedEvents)
    }

    @Test
    fun `engine event telemetry includes type and no message`() {
        val connection = FakeEngineServiceConnection(service = null)
        val sink = RecordingTelemetrySink()
        val gateway = AidlEngineGateway(
            connection = connection,
            telemetryLogger = TelemetryLogger(
                sink = sink,
                clock = { 1L }
            ),
            clock = { 1L }
        )

        gateway.observeEngineEvents { }
        connection.pushEvent(
            EngineEvent(
                type = EngineEvent.TYPE_COMMAND_APPLIED,
                message = "token=secret"
            )
        )

        val event = sink.events.single()
        assertEquals("engine_gateway.event", event.name)
        assertEquals(EngineEvent.TYPE_COMMAND_APPLIED, event.attributes["event_type"])
        assertEquals("true", event.attributes["message_present"])
        assertFalse(event.attributes.containsKey("message"))
        assertEquals(TelemetryModule.RustBridge, event.module)
    }
}

private class FakeEngineServiceConnection(override var service: EngineService?) : EngineServiceConnection {
    private var listener: EngineServiceListener? = null

    override fun connect(listener: EngineServiceListener) {
        this.listener = listener
    }

    override fun close() {
        service = null
        listener = null
    }

    fun pushSnapshot(snapshot: EngineSnapshot) {
        listener?.onSnapshotChanged(snapshot)
    }

    fun pushEvent(event: EngineEvent) {
        listener?.onEngineEvent(event)
    }

    fun connectService(service: EngineService) {
        this.service = service
        pushSnapshot(service.snapshot())
    }
}

private class RecordingEngineService(initialSnapshot: EngineSnapshot) : EngineService {
    private var currentSnapshot = initialSnapshot
    private var currentEffects: List<EngineEffect> = emptyList()
    private val commands = mutableListOf<EngineCommand>()
    private val platformEvents = mutableListOf<EnginePlatformEvent>()

    val commandTypes: List<String>
        get() = commands.map { it.type }

    val platformEventTypes: List<String>
        get() = platformEvents.map { it.type }

    var lastPassword: String? = null
        private set

    override fun loginPassword(
        email: String,
        password: ByteArray,
        deviceLabel: String
    ): EngineAuthOperationResult {
        lastPassword = password.decodeToString()
        return EngineAuthOperationResult.authenticated()
    }

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun browseResult(index: Int): EngineCatalogItem? = null

    override fun searchResult(index: Int): EngineCatalogItem? = null

    override fun effectCount(): Int = currentEffects.size

    override fun effect(index: Int): EngineEffect? = currentEffects.getOrNull(index)

    override fun dispatch(command: EngineCommand) {
        commands += command
        currentEffects = effectsFor(command)
        currentSnapshot = when (command.type) {
            EngineCommand.TYPE_PLAY -> currentSnapshot.copy(
                playbackState = EngineSnapshot.PLAYBACK_PLAYING,
                updatedAtEpochMillis = currentSnapshot.updatedAtEpochMillis + 1
            )

            EngineCommand.TYPE_PAUSE -> currentSnapshot.copy(
                playbackState = EngineSnapshot.PLAYBACK_PAUSED,
                updatedAtEpochMillis = currentSnapshot.updatedAtEpochMillis + 1
            )

            else -> currentSnapshot
        }
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent) {
        platformEvents += event
        currentEffects = emptyList()
        currentSnapshot = currentSnapshot.copy(
            updatedAtEpochMillis = currentSnapshot.updatedAtEpochMillis + 1
        )
    }

    private fun effectsFor(command: EngineCommand): List<EngineEffect> = when (command.type) {
        EngineCommand.TYPE_PLAY -> listOf(
            EngineEffect(type = EngineEffect.TYPE_REQUEST_AUDIO_FOCUS),
            EngineEffect(type = EngineEffect.TYPE_PLAY)
        )

        EngineCommand.TYPE_PAUSE -> listOf(EngineEffect(type = EngineEffect.TYPE_PAUSE))

        else -> emptyList()
    }
}

private class RecordingTelemetrySink : TelemetrySink {
    val events = mutableListOf<TelemetryEvent>()

    override fun record(event: TelemetryEvent) {
        events += event
    }
}
