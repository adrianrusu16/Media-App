package com.adrianrusu.pandawave.core.rust.bridge.gateway

import android.os.RemoteException
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryPage
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistReconciliation
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import com.adrianrusu.pandawave.core.testing.RecordingTelemetrySink
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
            relationshipAtEpochMillis = 1_000
        )
        val liked = saved.copy(relationshipId = "liked-1", mediaId = "track-2", title = "Liked")
        val service = RecordingEngineService(
            initialSnapshot = EngineSnapshot.idle(1L).copy(
                savedTracksCount = 1,
                likedTracksCount = 1,
                libraryPendingCount = 1
            ),
            saved = listOf(saved),
            liked = listOf(liked),
            pending = listOf("track-pending")
        )
        val gateway = AidlEngineGateway(FakeEngineServiceConnection(service))

        assertEquals(listOf(saved), gateway.savedTracksPage(0, 10))
        assertEquals(listOf(liked), gateway.likedTracksPage(0, 10))
        assertEquals(listOf("track-pending"), gateway.pendingLibraryTrackIdsPage(0, 10))

        val publicSurface = listOf(EngineLibraryItem::class.java, EngineService::class.java, EngineGateway::class.java)
            .flatMap { type -> type.declaredFields.map { it.name } + type.methods.map { it.name } }
            .joinToString(" ")
            .lowercase()
        for (forbidden in listOf("access_token", "refresh_token", "credential", "canopy")) {
            assertFalse(publicSurface.contains(forbidden), "library transport leaked $forbidden")
        }
    }

    @Test
    fun `history projections round trip through the service gateway without credentials`() {
        val history = EngineHistoryItem(
            historyId = "history-1",
            mediaId = "track-1",
            title = "Recently played",
            artist = "Artist",
            album = "Album",
            artworkUri = "content://pandawave/art/track-1",
            playedAtEpochMillis = 1_000L,
            listenedDurationMillis = 90_000L,
            completionRatio = 0.75F,
            playable = true
        )
        val service = RecordingEngineService(
            initialSnapshot = EngineSnapshot.idle(1L).copy(historyEntriesCount = 1),
            history = listOf(history)
        )
        val gateway = AidlEngineGateway(FakeEngineServiceConnection(service))

        assertEquals(
            com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryPage(0L, listOf(history)),
            gateway.historyPage(0, 10, 0L)
        )

        val publicSurface = listOf(EngineHistoryItem::class.java, EngineService::class.java, EngineGateway::class.java)
            .flatMap { type -> type.declaredFields.map { it.name } + type.methods.map { it.name } }
            .joinToString(" ")
            .lowercase()
        for (forbidden in listOf("access_token", "refresh_token", "credential", "canopy")) {
            assertFalse(publicSurface.contains(forbidden), "history transport leaked $forbidden")
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
            artworkUri = null,
            artworkId = "artwork-1",
            position = 0,
            addedAtEpochMillis = 1_500
        )
        val reconciliation = EnginePlaylistReconciliation(
            playlistId = "playlist-1",
            expectedRevision = 7,
            serverRevision = 8,
            serverMembershipIds = listOf("membership-server"),
            proposedMembershipIds = listOf("membership-local")
        )
        val service = RecordingEngineService(
            initialSnapshot = EngineSnapshot.idle(1L).copy(
                playlistsCount = 1,
                playlistTracksCount = 1,
                hasPlaylistsNextPage = true,
                hasPlaylistTracksNextPage = true,
                hasPlaylistReconciliation = true
            ),
            playlists = listOf(playlist),
            playlistTracks = listOf(track),
            selectedPlaylistId = "playlist-1",
            reconciliation = reconciliation
        )
        val gateway = AidlEngineGateway(FakeEngineServiceConnection(service))

        assertEquals(listOf(playlist), gateway.playlistsPage(0, 10))
        assertEquals(listOf(track), gateway.playlistTracksPage(0, 10))
        assertEquals("playlist-1", gateway.selectedPlaylistId())
        assertEquals(reconciliation, gateway.playlistReconciliation())

        val publicSurface = listOf(
            EnginePlaylistItem::class.java,
            EnginePlaylistTrackItem::class.java,
            EnginePlaylistReconciliation::class.java,
            EngineService::class.java,
            EngineGateway::class.java
        )
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
    fun `dispatch uses service result without snapshot or effect round trips`() {
        val service = RecordingEngineService(
            initialSnapshot = EngineSnapshot.idle(nowMillis = 10L)
        )
        val gateway = AidlEngineGateway(
            connection = FakeEngineServiceConnection(service = service),
            clock = { 1L }
        )
        service.resetReadCounters()

        val result = gateway.dispatch(EngineCommand(type = EngineCommand.TYPE_PLAY, payload = null))

        assertEquals(EngineSnapshot.PLAYBACK_PLAYING, result.snapshot.playbackState)
        assertEquals(
            listOf(
                EngineEffect(type = EngineEffect.TYPE_REQUEST_AUDIO_FOCUS),
                EngineEffect(type = EngineEffect.TYPE_PLAY)
            ),
            result.effects
        )
        assertEquals(0, service.snapshotReads)
        assertEquals(0, service.effectCountReads)
        assertEquals(0, service.effectReads)
    }

    @Test
    fun `gateway state remains accessible while a binder dispatch is blocked`() {
        val dispatchEntered = CountDownLatch(1)
        val releaseDispatch = CountDownLatch(1)
        val service = BlockingDispatchEngineService(dispatchEntered, releaseDispatch)
        val gateway = AidlEngineGateway(FakeEngineServiceConnection(service))
        val dispatchThread = thread {
            gateway.dispatch(EngineCommand(EngineCommand.TYPE_PLAY, null))
        }

        assertTrue(dispatchEntered.await(2, TimeUnit.SECONDS))
        val registrationCompleted = CountDownLatch(1)
        val registrationThread = thread {
            gateway.observeEngineEvents { }.close()
            registrationCompleted.countDown()
        }

        try {
            assertTrue(registrationCompleted.await(1, TimeUnit.SECONDS))
        } finally {
            releaseDispatch.countDown()
            dispatchThread.join(2_000)
            registrationThread.join(2_000)
        }
    }

    @Test
    fun `command queued during reconnect is drained after the reconnect callback`() {
        val service = RecordingEngineService(EngineSnapshot.idle(nowMillis = 1L))
        val connection = InterleavingEngineServiceConnection()
        val gateway = AidlEngineGateway(connection)
        connection.onNextServiceRead = { connection.connectService(service) }

        val result = gateway.dispatch(EngineCommand(EngineCommand.TYPE_PLAY, null))

        assertEquals(EngineEvent.TYPE_COMMAND_QUEUED, result.event.type)
        assertEquals(listOf(EngineCommand.TYPE_PLAY), service.commandTypes)
    }

    @Test
    fun `dispatch cannot enqueue after close wins the service lookup race`() {
        val connection = InterleavingEngineServiceConnection()
        val gateway = AidlEngineGateway(connection)
        connection.onNextServiceRead = gateway::close

        val result = gateway.dispatch(EngineCommand(EngineCommand.TYPE_PLAY, null))

        assertEquals(EngineEvent.TYPE_GATEWAY_UNAVAILABLE, result.event.type)
    }

    @Test
    fun `dispatch becomes unavailable when the binder dies after applying the command`() {
        val service = DeadBinderAfterDispatchService(EngineSnapshot.idle(nowMillis = 10L))
        val connection = FakeEngineServiceConnection(service)
        val gateway = AidlEngineGateway(
            connection = connection,
            clock = { 1L }
        )

        val result = gateway.dispatch(EngineCommand(EngineCommand.TYPE_PLAY, null))

        assertEquals(listOf(EngineCommand(EngineCommand.TYPE_PLAY, null)), service.dispatchedCommands)
        assertEquals(EngineEvent.TYPE_GATEWAY_UNAVAILABLE, result.event.type)
        assertEquals(EngineCommand.TYPE_PLAY, result.event.message)
        assertNull(connection.service)
    }

    @Test
    fun `queued command replay stops when the binder dies after dispatch`() {
        val connection = FakeEngineServiceConnection(service = null)
        val gateway = AidlEngineGateway(connection, clock = { 1L })
        val service = DeadBinderAfterDispatchService(EngineSnapshot.idle(nowMillis = 10L))

        gateway.dispatch(EngineCommand(EngineCommand.TYPE_PLAY, null))
        connection.connectService(service)

        assertEquals(listOf(EngineCommand(EngineCommand.TYPE_PLAY, null)), service.dispatchedCommands)
        assertNull(connection.service)
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
    fun `snapshot observers are serialized across concurrent binder callbacks`() {
        val connection = FakeEngineServiceConnection(service = null)
        val gateway = AidlEngineGateway(connection = connection, clock = { 1L })
        val firstCallbackEntered = CountDownLatch(1)
        val releaseFirstCallback = CountDownLatch(1)
        val activeCallbacks = AtomicInteger(0)
        val maximumActiveCallbacks = AtomicInteger(0)

        gateway.observeSnapshots { snapshot ->
            if (snapshot.updatedAtEpochMillis == 1L) return@observeSnapshots
            val active = activeCallbacks.incrementAndGet()
            maximumActiveCallbacks.accumulateAndGet(active, ::maxOf)
            try {
                if (snapshot.updatedAtEpochMillis == 2L) {
                    firstCallbackEntered.countDown()
                    assertTrue(releaseFirstCallback.await(2, TimeUnit.SECONDS))
                }
            } finally {
                activeCallbacks.decrementAndGet()
            }
        }

        val firstBinderThread = thread {
            connection.pushSnapshot(EngineSnapshot.idle(nowMillis = 2L))
        }
        assertTrue(firstCallbackEntered.await(2, TimeUnit.SECONDS))
        val secondBinderThread = thread {
            connection.pushSnapshot(EngineSnapshot.idle(nowMillis = 3L))
        }
        secondBinderThread.join(500)
        releaseFirstCallback.countDown()
        firstBinderThread.join(2_000)
        secondBinderThread.join(2_000)

        assertEquals(1, maximumActiveCallbacks.get())
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
    fun `delete account is unavailable offline and never replayed`() {
        val connection = FakeEngineServiceConnection(service = null)
        val gateway = AidlEngineGateway(connection = connection, clock = { 25L })
        val service = RecordingEngineService(EngineSnapshot.idle(nowMillis = 100L))

        val result = gateway.dispatch(EngineCommand(EngineCommand.TYPE_DELETE_ACCOUNT, null))
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
            results.map { result -> result.event.type }
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
            EngineCommand(EngineCommand.TYPE_CLEAR_HISTORY, null)
        )

        val commandResults = historyMutations.map(gateway::dispatch)
        val completionResult = gateway.dispatchPlatformEvent(
            EnginePlatformEvent(
                EnginePlatformEvent.TYPE_PLAYBACK_COMPLETED,
                "{\"version\":1,\"track_id\":\"track-1\",\"duration_ms\":1000,\"completion_ratio\":1.0}"
            )
        )
        val service = RecordingEngineService(EngineSnapshot.idle(nowMillis = 100L))
        connection.connectService(service)

        assertEquals(
            List(historyMutations.size) {
                EngineEvent.TYPE_GATEWAY_UNAVAILABLE
            },
            commandResults.map { it.event.type }
        )
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
            EngineCommand(
                EngineCommand.TYPE_UPDATE_PLAYLIST,
                """{"version":1,"playlist_id":"playlist-1","name":"Road trip","expected_revision":7}"""
            ),
            EngineCommand(EngineCommand.TYPE_DELETE_PLAYLIST, """{"version":1,"playlist_id":"playlist-1"}"""),
            EngineCommand(
                EngineCommand.TYPE_ADD_PLAYLIST_TRACK,
                """{"version":1,"playlist_id":"playlist-1","track_id":"track-1"}"""
            ),
            EngineCommand(
                EngineCommand.TYPE_REMOVE_PLAYLIST_TRACK,
                """{"version":1,"playlist_id":"playlist-1","track_id":"track-1"}"""
            ),
            EngineCommand(
                EngineCommand.TYPE_REORDER_PLAYLIST_TRACKS,
                """{"version":1,"playlist_id":"playlist-1","ordered_membership_ids":["membership-1"],"expected_revision":7}"""
            )
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
            "{\"preferences\":{\"theme\":\"dark\"}}"
        )
    )
}

