package com.adrianrusu.pandawave.core.rust.bridge.gateway

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistReconciliation
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem
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
    fun `saved liked and pending library projections round trip without credentials`() {
        val saved = EngineLibraryItem(
            relationshipId = "saved-1",
            mediaId = "track-1",
            title = "Saved",
            artistId = "artist-1",
            artist = "Artist",
            durationMillis = 120_000,
            relationshipAtEpochMillis = 1_000,
        )
        val liked = saved.copy(relationshipId = "liked-1", mediaId = "track-2", title = "Liked")
        val service = RecordingEngineService(
            initialSnapshot = EngineSnapshot.idle(1L).copy(
                savedTracksCount = 1,
                likedTracksCount = 1,
                libraryPendingCount = 1,
            ),
            saved = listOf(saved),
            liked = listOf(liked),
            pending = listOf("track-pending"),
        )
        val gateway = AidlEngineGateway(FakeEngineServiceConnection(service))

        assertEquals(saved, gateway.savedTrack(0))
        assertEquals(liked, gateway.likedTrack(0))
        assertEquals("track-pending", gateway.pendingLibraryTrackId(0))

        val publicSurface = listOf(EngineLibraryItem::class.java, EngineService::class.java, EngineGateway::class.java)
            .flatMap { type -> type.declaredFields.map { it.name } + type.methods.map { it.name } }
            .joinToString(" ")
            .lowercase()
        for (forbidden in listOf("access_token", "refresh_token", "credential", "canopy")) {
            assertFalse(publicSurface.contains(forbidden), "library transport leaked $forbidden")
        }
    }

    @Test
    fun `playlist projections round trip through the service gateway without credentials`() {
        val playlist = EnginePlaylistItem("playlist-1", "Road trip", "Summer", 7, 1_000, 2_000)
        val track = EnginePlaylistTrackItem(
            membershipId = "membership-1",
            playlistId = "playlist-1",
            mediaId = "track-1",
            title = "Track",
            artistId = "artist-1",
            artist = "Artist",
            album = "Album",
            durationMillis = 120_000,
            explicit = false,
            artworkId = "artwork-1",
            position = 0,
            addedAtEpochMillis = 1_500,
        )
        val reconciliation = EnginePlaylistReconciliation(
            playlistId = "playlist-1",
            expectedRevision = 7,
            serverRevision = 8,
            serverMembershipIds = listOf("membership-server"),
            proposedMembershipIds = listOf("membership-local"),
        )
        val service = RecordingEngineService(
            initialSnapshot = EngineSnapshot.idle(1L).copy(
                playlistsCount = 1,
                playlistTracksCount = 1,
                hasPlaylistsNextPage = true,
                hasPlaylistTracksNextPage = true,
                hasPlaylistReconciliation = true,
            ),
            playlists = listOf(playlist),
            playlistTracks = listOf(track),
            selectedPlaylistId = "playlist-1",
            reconciliation = reconciliation,
        )
        val gateway = AidlEngineGateway(FakeEngineServiceConnection(service))

        assertEquals(playlist, gateway.playlist(0))
        assertEquals(track, gateway.playlistTrack(0))
        assertEquals("playlist-1", gateway.selectedPlaylistId())
        assertEquals(reconciliation, gateway.playlistReconciliation())

        val publicSurface = listOf(EnginePlaylistItem::class.java, EnginePlaylistTrackItem::class.java,
            EnginePlaylistReconciliation::class.java, EngineService::class.java, EngineGateway::class.java)
            .flatMap { type -> type.declaredFields.map { it.name } + type.methods.map { it.name } }
            .joinToString(" ")
            .lowercase()
        for (forbidden in listOf("access_token", "refresh_token", "credential", "canopy")) {
            assertFalse(publicSurface.contains(forbidden), "playlist transport leaked $forbidden")
        }
    }

    @Test
    fun `auth availability changes when the service connects without carrying credentials`() {
        val connection = FakeEngineServiceConnection(service = null)
        val gateway = AidlEngineGateway(connection = connection, clock = { 1L })
        val availability = mutableListOf<Boolean>()

        gateway.observeAuthAvailability(availability::add)
        connection.connectService(RecordingEngineService(EngineSnapshot.idle(nowMillis = 2L)))

        assertEquals(listOf(false, true), availability)
        assertEquals(true, gateway.isAuthAvailable)
    }

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

    @Test
    fun `non replayable profile mutation is unavailable and never replayed`() {
        val connection = FakeEngineServiceConnection(service = null)
        val gateway = AidlEngineGateway(connection = connection, clock = { 25L })
        val service = RecordingEngineService(EngineSnapshot.idle(nowMillis = 100L))

        val result = gateway.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_UPDATE_PROFILE,
                payload = """{"version":1,"update_display_name":true,"display_name":"Driver"}"""
            )
        )
        connection.connectService(service)

        assertEquals(EngineEvent.TYPE_GATEWAY_UNAVAILABLE, result.event.type)
        assertEquals(emptyList(), service.commandTypes)
    }

    @Test
    fun `all protected profile mutations are unavailable while disconnected`() {
        val gateway = AidlEngineGateway(FakeEngineServiceConnection(null))

        val results = protectedProfileMutations().map { command -> gateway.dispatch(command) }

        assertEquals(
            List(protectedProfileMutations().size) { EngineEvent.TYPE_GATEWAY_UNAVAILABLE },
            results.map { result -> result.event.type },
        )
    }

    @Test
    fun `protected profile mutations are never replayed after reconnect`() {
        val connection = FakeEngineServiceConnection(null)
        val gateway = AidlEngineGateway(connection)
        protectedProfileMutations().forEach { command -> gateway.dispatch(command) }
        val service = RecordingEngineService(EngineSnapshot.idle(nowMillis = 100L))

        connection.connectService(service)

        assertEquals(emptyList(), service.commandTypes)
    }

    @Test
    fun `history mutations and completion are unavailable and never replayed after reconnect`() {
        val connection = FakeEngineServiceConnection(null)
        val gateway = AidlEngineGateway(connection)
        val historyMutations = listOf(
            EngineCommand(EngineCommand.TYPE_UPDATE_HISTORY_SETTINGS, "{\"version\":1,\"enabled\":false}"),
            EngineCommand(EngineCommand.TYPE_DELETE_HISTORY_ENTRY, "{\"version\":1,\"history_id\":\"history-1\"}"),
            EngineCommand(EngineCommand.TYPE_CLEAR_HISTORY, null),
        )

        val commandResults = historyMutations.map(gateway::dispatch)
        val completionResult = gateway.dispatchPlatformEvent(
            EnginePlatformEvent(
                EnginePlatformEvent.TYPE_PLAYBACK_COMPLETED,
                "{\"version\":1,\"track_id\":\"track-1\",\"duration_ms\":1000,\"completion_ratio\":1.0}",
            ),
        )
        val service = RecordingEngineService(EngineSnapshot.idle(nowMillis = 100L))
        connection.connectService(service)

        assertEquals(List(historyMutations.size) { EngineEvent.TYPE_GATEWAY_UNAVAILABLE }, commandResults.map { it.event.type })
        assertEquals(EngineEvent.TYPE_GATEWAY_UNAVAILABLE, completionResult.event.type)
        assertEquals(emptyList(), service.commandTypes)
        assertEquals(emptyList(), service.platformEventTypes)
    }

    @Test
    fun `library mutations are unavailable and never replayed after reconnect`() {
        val connection = FakeEngineServiceConnection(null)
        val gateway = AidlEngineGateway(connection)
        val mutations = listOf(
            EngineCommand(EngineCommand.TYPE_SAVE_TRACK, """{"version":1,"track_id":"track-1"}"""),
            EngineCommand(EngineCommand.TYPE_REMOVE_SAVED_TRACK, """{"version":1,"track_id":"track-1"}"""),
            EngineCommand(EngineCommand.TYPE_LIKE_TRACK, """{"version":1,"track_id":"track-1"}"""),
            EngineCommand(EngineCommand.TYPE_UNLIKE_TRACK, """{"version":1,"track_id":"track-1"}"""),
            EngineCommand(EngineCommand.TYPE_CREATE_PLAYLIST, """{"version":1,"name":"Road trip"}"""),
            EngineCommand(EngineCommand.TYPE_UPDATE_PLAYLIST, """{"version":1,"playlist_id":"playlist-1","name":"Road trip","expected_revision":7}"""),
            EngineCommand(EngineCommand.TYPE_DELETE_PLAYLIST, """{"version":1,"playlist_id":"playlist-1"}"""),
            EngineCommand(EngineCommand.TYPE_ADD_PLAYLIST_TRACK, """{"version":1,"playlist_id":"playlist-1","track_id":"track-1"}"""),
            EngineCommand(EngineCommand.TYPE_REMOVE_PLAYLIST_TRACK, """{"version":1,"playlist_id":"playlist-1","track_id":"track-1"}"""),
            EngineCommand(EngineCommand.TYPE_REORDER_PLAYLIST_TRACKS, """{"version":1,"playlist_id":"playlist-1","ordered_membership_ids":["membership-1"],"expected_revision":7}"""),
        )

        val results = mutations.map(gateway::dispatch)
        val service = RecordingEngineService(EngineSnapshot.idle(nowMillis = 100L))
        connection.connectService(service)

        assertEquals(List(mutations.size) { EngineEvent.TYPE_GATEWAY_UNAVAILABLE }, results.map { it.event.type })
        assertEquals(emptyList(), service.commandTypes)
    }

    private fun protectedProfileMutations(): List<EngineCommand> = listOf(
        EngineCommand(EngineCommand.TYPE_UPSERT_PROFILE, "{\"display_name\":\"Canopy\"}"),
        EngineCommand(EngineCommand.TYPE_UPDATE_PROFILE, "{\"display_name\":\"Canopy\"}"),
        EngineCommand(EngineCommand.TYPE_DELETE_PROFILE, null),
        EngineCommand(
            EngineCommand.TYPE_UPDATE_PROFILE_PREFERENCES,
            "{\"preferences\":{\"theme\":\"dark\"}}",
        ),
    )
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

