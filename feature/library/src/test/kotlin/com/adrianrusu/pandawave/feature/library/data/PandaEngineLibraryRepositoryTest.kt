package com.adrianrusu.pandawave.feature.library.data

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
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

    private fun authenticatedSnapshot(
        savedTracksCount: Int = 0,
        likedTracksCount: Int = 0,
        libraryPendingCount: Int = 0,
        hasSavedTracksNextPage: Boolean = false,
        hasLikedTracksNextPage: Boolean = false,
    ): EngineSnapshot = EngineSnapshot.idle(1L).copy(
        authState = EngineAuthState(EngineAuthState.AUTHENTICATED),
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
) : EngineGateway {
    private var current = snapshot
    private val listeners = mutableListOf<(EngineSnapshot) -> Unit>()
    val commands = mutableListOf<EngineCommand>()

    override fun snapshot(): EngineSnapshot = current
    override fun browseResult(index: Int): EngineCatalogItem? = null
    override fun searchResult(index: Int): EngineCatalogItem? = null
    override fun savedTrack(index: Int): EngineLibraryItem? = saved.getOrNull(index)
    override fun likedTrack(index: Int): EngineLibraryItem? = liked.getOrNull(index)
    override fun pendingLibraryTrackId(index: Int): String? = pending.getOrNull(index)

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commands += command
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

    override fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable = AutoCloseable { }
}