private class FakeEngineServiceConnection(override var service: EngineService?) : EngineServiceConnection {
    private var listener: EngineServiceListener? = null

    override fun connect(listener: EngineServiceListener) {
        this.listener = listener
    }

    override fun invalidate(service: EngineService) {
        if (this.service === service) {
            this.service = null
        }
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

private class InterleavingEngineServiceConnection : EngineServiceConnection {
    private var currentService: EngineService? = null
    private var listener: EngineServiceListener? = null
    var onNextServiceRead: (() -> Unit)? = null

    override val service: EngineService?
        get() {
            val observedService = currentService
            val callback = onNextServiceRead
            onNextServiceRead = null
            callback?.invoke()
            return observedService
        }

    override fun connect(listener: EngineServiceListener) {
        this.listener = listener
    }

    override fun close() {
        currentService = null
        listener = null
    }

    fun connectService(service: EngineService) {
        currentService = service
        listener?.onSnapshotChanged(service.snapshot())
    }
}

private class RecordingEngineService(
    initialSnapshot: EngineSnapshot,
    private val saved: List<EngineLibraryItem> = emptyList(),
    private val liked: List<EngineLibraryItem> = emptyList(),
    private val history: List<EngineHistoryItem> = emptyList(),
    private val pending: List<String> = emptyList(),
    private val playlists: List<EnginePlaylistItem> = emptyList(),
    private val playlistTracks: List<EnginePlaylistTrackItem> = emptyList(),
    private val selectedPlaylistId: String? = null,
    private val reconciliation: EnginePlaylistReconciliation? = null
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
    var snapshotReads = 0
        private set
    var effectCountReads = 0
        private set
    var effectReads = 0
        private set

    fun resetReadCounters() {
        snapshotReads = 0
        effectCountReads = 0
        effectReads = 0
    }

    override fun loginPassword(email: String, password: ByteArray, deviceLabel: String): EngineAuthOperationResult {
        lastPassword = password.decodeToString()
        return EngineAuthOperationResult.authenticated()
    }

    override fun snapshot(): EngineSnapshot {
        snapshotReads += 1
        return currentSnapshot
    }

    override fun savedTracksPage(offset: Int, limit: Int): List<EngineLibraryItem> =
        saved.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))

