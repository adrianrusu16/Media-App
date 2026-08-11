package com.adrianrusu.pandawave.feature.library.data

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.feature.library.domain.LibraryRepository
import com.adrianrusu.pandawave.feature.library.domain.LibraryState
import com.adrianrusu.pandawave.feature.library.domain.LibraryTab
import com.adrianrusu.pandawave.feature.library.domain.LibraryTrack
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

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        subscription = engineGateway.observeSnapshots(::project)
        project(engineGateway.snapshot())
    }

    override fun selectTab(tab: LibraryTab) {
        mutableState.value = mutableState.value.copy(selectedTab = tab)
    }

    override fun refresh() {
        dispatch(EngineCommand(EngineCommand.TYPE_LIST_SAVED_TRACKS, EngineCommandPayloads.libraryPage(PAGE_SIZE)))
        dispatch(EngineCommand(EngineCommand.TYPE_LIST_LIKED_TRACKS, EngineCommandPayloads.libraryPage(PAGE_SIZE)))
    }

    override fun loadNext(tab: LibraryTab) {
        val type = when (tab) {
            LibraryTab.SAVED -> EngineCommand.TYPE_LOAD_NEXT_SAVED_TRACKS_PAGE
            LibraryTab.LIKED -> EngineCommand.TYPE_LOAD_NEXT_LIKED_TRACKS_PAGE
        }
        dispatch(EngineCommand(type, null))
    }

    override fun save(mediaId: String) = mutate(EngineCommand.TYPE_SAVE_TRACK, mediaId)
    override fun removeSaved(mediaId: String) = mutate(EngineCommand.TYPE_REMOVE_SAVED_TRACK, mediaId)
    override fun like(mediaId: String) = mutate(EngineCommand.TYPE_LIKE_TRACK, mediaId)
    override fun unlike(mediaId: String) = mutate(EngineCommand.TYPE_UNLIKE_TRACK, mediaId)

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
            project(outcome.snapshot)
        }
    }

    private fun project(snapshot: EngineSnapshot) {
        val selectedTab = mutableState.value.selectedTab
        val identity = snapshot.libraryIdentity()
        if (identity == null) {
            hydratedIdentity = null
            mutableState.value = LibraryState(selectedTab = selectedTab, isLoading = false, isSignedOut = true)
            return
        }

        val errorType = snapshot.errorType.takeIf { snapshot.hasError }
        mutableState.value = LibraryState(
            selectedTab = selectedTab,
            savedTracks = List(snapshot.savedTracksCount.coerceAtLeast(0), engineGateway::savedTrack)
                .filterNotNull()
                .map { it.toLibraryTrack() },
            likedTracks = List(snapshot.likedTracksCount.coerceAtLeast(0), engineGateway::likedTrack)
                .filterNotNull()
                .map { it.toLibraryTrack() },
            pendingMediaIds = List(snapshot.libraryPendingCount.coerceAtLeast(0), engineGateway::pendingLibraryTrackId)
                .filterNotNull()
                .filter(String::isNotBlank)
                .toSet(),
            hasSavedNextPage = snapshot.hasSavedTracksNextPage,
            hasLikedNextPage = snapshot.hasLikedTracksNextPage,
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
        dispatch(EngineCommand(EngineCommand.TYPE_LIST_LIKED_TRACKS, EngineCommandPayloads.libraryPage(PAGE_SIZE)))
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

    private companion object {
        const val PAGE_SIZE = 25
    }

    private data class LibraryIdentity(val accountId: String, val sessionId: String)
}
