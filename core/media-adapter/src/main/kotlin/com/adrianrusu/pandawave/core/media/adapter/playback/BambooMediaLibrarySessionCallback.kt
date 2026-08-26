package com.adrianrusu.pandawave.core.media.adapter.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.adrianrusu.pandawave.core.common.trace.PandaTrace
import com.adrianrusu.pandawave.core.playback.BambooControlState
import com.adrianrusu.pandawave.core.playback.BambooPlaybackControls
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Callable
import java.util.concurrent.Executor

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
    private val sessionPackageName: String,
    private val catalogExecutor: Executor,
    private val resumptionStore: MediaSessionPlaybackResumptionStore? = null,
    private val playbackState: () -> BambooPlaybackState = { BambooPlaybackState() },
    private val openNowPlaying: () -> Unit = {},
    private val controls: () -> BambooPlaybackControls = { controlsFor(controlsEnabled()) }
) : MediaLibrarySession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        if (!BambooMediaSessionControllerPolicy.isAccepted(
                controllerPackageName = controller.packageName,
                sessionPackageName = sessionPackageName,
                isMediaNotificationController = session.isMediaNotificationController(controller),
                isAutomotiveController = session.isAutomotiveController(controller),
                isAutoCompanionController = session.isAutoCompanionController(controller),
            )
        ) {
            return MediaSession.ConnectionResult.reject()
        }

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(availableSessionCommands())
            .setAvailablePlayerCommands(
                BambooMediaSessionCommandPolicy.availablePlayerCommands(
                    playerCommands = session.player.availableCommands,
                    controls = controls()
                )
            )
            .build()
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> = PandaTrace.section("PW.Media3.Callback.getRoot") {
        Futures.immediateFuture(
            LibraryResult.ofItem(
                catalog.root(),
                params
            )
        )
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> = asyncCatalog("PW.Media3.Callback.getItem") {
        val item = catalog.item(mediaId)
        if (item == null) {
            LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        } else {
            LibraryResult.ofItem(item, /* params= */ null)
        }
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        asyncCatalog("PW.Media3.Callback.getChildren") {
            LibraryResult.ofItemList(
                catalog.children(
                    parentId = parentId,
                    page = page,
                    pageSize = pageSize
                ),
                params
            )
        }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> = asyncCatalog("PW.Media3.Callback.search") {
        val page = catalog.searchPage(query, page = 0, pageSize = DEFAULT_SEARCH_PAGE_SIZE)
        val resultCount = if (page.hasNextPage) Int.MAX_VALUE else page.totalCount
        session.notifySearchResultChanged(browser, query, resultCount, params)
        LibraryResult.ofVoid()
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        asyncCatalog("PW.Media3.Callback.getSearchResult") {
            LibraryResult.ofItemList(
                catalog.search(query = query, page = page, pageSize = pageSize),
                params
            )
        }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> = PandaTrace.section("PW.Media3.Callback.addMediaItems") {
        Futures.immediateFuture(
            dispatchPlayback(mediaItems, startIndex = 0).toMutableList()
        )
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        PandaTrace.section("PW.Media3.Callback.setMediaItems") {
            val resolved = dispatchPlayback(mediaItems, startIndex = startIndex)
            if (resolved.isEmpty()) {
                return@section Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                )
            }
            Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    resolved,
                    startIndex.coerceIn(0, resolved.lastIndex.coerceAtLeast(0)),
                    startPositionMs.coerceAtLeast(0L)
                )
            )
        }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val stored = resumptionStore?.load()
        if (stored == null || stored.mediaIds.isEmpty()) {
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
            )
        }
        val items = stored.mediaIds.map { mediaId ->
            catalog.item(mediaId) ?: BambooMediaLibraryPlaybackSelection.playableMetadataItem(mediaId)
        }
        val current = playbackState()
        val positionMillis = if (current.mediaId == stored.mediaIds.getOrNull(stored.startIndex)) {
            current.positionMillis.coerceAtLeast(0L)
        } else {
            stored.positionMillis
        }
        if (isForPlayback) {
            dispatchPlayback(
                items = items,
                startIndex = stored.startIndex,
                persist = false,
            )
        }
        return Futures.immediateFuture(
            MediaSession.MediaItemsWithStartPosition(items, stored.startIndex, positionMillis)
        )
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        if (customCommand.customAction == PandaWaveMediaSessionContract.COMMAND_OPEN_NOW_PLAYING) {
            openNowPlaying()
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        return super.onCustomCommand(session, controller, customCommand, args)
    }

    private fun dispatchPlayback(
        items: List<MediaItem>,
        startIndex: Int,
        persist: Boolean = true,
    ): List<MediaItem> {
        val resolved = BambooMediaLibraryPlaybackSelection.withoutLocalConfiguration(items)
        val mediaIds = BambooMediaLibraryPlaybackSelection.mediaIds(resolved)
        val intent = BambooMediaLibraryPlaybackSelection.playbackIntent(mediaIds, startIndex)
        when (intent) {
            is BambooPlaybackIntent.PlayMedia -> playbackBridge.dispatchCatalogPlay(intent.mediaId)
            is BambooPlaybackIntent.PlayQueue -> playbackBridge.dispatchCatalogPlayQueue(
                mediaIds = intent.mediaIds,
                startIndex = intent.startIndex,
            )
            else -> Unit
        }
        if (persist && mediaIds.isNotEmpty()) {
            resumptionStore?.save(
                mediaIds = mediaIds,
                startIndex = startIndex.coerceIn(0, mediaIds.lastIndex),
                positionMillis = 0L,
            )
        }
        return resolved
    }

    private fun <T> asyncCatalog(traceName: String, block: () -> T): ListenableFuture<T> =
        Futures.submit(
            Callable {
                PandaTrace.section(traceName, block)
            },
            catalogExecutor,
        )

    private fun availableSessionCommands() =
        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .add(SessionCommand(PandaWaveMediaSessionContract.COMMAND_OPEN_NOW_PLAYING, Bundle.EMPTY))
            .build()
}

internal fun nowPlayingLaunchIntent(context: Context): Intent? =
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        addFlags(
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
        )
        putExtra(PandaWaveMediaSessionContract.EXTRA_OPEN_NOW_PLAYING, true)
    }

private fun controlsFor(enabled: Boolean): BambooPlaybackControls {
    val control = if (enabled) BambooControlState.enabled() else BambooControlState.hidden()
    return BambooPlaybackControls(control, control, control, showPlayIcon = true)
}

private const val DEFAULT_SEARCH_PAGE_SIZE = 50
