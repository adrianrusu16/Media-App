package com.adrianrusu.pandawave.feature.library.data

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAccount
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthSession
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
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
                hasSavedTracksNextPage = true,
            ),
            saved = listOf(item("saved-1"), item("saved-2")),
            liked = listOf(item("liked-1")),
        )
        val repository = PandaEngineLibraryRepository(gateway)

        repository.start()

        assertEquals(
            listOf(EngineCommand.TYPE_LIST_SAVED_TRACKS, EngineCommand.TYPE_LIST_LIKED_TRACKS),
            gateway.commands.map(EngineCommand::type),
        )
        assertEquals(listOf("saved-1", "saved-2"), repository.state.value.savedTracks.map { it.mediaId })
        assertEquals(listOf("liked-1"), repository.state.value.likedTracks.map { it.mediaId })
        assertTrue(repository.state.value.hasSavedNextPage)
        assertFalse(repository.state.value.hasLikedNextPage)
    }

    @Test
    fun `pagination is engine owned and never exposes a continuation token`() {
        val gateway = RecordingLibraryGateway(authenticatedSnapshot())
        val repository = PandaEngineLibraryRepository(gateway)
        repository.start()
        gateway.commands.clear()

        repository.loadNext(LibraryTab.SAVED)
        repository.loadNext(LibraryTab.LIKED)

        assertEquals(
            listOf(
                EngineCommand.TYPE_LOAD_NEXT_SAVED_TRACKS_PAGE,
                EngineCommand.TYPE_LOAD_NEXT_LIKED_TRACKS_PAGE,
            ),
            gateway.commands.map(EngineCommand::type),
        )
        assertEquals(listOf(null, null), gateway.commands.map(EngineCommand::payload))
    }

    @Test
    fun `pending identities are explicit and disconnected mutations fail closed`() {
        val gateway = RecordingLibraryGateway(
            snapshot = authenticatedSnapshot(libraryPendingCount = 1),
            pending = listOf("track-pending"),
            dispatchEventType = EngineEvent.TYPE_GATEWAY_UNAVAILABLE,
        )
        val repository = PandaEngineLibraryRepository(gateway)
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
        val repository = PandaEngineLibraryRepository(gateway)

        repository.start()
        assertTrue(gateway.commands.isEmpty())

        gateway.emit(authenticatedSnapshot(accountId = "account-1", sessionId = "session-1"))
        assertEquals(
            listOf(EngineCommand.TYPE_LIST_SAVED_TRACKS, EngineCommand.TYPE_LIST_LIKED_TRACKS),
            gateway.commands.map(EngineCommand::type),
        )

        gateway.emit(authenticatedSnapshot(accountId = "account-1", sessionId = "session-1"))
        assertEquals(2, gateway.commands.size)

        gateway.emit(authenticatedSnapshot(accountId = "account-2", sessionId = "session-2"))
        assertEquals(4, gateway.commands.size)

        gateway.emit(authenticatedSnapshot(accountId = "account-2", sessionId = "session-3"))
        assertEquals(6, gateway.commands.size)
    }

    @Test
    fun `synchronous identity replacement cancels stale hydration and dedupes replacement loads`() {
        val replacement = authenticatedSnapshot(accountId = "account-2", sessionId = "session-2")
        val gateway = RecordingLibraryGateway(
            snapshot = authenticatedSnapshot(accountId = "account-1", sessionId = "session-1"),
            replaceOnFirstSavedLoad = replacement,
        )
        val repository = PandaEngineLibraryRepository(gateway)

        repository.start()

        assertEquals(
            listOf(
                LibraryLoad(EngineCommand.TYPE_LIST_SAVED_TRACKS, "account-1", "session-1"),
                LibraryLoad(EngineCommand.TYPE_LIST_SAVED_TRACKS, "account-2", "session-2"),
                LibraryLoad(EngineCommand.TYPE_LIST_LIKED_TRACKS, "account-2", "session-2"),
            ),
            gateway.libraryLoads,
        )
    }

    private fun authenticatedSnapshot(
        savedTracksCount: Int = 0,
        likedTracksCount: Int = 0,
        libraryPendingCount: Int = 0,
        hasSavedTracksNextPage: Boolean = false,
        hasLikedTracksNextPage: Boolean = false,
        accountId: String = "account-1",
        sessionId: String = "session-1",
    ): EngineSnapshot = EngineSnapshot.idle(1L).copy(
        authState = EngineAuthState(
            state = EngineAuthState.AUTHENTICATED,
            account = EngineAccount(accountId, "$accountId@example.com", "active", 1L),
            session = EngineAuthSession(sessionId, "PandaWave", 1L, 1L, 10_000L, true),
        ),
        savedTracksCount = savedTracksCount,
        likedTracksCount = likedTracksCount,
        libraryPendingCount = libraryPendingCount,
        hasSavedTracksNextPage = hasSavedTracksNextPage,
        hasLikedTracksNextPage = hasLikedTracksNextPage,
    )

    private fun item(mediaId: String) = EngineLibraryItem(
        relationshipId = mediaId,
        mediaId = mediaId,
        title = "Track $mediaId",
        artistId = "artist-1",
        artist = "Artist",
        durationMillis = 120_000,
        relationshipAtEpochMillis = 1_000,
    )
}

private class RecordingLibraryGateway(
    snapshot: EngineSnapshot,
    private val saved: List<EngineLibraryItem> = emptyList(),
    private val liked: List<EngineLibraryItem> = emptyList(),
    private val pending: List<String> = emptyList(),
    private val dispatchEventType: String = EngineEvent.TYPE_COMMAND_APPLIED,
    private val replaceOnFirstSavedLoad: EngineSnapshot? = null,
) : EngineGateway {
    private var current = snapshot
    private var replacedDuringSavedLoad = false
    private val listeners = mutableListOf<(EngineSnapshot) -> Unit>()
    val commands = mutableListOf<EngineCommand>()
    val libraryLoads = mutableListOf<LibraryLoad>()

    override fun snapshot(): EngineSnapshot = current
    override fun browseResult(index: Int): EngineCatalogItem? = null
    override fun searchResult(index: Int): EngineCatalogItem? = null
    override fun savedTrack(index: Int): EngineLibraryItem? = saved.getOrNull(index)
    override fun likedTrack(index: Int): EngineLibraryItem? = liked.getOrNull(index)
    override fun pendingLibraryTrackId(index: Int): String? = pending.getOrNull(index)

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commands += command
        if (command.type in setOf(EngineCommand.TYPE_LIST_SAVED_TRACKS, EngineCommand.TYPE_LIST_LIKED_TRACKS)) {
            libraryLoads += LibraryLoad(
                type = command.type,
                accountId = current.authState.account?.id.orEmpty(),
                sessionId = current.authState.session?.id.orEmpty(),
            )
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
            effects = emptyList<EngineEffect>(),
        )
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult =
        EngineDispatchResult(
            snapshot = current,
            event = EngineEvent(EngineEvent.TYPE_PLATFORM_EVENT_APPLIED, event.type),
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