private class RecordingEngineService(
    initialSnapshot: EngineSnapshot,
    private val saved: List<EngineLibraryItem> = emptyList(),
    private val liked: List<EngineLibraryItem> = emptyList(),
    private val pending: List<String> = emptyList(),
    private val playlists: List<EnginePlaylistItem> = emptyList(),
    private val playlistTracks: List<EnginePlaylistTrackItem> = emptyList(),
    private val selectedPlaylistId: String? = null,
    private val reconciliation: EnginePlaylistReconciliation? = null,
) : EngineService {
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

    override fun loginPassword(email: String, password: ByteArray, deviceLabel: String): EngineAuthOperationResult {
        lastPassword = password.decodeToString()
        return EngineAuthOperationResult.authenticated()
    }

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun browseResult(index: Int): EngineCatalogItem? = null

    override fun searchResult(index: Int): EngineCatalogItem? = null

    override fun savedTrack(index: Int): EngineLibraryItem? = saved.getOrNull(index)

    override fun likedTrack(index: Int): EngineLibraryItem? = liked.getOrNull(index)

    override fun pendingLibraryTrackId(index: Int): String? = pending.getOrNull(index)

    override fun playlist(index: Int): EnginePlaylistItem? = playlists.getOrNull(index)

    override fun playlistTrack(index: Int): EnginePlaylistTrackItem? = playlistTracks.getOrNull(index)

    override fun selectedPlaylistId(): String? = selectedPlaylistId

    override fun playlistReconciliation(): EnginePlaylistReconciliation? = reconciliation

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
