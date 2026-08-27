package com.adrianrusu.pandawave.feature.library.data

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAccount
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthSession
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
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
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import com.adrianrusu.pandawave.feature.library.domain.LibraryTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PandaEngineLibraryRepositoryTest {
    @Test
    fun `start loads both user reachable collections and projects bounded items`() {
        val gateway = RecordingLibraryGateway(
            snapshot = authenticatedSnapshot(
                savedTracksCount = 2,
                likedTracksCount = 1,
                hasSavedTracksNextPage = true
            ),
            saved = listOf(item("saved-1"), item("saved-2")),
            liked = listOf(item("liked-1"))
        )
        val repository = testLibraryRepository(gateway)

        repository.start()

        assertEquals(
            listOf(
                EngineCommand.TYPE_LIST_SAVED_TRACKS,
                EngineCommand.TYPE_LIST_LIKED_TRACKS,
                EngineCommand.TYPE_LOAD_HISTORY_SETTINGS,
                EngineCommand.TYPE_LIST_HISTORY,
                EngineCommand.TYPE_LIST_PLAYLISTS
            ),
            gateway.commands.map(EngineCommand::type)
        )
        assertEquals(listOf("saved-1", "saved-2"), repository.state.value.savedTracks.map { it.mediaId })
        assertEquals(listOf("liked-1"), repository.state.value.likedTracks.map { it.mediaId })
        assertTrue(repository.state.value.hasSavedNextPage)
        assertFalse(repository.state.value.hasLikedNextPage)
    }

    @Test
    fun `pagination is engine owned and never exposes a continuation token`() {
        val gateway = RecordingLibraryGateway(authenticatedSnapshot())
        val repository = testLibraryRepository(gateway)
        repository.start()
        gateway.commands.clear()

        repository.loadNext(LibraryTab.SAVED)
        repository.loadNext(LibraryTab.LIKED)
        repository.loadNext(LibraryTab.HISTORY)

        assertEquals(
            listOf(
                EngineCommand.TYPE_LOAD_NEXT_SAVED_TRACKS_PAGE,
                EngineCommand.TYPE_LOAD_NEXT_LIKED_TRACKS_PAGE,
                EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE
            ),
            gateway.commands.map(EngineCommand::type)
        )
        assertEquals(listOf(null, null, null), gateway.commands.map(EngineCommand::payload))
    }

    @Test
    fun `history projection appends pages and refreshes once when the engine generation changes`() {
        val telemetrySink = RecordingLibraryTelemetrySink()
        val gateway = RecordingLibraryGateway(
            snapshot = authenticatedSnapshot(historyEntriesCount = 1, hasHistoryNextPage = true),
            history = listOf(
                EngineHistoryItem(
                    historyId = "history-1", mediaId = "track-1", title = "First",
                    artist = "Artist", album = null, artworkUri = "art-1",
                    playedAtEpochMillis = 1_000, listenedDurationMillis = 90_000,
                    completionRatio = 0.8F, playable = true
                ),
                EngineHistoryItem(
                    historyId = "history-2", mediaId = "track-2", title = "Second",
                    artist = "Artist", album = null, artworkUri = null,
                    playedAtEpochMillis = 2_000, listenedDurationMillis = 120_000,
                    completionRatio = 1F, playable = true
                )
            )
        )
        val repository = PandaEngineLibraryRepository(
            gateway,
            TelemetryLogger(sink = telemetrySink, clock = { 42L })
        )
        repository.start()

        assertEquals(listOf("history-1"), repository.state.value.historyEntries.map { it.historyId })

        gateway.commands.clear()
        repository.loadNext(LibraryTab.HISTORY)

        assertEquals(listOf(EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE), gateway.commands.map(EngineCommand::type))
        assertEquals(listOf("history-1", "history-2"), repository.state.value.historyEntries.map { it.historyId })

        gateway.commands.clear()
        gateway.emit(authenticatedSnapshot(historyGeneration = 2))

        assertEquals(listOf(EngineCommand.TYPE_LIST_HISTORY), gateway.commands.map(EngineCommand::type))
        assertEquals(listOf("history-1"), repository.state.value.historyEntries.map { it.historyId })
        assertEquals(
            listOf(
                mapOf(
                    "previous_generation" to "0",
                    "current_generation" to "2",
                    "reason" to "engine_invalidation"
                )
            ),
            telemetrySink.events
                .filter { it.name == "library.history.refresh_requested" }
                .map(TelemetryEvent::attributes)
        )

        gateway.emit(authenticatedSnapshot(historyGeneration = 2))

        assertEquals(listOf(EngineCommand.TYPE_LIST_HISTORY), gateway.commands.map(EngineCommand::type))
        assertEquals(listOf("history-1"), repository.state.value.historyEntries.map { it.historyId })
    }

    @Test
    fun `history projection merges snapshot entries without listing again`() {
        val gateway = RecordingLibraryGateway(
            snapshot = authenticatedSnapshot(historyEntriesCount = 1),
            history = listOf(
                historyItem("history-1", "track-1"),
                historyItem("history-2", "track-2")
            )
        )
        val repository = testLibraryRepository(gateway)
        repository.start()
        gateway.commands.clear()

        assertEquals(listOf("history-1"), repository.state.value.historyEntries.map { it.historyId })

        gateway.emit(authenticatedSnapshot(historyEntriesCount = 2, historyGeneration = 2))

        assertEquals(emptyList(), gateway.commands.map(EngineCommand::type))
        assertEquals(
            listOf("history-2", "history-1"),
            repository.state.value.historyEntries.map { it.historyId }
        )
    }

    @Test
    fun `unrelated snapshots reuse cached history without gateway reads`() {
        val snapshot = authenticatedSnapshot(historyEntriesCount = 1)
        val gateway = RecordingLibraryGateway(
            snapshot = snapshot,
            history = listOf(historyItem("history-1", "track-1"))
        )
        val repository = testLibraryRepository(gateway)
        repository.start()
        val historyReadsAfterStart = gateway.historyPageReads

        gateway.emit(snapshot.copy(updatedAtEpochMillis = snapshot.updatedAtEpochMillis + 1))

        assertEquals(historyReadsAfterStart, gateway.historyPageReads)
        assertEquals(listOf("history-1"), repository.state.value.historyEntries.map { it.historyId })
    }

    @Test
    fun `signed out snapshots switch to anonymous history and hydrate once`() {
        val gateway = RecordingLibraryGateway(
            snapshot = authenticatedSnapshot(historyEntriesCount = 1),
            history = listOf(historyItem("history-1", "track-1"))
        )
        val repository = testLibraryRepository(gateway)
        repository.start()
        repository.selectTab(LibraryTab.HISTORY)
        gateway.commands.clear()

        assertEquals(listOf("history-1"), repository.state.value.historyEntries.map { it.historyId })

        gateway.emit(EngineSnapshot.idle(2L))

        assertTrue(repository.state.value.isSignedOut)
        assertEquals(LibraryTab.HISTORY, repository.state.value.selectedTab)
        assertEquals(listOf("history-1"), repository.state.value.historyEntries.map { it.historyId })
        assertFalse(repository.state.value.hasHistoryNextPage)
        assertEquals(
            listOf(
                EngineCommand.TYPE_LOAD_HISTORY_SETTINGS,
                EngineCommand.TYPE_LIST_HISTORY
            ),
            gateway.commands.map(EngineCommand::type)
        )
    }

    @Test
    fun `playlist projection exposes selected tracks pagination and reconciliation revisions`() {
        val gateway = RecordingLibraryGateway(
            snapshot = authenticatedSnapshot(
                playlistsCount = 1,
                playlistTracksCount = 1,
                hasPlaylistsNextPage = true,
                hasPlaylistTracksNextPage = true,
                hasPlaylistReconciliation = true
            ),
            playlists = listOf(EnginePlaylistItem("playlist-1", "Road trip", "Summer", 7, 1_000, 2_000)),
            playlistTracks = listOf(
                EnginePlaylistTrackItem(
                    membershipId = "membership-1", playlistId = "playlist-1", mediaId = "track-1",
                    title = "Track", artistId = "artist-1", artist = "Artist", album = "Album",
                    durationMillis = 120_000, explicit = false, artworkUri = null,
                    artworkId = "artwork-1", position = 0,
                    addedAtEpochMillis = 1_500
                )
            ),
            selectedPlaylistId = "playlist-1",
            reconciliation = EnginePlaylistReconciliation(
                playlistId = "playlist-1",
                expectedRevision = 7,
                serverRevision = 8,
                serverMembershipIds = listOf("membership-server"),
                proposedMembershipIds = listOf("membership-local")
            )
        )
        val repository = testLibraryRepository(gateway)

        repository.start()

        assertEquals(listOf("playlist-1"), repository.state.value.playlists.map { it.id })
        assertEquals("playlist-1", repository.state.value.selectedPlaylistId)
        assertEquals(listOf("membership-1"), repository.state.value.playlistTracks.map { it.relationshipId })
        assertTrue(repository.state.value.hasPlaylistsNextPage)
        assertTrue(repository.state.value.hasPlaylistTracksNextPage)
        assertEquals(7, repository.state.value.playlistConflict?.expectedRevision)
        assertEquals(8, repository.state.value.playlistConflict?.serverRevision)
        assertEquals(listOf("membership-server"), repository.state.value.playlistConflict?.serverMembershipIds)
        assertEquals(listOf("membership-local"), repository.state.value.playlistConflict?.proposedMembershipIds)
    }

    @Test
    fun `pending identities are explicit and disconnected mutations fail closed`() {
        val gateway = RecordingLibraryGateway(
            snapshot = authenticatedSnapshot(libraryPendingCount = 1),
            pending = listOf("track-pending"),
            dispatchEventType = EngineEvent.TYPE_GATEWAY_UNAVAILABLE
        )
        val repository = testLibraryRepository(gateway)
        repository.start()

        assertEquals(setOf("track-pending"), repository.state.value.pendingMediaIds)

        repository.like("track-2")

        assertEquals(EngineCommand.TYPE_LIKE_TRACK, gateway.commands.last().type)
        assertEquals(setOf("track-pending"), repository.state.value.pendingMediaIds)
        assertTrue(repository.state.value.isRetryableError)
    }

    @Test
    fun `authenticated snapshot transitions hydrate each identity exactly once`() {
        val gateway = RecordingLibraryGateway(EngineSnapshot.idle(1L))
        val repository = testLibraryRepository(gateway)

        repository.start()
        assertEquals(
            listOf(
                EngineCommand.TYPE_LOAD_HISTORY_SETTINGS,
                EngineCommand.TYPE_LIST_HISTORY
            ),
            gateway.commands.map(EngineCommand::type)
        )
        gateway.commands.clear()

        gateway.emit(authenticatedSnapshot(accountId = "account-1", sessionId = "session-1"))
        assertEquals(
            listOf(
                EngineCommand.TYPE_LIST_SAVED_TRACKS,
                EngineCommand.TYPE_LIST_LIKED_TRACKS,
                EngineCommand.TYPE_LOAD_HISTORY_SETTINGS,
                EngineCommand.TYPE_LIST_HISTORY,
                EngineCommand.TYPE_LIST_PLAYLISTS
            ),
            gateway.commands.map(EngineCommand::type)
        )

        gateway.emit(authenticatedSnapshot(accountId = "account-1", sessionId = "session-1"))
        assertEquals(5, gateway.commands.size)

        gateway.emit(authenticatedSnapshot(accountId = "account-2", sessionId = "session-2"))
        assertEquals(10, gateway.commands.size)

        gateway.emit(authenticatedSnapshot(accountId = "account-2", sessionId = "session-3"))
        assertEquals(15, gateway.commands.size)
    }

    @Test
    fun `synchronous identity replacement cancels stale hydration and dedupes replacement loads`() {
        val replacement = authenticatedSnapshot(accountId = "account-2", sessionId = "session-2")
        val gateway = RecordingLibraryGateway(
            snapshot = authenticatedSnapshot(accountId = "account-1", sessionId = "session-1"),
            replaceOnFirstSavedLoad = replacement
        )
        val repository = testLibraryRepository(gateway)

        repository.start()

        assertEquals(
            listOf(
                LibraryLoad(EngineCommand.TYPE_LIST_SAVED_TRACKS, "account-1", "session-1"),
                LibraryLoad(EngineCommand.TYPE_LIST_SAVED_TRACKS, "account-2", "session-2"),
                LibraryLoad(EngineCommand.TYPE_LIST_LIKED_TRACKS, "account-2", "session-2"),
                LibraryLoad(EngineCommand.TYPE_LIST_HISTORY, "account-2", "session-2"),
                LibraryLoad(EngineCommand.TYPE_LIST_PLAYLISTS, "account-2", "session-2")
            ),
            gateway.libraryLoads
        )
    }

    private fun authenticatedSnapshot(
        savedTracksCount: Int = 0,
        likedTracksCount: Int = 0,
        libraryPendingCount: Int = 0,
        hasSavedTracksNextPage: Boolean = false,
        hasLikedTracksNextPage: Boolean = false,
        playlistsCount: Int = 0,
        playlistTracksCount: Int = 0,
        historyEntriesCount: Int = 0,
        hasHistoryNextPage: Boolean = false,
        historyGeneration: Long = 0,
        hasPlaylistsNextPage: Boolean = false,
        hasPlaylistTracksNextPage: Boolean = false,
        hasPlaylistReconciliation: Boolean = false,
        accountId: String = "account-1",
        sessionId: String = "session-1"
    ): EngineSnapshot = EngineSnapshot.idle(1L).copy(
        authState = EngineAuthState(
            state = EngineAuthState.AUTHENTICATED,
            account = EngineAccount(accountId, "$accountId@example.com", "active", 1L),
            session = EngineAuthSession(sessionId, "PandaWave", 1L, 1L, 10_000L, true)
        ),
        savedTracksCount = savedTracksCount,
        likedTracksCount = likedTracksCount,
        historyEntriesCount = historyEntriesCount,
        hasHistoryNextPage = hasHistoryNextPage,
        historyGeneration = historyGeneration,
        libraryPendingCount = libraryPendingCount,
        hasSavedTracksNextPage = hasSavedTracksNextPage,
        hasLikedTracksNextPage = hasLikedTracksNextPage,
        playlistsCount = playlistsCount,
        playlistTracksCount = playlistTracksCount,
        hasPlaylistsNextPage = hasPlaylistsNextPage,
        hasPlaylistTracksNextPage = hasPlaylistTracksNextPage,
        hasPlaylistReconciliation = hasPlaylistReconciliation
    )

    private fun item(mediaId: String) = EngineLibraryItem(
        relationshipId = mediaId,
        mediaId = mediaId,
        title = "Track $mediaId",
        artistId = "artist-1",
        artist = "Artist",
        durationMillis = 120_000,
        relationshipAtEpochMillis = 1_000
    )

    private fun historyItem(historyId: String, mediaId: String) = EngineHistoryItem(
        historyId = historyId,
        mediaId = mediaId,
        title = "Played $mediaId",
        artist = "Artist",
        album = null,
        artworkUri = "art-$mediaId",
        playedAtEpochMillis = 1_000,
        listenedDurationMillis = 90_000,
        completionRatio = 0.8F,
        playable = true
    )
}