    override fun likedTracksPage(offset: Int, limit: Int): List<EngineLibraryItem> =
        liked.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))

    override fun historyPage(offset: Int, limit: Int, generation: Long) = EngineHistoryPage(
        generation,
        history.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))
    )

    override fun pendingLibraryTrackIdsPage(offset: Int, limit: Int): List<String> =
        pending.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))

    override fun playlistsPage(offset: Int, limit: Int): List<EnginePlaylistItem> =
        playlists.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))

    override fun playlistTracksPage(offset: Int, limit: Int): List<EnginePlaylistTrackItem> =
        playlistTracks.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))

    override fun selectedPlaylistId(): String? = selectedPlaylistId

    override fun playlistReconciliation(): EnginePlaylistReconciliation? = reconciliation

    override fun effectCount(): Int {
        effectCountReads += 1
        return currentEffects.size
    }

    override fun effect(index: Int): EngineEffect? {
        effectReads += 1
        return currentEffects.getOrNull(index)
    }

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
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
        return EngineDispatchResult(
            snapshot = currentSnapshot,
            event = EngineEvent(type = EngineEvent.TYPE_COMMAND_APPLIED, message = command.type),
            effects = currentEffects
        )
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult {
        platformEvents += event
        currentEffects = emptyList()
        currentSnapshot = currentSnapshot.copy(
            updatedAtEpochMillis = currentSnapshot.updatedAtEpochMillis + 1
        )
        return EngineDispatchResult(
            snapshot = currentSnapshot,
            event = EngineEvent(type = EngineEvent.TYPE_PLATFORM_EVENT_APPLIED, message = event.type),
            effects = currentEffects
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

private class DeadBinderAfterDispatchService(private val initialSnapshot: EngineSnapshot) : EngineService {
    val dispatchedCommands = mutableListOf<EngineCommand>()

    override fun snapshot(): EngineSnapshot {
        if (dispatchedCommands.isNotEmpty()) {
            throw RemoteException("PandaEngine binder died")
        }
        return initialSnapshot
    }

    override fun effectCount(): Int = 0

    override fun effect(index: Int): EngineEffect? = null

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        dispatchedCommands += command
        throw RemoteException("PandaEngine binder died")
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult =
        throw RemoteException("PandaEngine binder died")
}

private class BlockingDispatchEngineService(
    private val dispatchEntered: CountDownLatch,
    private val releaseDispatch: CountDownLatch
) : EngineService {
    private val currentSnapshot = EngineSnapshot.idle(nowMillis = 1L)

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun effectCount(): Int = 0

    override fun effect(index: Int): EngineEffect? = null

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        dispatchEntered.countDown()
        assertTrue(releaseDispatch.await(2, TimeUnit.SECONDS))
        return EngineDispatchResult(
            snapshot = currentSnapshot,
            event = EngineEvent(type = EngineEvent.TYPE_COMMAND_APPLIED, message = command.type)
        )
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult = EngineDispatchResult(
        snapshot = currentSnapshot,
        event = EngineEvent(type = EngineEvent.TYPE_PLATFORM_EVENT_APPLIED, message = event.type)
    )
}
