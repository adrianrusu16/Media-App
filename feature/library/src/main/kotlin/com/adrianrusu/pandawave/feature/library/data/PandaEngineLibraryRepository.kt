package com.adrianrusu.pandawave.feature.library.data

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineHistoryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlaylistTrackItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.feature.library.domain.LibraryHistoryEntry
import com.adrianrusu.pandawave.feature.library.domain.LibraryRepository
import com.adrianrusu.pandawave.feature.library.domain.LibraryState
import com.adrianrusu.pandawave.feature.library.domain.LibraryTab
import com.adrianrusu.pandawave.feature.library.domain.LibraryTrack
import com.adrianrusu.pandawave.feature.library.domain.LibraryPlaylist
import com.adrianrusu.pandawave.feature.library.domain.PlaylistConflict
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PandaEngineLibraryRepository @Inject constructor(
    private val engineGateway: EngineGateway,
) : LibraryRepository {
    private val mutableState = MutableStateFlow(LibraryState())
    override val state: StateFlow<LibraryState> = mutableState.asStateFlow()

    private val started = AtomicBoolean(false)
    private var subscription: AutoCloseable? = null
    private var hydratedIdentity: LibraryIdentity? = null
    private var historyCacheKey: HistoryCacheKey? = null
    private var historyEntries: List<LibraryHistoryEntry> = emptyList()
    private val savedTracksCache = ProjectionCache<EngineLibraryItem, LibraryTrack>()
    private val likedTracksCache = ProjectionCache<EngineLibraryItem, LibraryTrack>()
    private val playlistsCache = ProjectionCache<EnginePlaylistItem, LibraryPlaylist>()
    private val playlistTracksCache = ProjectionCache<EnginePlaylistTrackItem, LibraryTrack>()
    private val pendingIdsCache = ProjectionCache<String, String>()
    private var playlistSelectionKey: PlaylistSelectionKey? = null
    private var playlistSelection = PlaylistSelection()

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        subscription = engineGateway.observeSnapshots { snapshot -> project(snapshot) }
        project(engineGateway.snapshot())
    }

    override fun selectTab(tab: LibraryTab) {
        mutableState.value = mutableState.value.copy(selectedTab = tab)
    }

    override fun refresh() {
        dispatch(EngineCommand(EngineCommand.TYPE_LIST_SAVED_TRACKS, EngineCommandPayloads.libraryPage(PAGE_SIZE)))
        dispatch(EngineCommand(EngineCommand.TYPE_LIST_LIKED_TRACKS, EngineCommandPayloads.libraryPage(PAGE_SIZE)))
        dispatch(EngineCommand(EngineCommand.TYPE_LIST_HISTORY, EngineCommandPayloads.historyPage(HISTORY_PAGE_SIZE)))
        dispatch(EngineCommand(EngineCommand.TYPE_LIST_PLAYLISTS, EngineCommandPayloads.playlistPage(PAGE_SIZE)))
    }

    override fun loadNext(tab: LibraryTab) {
        val type = when (tab) {
            LibraryTab.SAVED -> EngineCommand.TYPE_LOAD_NEXT_SAVED_TRACKS_PAGE
            LibraryTab.LIKED -> EngineCommand.TYPE_LOAD_NEXT_LIKED_TRACKS_PAGE
            LibraryTab.HISTORY -> EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE
            LibraryTab.PLAYLISTS -> if (mutableState.value.selectedPlaylistId == null) {
                EngineCommand.TYPE_LOAD_NEXT_PLAYLISTS_PAGE
            } else {
                EngineCommand.TYPE_LOAD_NEXT_PLAYLIST_TRACKS_PAGE
            }
        }
        dispatch(EngineCommand(type, null))
    }

    override fun save(mediaId: String) = mutate(EngineCommand.TYPE_SAVE_TRACK, mediaId)
    override fun removeSaved(mediaId: String) = mutate(EngineCommand.TYPE_REMOVE_SAVED_TRACK, mediaId)
    override fun like(mediaId: String) = mutate(EngineCommand.TYPE_LIKE_TRACK, mediaId)
    override fun unlike(mediaId: String) = mutate(EngineCommand.TYPE_UNLIKE_TRACK, mediaId)
    override fun createPlaylist(name: String, description: String?) {
        require(name.isNotBlank())
        dispatch(EngineCommand(EngineCommand.TYPE_CREATE_PLAYLIST, EngineCommandPayloads.playlistDetails(null, name, description)))
    }
    override fun updatePlaylist(playlistId: String, name: String, description: String?, expectedRevision: Long) {
        require(playlistId.isNotBlank())
        require(name.isNotBlank())
        dispatch(EngineCommand(EngineCommand.TYPE_UPDATE_PLAYLIST, EngineCommandPayloads.playlistDetails(playlistId, name, description, expectedRevision)))
    }
    override fun deletePlaylist(playlistId: String) = dispatch(EngineCommand(EngineCommand.TYPE_DELETE_PLAYLIST, EngineCommandPayloads.playlistId(playlistId)))
    override fun selectPlaylist(playlistId: String) {
        require(playlistId.isNotBlank())
        mutableState.value = mutableState.value.copy(selectedPlaylistId = playlistId)
        dispatch(EngineCommand(EngineCommand.TYPE_LIST_PLAYLIST_TRACKS, EngineCommandPayloads.playlistPage(PAGE_SIZE, playlistId)))
    }
    override fun addPlaylistTrack(playlistId: String, mediaId: String) = dispatch(EngineCommand(EngineCommand.TYPE_ADD_PLAYLIST_TRACK, EngineCommandPayloads.playlistTrack(playlistId, mediaId)))
    override fun removePlaylistTrack(playlistId: String, mediaId: String) = dispatch(EngineCommand(EngineCommand.TYPE_REMOVE_PLAYLIST_TRACK, EngineCommandPayloads.playlistTrack(playlistId, mediaId)))
    override fun reorderPlaylist(playlistId: String, membershipIds: List<String>, expectedRevision: Long) = dispatch(EngineCommand(EngineCommand.TYPE_REORDER_PLAYLIST_TRACKS, EngineCommandPayloads.playlistReorder(playlistId, membershipIds, expectedRevision)) )

    override fun close() {
        subscription?.close()
        subscription = null
        hydratedIdentity = null
        started.set(false)
    }

    private fun mutate(type: String, mediaId: String) {
        require(mediaId.isNotBlank())
        dispatch(EngineCommand(type, EngineCommandPayloads.libraryTrack(mediaId)))
    }

    private fun dispatch(command: EngineCommand) {
        val outcome = engineGateway.dispatch(command)
        if (outcome.event.type == EngineEvent.TYPE_GATEWAY_UNAVAILABLE) {
            mutableState.value = mutableState.value.copy(
                isLoading = false,
                errorType = EngineSnapshot.ERROR_NETWORK,
                isRetryableError = true,
            )
        } else {
            project(outcome.snapshot, command)
        }
    }

    private fun project(snapshot: EngineSnapshot, command: EngineCommand? = null) {
        val selectedTab = mutableState.value.selectedTab
        val identity = snapshot.libraryIdentity()
        if (identity == null) {
            hydratedIdentity = null
            historyCacheKey = null
            historyEntries = emptyList()
            clearProjectionCaches()
            mutableState.value = LibraryState(selectedTab = selectedTab, isLoading = false, isSignedOut = true)
            return
        }
        if (hydratedIdentity != identity) clearProjectionCaches()
        updateHistoryCache(snapshot, command)

        val errorType = snapshot.errorType.takeIf { snapshot.hasError }
        val playlistSelection = playlistSelection(snapshot, command)
        mutableState.value = LibraryState(
            selectedTab = selectedTab,
            savedTracks = savedTracksCache.project(
                count = snapshot.savedTracksCount.coerceAtLeast(0),
                force = command?.type in savedTrackCommands,
                itemAt = engineGateway::savedTrack,
                mapper = { it.toLibraryTrack() },
            ),
            likedTracks = likedTracksCache.project(
                count = snapshot.likedTracksCount.coerceAtLeast(0),
                force = command?.type in likedTrackCommands,
                itemAt = engineGateway::likedTrack,
                mapper = { it.toLibraryTrack() },
            ),
            historyEntries = historyEntries,
            playlists = playlistsCache.project(
                count = snapshot.playlistsCount.coerceAtLeast(0),
                force = command?.type in playlistCommands,
                itemAt = engineGateway::playlist,
                mapper = { LibraryPlaylist(it.id, it.name, it.description, it.revision) },
            ),
            selectedPlaylistId = playlistSelection.selectedPlaylistId,
            playlistTracks = playlistTracksCache.project(
                count = snapshot.playlistTracksCount.coerceAtLeast(0),
                force = command?.type in playlistTrackCommands,
                itemAt = engineGateway::playlistTrack,
                mapper = { item -> LibraryTrack(item.membershipId, item.mediaId, item.title, item.artist, item.album, item.durationMillis, item.explicit, item.artworkId, item.addedAtEpochMillis) },
            ),
            playlistConflict = playlistSelection.conflict,
            pendingMediaIds = pendingIdsCache.project(
                count = snapshot.libraryPendingCount.coerceAtLeast(0),
                force = command?.type in libraryMutationCommands,
                itemAt = engineGateway::pendingLibraryTrackId,
                mapper = { it },
            ).filter(String::isNotBlank).toSet(),
            hasSavedNextPage = snapshot.hasSavedTracksNextPage,
            hasLikedNextPage = snapshot.hasLikedTracksNextPage,
            hasHistoryNextPage = snapshot.hasHistoryNextPage,
            hasPlaylistsNextPage = snapshot.hasPlaylistsNextPage,
            hasPlaylistTracksNextPage = snapshot.hasPlaylistTracksNextPage,
            isLoading = snapshot.isBusy,
            isSignedOut = false,
            errorType = errorType,
            isRetryableError = errorType == EngineSnapshot.ERROR_NETWORK,
        )
        hydrateFor(identity)
    }

    private fun hydrateFor(identity: LibraryIdentity) {
        if (hydratedIdentity == identity) return
        hydratedIdentity = identity
        dispatch(EngineCommand(EngineCommand.TYPE_LIST_SAVED_TRACKS, EngineCommandPayloads.libraryPage(PAGE_SIZE)))
        if (hydratedIdentity != identity) return
        dispatch(EngineCommand(EngineCommand.TYPE_LIST_LIKED_TRACKS, EngineCommandPayloads.libraryPage(PAGE_SIZE)))
        if (hydratedIdentity != identity) return
        dispatch(EngineCommand(EngineCommand.TYPE_LOAD_HISTORY_SETTINGS, null))
        if (hydratedIdentity != identity) return
        dispatch(EngineCommand(EngineCommand.TYPE_LIST_HISTORY, EngineCommandPayloads.historyPage(HISTORY_PAGE_SIZE)))
        if (hydratedIdentity != identity) return
        dispatch(EngineCommand(EngineCommand.TYPE_LIST_PLAYLISTS, EngineCommandPayloads.playlistPage(PAGE_SIZE)))
    }

    private fun updateHistoryCache(snapshot: EngineSnapshot, command: EngineCommand?) {
        val identity = snapshot.libraryIdentity()
        val nextKey = identity?.let { HistoryCacheKey(it.accountId, it.sessionId, snapshot.historyGeneration) }
        if (historyCacheKey != nextKey) {
            historyCacheKey = nextKey
            historyEntries = emptyList()
        }
        if (command?.type != EngineCommand.TYPE_LIST_HISTORY &&
            command?.type != EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE
        ) {
            return
        }
        val page = List(snapshot.historyEntriesCount.coerceAtLeast(0), engineGateway::historyEntry)
            .filterNotNull()
            .map { item -> item.toLibraryHistoryEntry() }
        historyEntries = when (command?.type) {
            EngineCommand.TYPE_LIST_HISTORY -> page
            EngineCommand.TYPE_LOAD_NEXT_HISTORY_PAGE -> historyEntries + page
            else -> historyEntries
        }
    }

    private fun playlistSelection(snapshot: EngineSnapshot, command: EngineCommand?): PlaylistSelection {
        val key = PlaylistSelectionKey(
            playlistsCount = snapshot.playlistsCount,
            playlistTracksCount = snapshot.playlistTracksCount,
            hasReconciliation = snapshot.hasPlaylistReconciliation,
        )
        if (playlistSelectionKey == key && command?.type !in playlistSelectionCommands) {
            return playlistSelection
        }
        playlistSelectionKey = key
        playlistSelection = PlaylistSelection(
            selectedPlaylistId = engineGateway.selectedPlaylistId(),
            conflict = engineGateway.playlistReconciliation()?.let {
                PlaylistConflict(
                    playlistId = it.playlistId,
                    expectedRevision = it.expectedRevision,
                    serverRevision = it.serverRevision,
                    serverMembershipIds = it.serverMembershipIds,
                    proposedMembershipIds = it.proposedMembershipIds,
                )
            },
        )
        return playlistSelection
    }

    private fun clearProjectionCaches() {
        savedTracksCache.clear()
        likedTracksCache.clear()
        playlistsCache.clear()
        playlistTracksCache.clear()
        pendingIdsCache.clear()
        playlistSelectionKey = null
        playlistSelection = PlaylistSelection()
    }

    private fun EngineSnapshot.libraryIdentity(): LibraryIdentity? {
        val auth = authState
        val accountId = auth.account?.id?.takeIf(String::isNotBlank) ?: return null
        val sessionId = auth.session?.id?.takeIf(String::isNotBlank) ?: return null
        return LibraryIdentity(accountId, sessionId).takeIf {
            auth.state == EngineAuthState.AUTHENTICATED && auth.session?.current == true
        }
    }

    private fun EngineLibraryItem.toLibraryTrack() = LibraryTrack(
        relationshipId = relationshipId,
        mediaId = mediaId,
        title = title,
        artist = artist,
        album = album,
        durationMillis = durationMillis,
        explicit = explicit,
        artworkId = artworkId,
        relationshipAtEpochMillis = relationshipAtEpochMillis,
    )

    private fun EngineHistoryItem.toLibraryHistoryEntry() = LibraryHistoryEntry(
        historyId = historyId,
        mediaId = mediaId,
        title = title,
        artist = artist,
        album = album,
        artworkUri = artworkUri,
        playedAtEpochMillis = playedAtEpochMillis,
        listenedDurationMillis = listenedDurationMillis,
        completionRatio = completionRatio,
        playable = playable,
    )

    private data class LibraryIdentity(val accountId: String, val sessionId: String)
    private data class HistoryCacheKey(val accountId: String, val sessionId: String, val generation: Long)
    private data class PlaylistSelectionKey(
        val playlistsCount: Int,
        val playlistTracksCount: Int,
        val hasReconciliation: Boolean,
    )
    private data class PlaylistSelection(
        val selectedPlaylistId: String? = null,
        val conflict: PlaylistConflict? = null,
    )

    private companion object {
        const val PAGE_SIZE = 25
        const val HISTORY_PAGE_SIZE = 40

        val savedTrackCommands = setOf(
            EngineCommand.TYPE_LIST_SAVED_TRACKS,
            EngineCommand.TYPE_LOAD_NEXT_SAVED_TRACKS_PAGE,
            EngineCommand.TYPE_SAVE_TRACK,
            EngineCommand.TYPE_REMOVE_SAVED_TRACK,
        )
        val likedTrackCommands = setOf(
            EngineCommand.TYPE_LIST_LIKED_TRACKS,
            EngineCommand.TYPE_LOAD_NEXT_LIKED_TRACKS_PAGE,
            EngineCommand.TYPE_LIKE_TRACK,
            EngineCommand.TYPE_UNLIKE_TRACK,
        )
        val playlistCommands = setOf(
            EngineCommand.TYPE_LIST_PLAYLISTS,
            EngineCommand.TYPE_LOAD_NEXT_PLAYLISTS_PAGE,
            EngineCommand.TYPE_CREATE_PLAYLIST,
            EngineCommand.TYPE_UPDATE_PLAYLIST,
            EngineCommand.TYPE_DELETE_PLAYLIST,
        )
        val playlistTrackCommands = setOf(
            EngineCommand.TYPE_LIST_PLAYLIST_TRACKS,
            EngineCommand.TYPE_LOAD_NEXT_PLAYLIST_TRACKS_PAGE,
            EngineCommand.TYPE_ADD_PLAYLIST_TRACK,
            EngineCommand.TYPE_REMOVE_PLAYLIST_TRACK,
            EngineCommand.TYPE_REORDER_PLAYLIST_TRACKS,
        )
        val playlistSelectionCommands = playlistCommands + playlistTrackCommands
        val libraryMutationCommands = savedTrackCommands + likedTrackCommands + playlistCommands + playlistTrackCommands
    }
}

private class ProjectionCache<Source, Target> {
    private var count: Int = -1
    private var items: List<Target> = emptyList()

    fun project(
        count: Int,
        force: Boolean,
        itemAt: (Int) -> Source?,
        mapper: (Source) -> Target,
    ): List<Target> {
        if (!force && this.count == count) return items
        this.count = count
        items = List(count, itemAt).filterNotNull().map(mapper)
        return items
    }

    fun clear() {
        count = -1
        items = emptyList()
    }
}