private class RecordingLibraryGateway(
    snapshot: EngineSnapshot,
    private val saved: List<EngineLibraryItem> = emptyList(),
    private val liked: List<EngineLibraryItem> = emptyList(),
    private val history: List<EngineHistoryItem> = emptyList(),
    private val pending: List<String> = emptyList(),
    private val playlists: List<EnginePlaylistItem> = emptyList(),
    private val playlistTracks: List<EnginePlaylistTrackItem> = emptyList(),
    private val selectedPlaylistId: String? = null,
    private val reconciliation: EnginePlaylistReconciliation? = null,
    private val dispatchEventType: String = EngineEvent.TYPE_COMMAND_APPLIED,
    private val replaceOnFirstSavedLoad: EngineSnapshot? = null
) : EngineGateway {
    private var current = snapshot
    private var historyOffset = 0
    private var replacedDuringSavedLoad = false
    private val listeners = mutableListOf<(EngineSnapshot) -> Unit>()
    val commands = mutableListOf<EngineCommand>()
    val libraryLoads = mutableListOf<LibraryLoad>()
    var historyEntryReads = 0
        private set
    var historyPageReads = 0
        private set

    override fun snapshot(): EngineSnapshot = current
    override fun browseResult(index: Int): EngineCatalogItem? = null
    override fun searchResult(index: Int): EngineCatalogItem? = null
    override fun savedTrack(index: Int): EngineLibraryItem? = saved.getOrNull(index)
    override fun likedTrack(index: Int): EngineLibraryItem? = liked.getOrNull(index)
    override fun historyEntry(index: Int): EngineHistoryItem? {
        historyEntryReads += 1
        return history.getOrNull(historyOffset + index)
    }
    override fun savedTracksPage(offset: Int, limit: Int): List<EngineLibraryItem> =
        saved.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))
    override fun likedTracksPage(offset: Int, limit: Int): List<EngineLibraryItem> =
        liked.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))
    override fun historyPage(offset: Int, limit: Int, generation: Long): EngineHistoryPage {
        historyPageReads += 1
        return EngineHistoryPage(
            generation = generation,
            items = history
                .drop(historyOffset + offset.coerceAtLeast(0))
                .take(limit.coerceAtLeast(0))
        )
    }
    override fun playlistsPage(offset: Int, limit: Int): List<EnginePlaylistItem> =
        playlists.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))
    override fun playlistTracksPage(offset: Int, limit: Int): List<EnginePlaylistTrackItem> =
        playlistTracks.drop(offset.coerceAtLeast(0)).take(limit.coerceAtLeast(0))
    override fun pendingLibraryTrackId(index: Int): String? = pending.getOrNull(index)
    override fun playlist(index: Int): EnginePlaylistItem? = playlists.getOrNull(index)
    override fun playlistTrack(index: Int): EnginePlaylistTrackItem? = playlistTracks.getOrNull(index)
    override fun selectedPlaylistId(): String? = selectedPlaylistId
    override fun playlistReconciliation(): EnginePlaylistReconciliation? = reconciliation

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commands += command
        if (command.type in
            setOf(
                EngineCommand.TYPE_LIST_SAVED_TRACKS,
                EngineCommand.TYPE_LIST_LIKED_TRACKS,
                EngineCommand.TYPE_LIST_HISTORY,
                EngineCommand.TYPE_LIST_PLAYLISTS
            )
        ) {
            libraryLoads += LibraryLoad(
                type = command.type,
                accountId = current.authState.account?.id.orEmpty(),
                sessionId = current.authState.session?.id.orEmpty()
            )
        }
        when (command.type) {
            EngineCommand.TYPE_LIST_HISTORY -> {
                historyOffset = 0
                current = current.copy(
                    historyEntriesCount = minOf(current.historyEntriesCount.coerceAtLeast(1), history.size),
                    hasHistoryNextPage = history.size > current.historyEntriesCount.coerceAtLeast(1)
                )
            }

            EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE -> {
                historyOffset = minOf(historyOffset + current.historyEntriesCount, history.size)
                current = current.copy(
                    historyEntriesCount = (history.size - historyOffset).coerceIn(0, 1),
                    hasHistoryNextPage = historyOffset + 1 < history.size
                )
            }
        }
        if (!replacedDuringSavedLoad && command.type == EngineCommand.TYPE_LIST_SAVED_TRACKS) {
            replaceOnFirstSavedLoad?.let { replacement ->
                replacedDuringSavedLoad = true
                emit(replacement)
            }
        }
        return EngineDispatchResult(
            snapshot = current,
            event = EngineEvent(dispatchEventType, command.type),
            effects = emptyList<EngineEffect>()
        )
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult = EngineDispatchResult(
        snapshot = current,
        event = EngineEvent(EngineEvent.TYPE_PLATFORM_EVENT_APPLIED, event.type)
    )

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        listeners += listener
        listener(current)
        return AutoCloseable { listeners -= listener }
    }

    fun emit(snapshot: EngineSnapshot) {
        current = snapshot
        listeners.forEach { it(snapshot) }
    }

    override fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable = AutoCloseable { }
}

private data class LibraryLoad(val type: String, val accountId: String, val sessionId: String)

private fun testLibraryRepository(gateway: EngineGateway): PandaEngineLibraryRepository = PandaEngineLibraryRepository(
    engineGateway = gateway,
    telemetryLogger = TelemetryLogger(sink = TelemetrySink { }, clock = { 42L })
)

private class RecordingLibraryTelemetrySink : TelemetrySink {
    val events = mutableListOf<TelemetryEvent>()

    override fun record(event: TelemetryEvent) {
        events += event
    }
}
