package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.adrianrusu.pandawave.core.playback.BambooControlState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackControls

/**
 * Media3 session callback for platform controllers.
 *
 * Library contents flow through a catalog source so PandaEngine can become the
 * backing provider without changing the Media3 session contract.
 */
@UnstableApi
internal class BambooMediaLibrarySessionCallback(
    private val controlsEnabled: () -> Boolean,
    private val catalog: BambooMediaLibraryCatalog,
    private val playbackBridge: Media3PlaybackEngineBridge,
    private val controls: () -> BambooPlaybackControls = { controlsFor(controlsEnabled()) }
) : MediaLibrarySession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        .setAvailablePlayerCommands(
            BambooMediaSessionCommandPolicy.availablePlayerCommands(
                playerCommands = session.player.availableCommands,
                controls = controls()
            )
        )
        .build()

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
        LibraryResult.ofItem(
            catalog.root(),
            params
        )
    )

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = Futures.immediateFuture(
        LibraryResult.ofItemList(
            catalog.children(
                parentId = parentId,
                page = page,
                pageSize = pageSize
            ),
            params
        )
    )

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        val resultCount = catalog.search(query, page = 0, pageSize = Int.MAX_VALUE).size
        session.notifySearchResultChanged(browser, query, resultCount, params)
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        mediaItems.firstOrNull()?.mediaId?.let(playbackBridge::dispatchCatalogPlay)
        return Futures.immediateFuture(mediaItems)
    }
}

private fun controlsFor(enabled: Boolean): BambooPlaybackControls {
    val control = if (enabled) BambooControlState.enabled() else BambooControlState.hidden()
    return BambooPlaybackControls(control, control, control, showPlayIcon = true)
}
