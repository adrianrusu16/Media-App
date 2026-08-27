package com.adrianrusu.pandawave.core.media.adapter.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.adrianrusu.pandawave.core.common.trace.PandaTrace
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.playback.PandaPlaybackContext
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Callable
import java.util.concurrent.Executor

@UnstableApi
internal class BambooMediaLibrarySessionCallback(
    private val catalog: BambooMediaLibraryCatalog,
    private val playbackBridge: Media3PlaybackEngineBridge,
    private val queue: Media3QueueProjection,
    private val sessionPackageName: String,
    private val catalogExecutor: Executor,
    private val resumptionStore: MediaSessionPlaybackResumptionStore? = null,
    private val playbackState: () -> BambooPlaybackState = { BambooPlaybackState() },
    private val openNowPlaying: () -> Unit = {}
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
                isAutoCompanionController = session.isAutoCompanionController(controller)
            )
        ) {
            return MediaSession.ConnectionResult.reject()
        }

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
            .setAvailableSessionCommands(availableSessionCommands())
            .setAvailablePlayerCommands(
                BambooMediaSessionCommandPolicy.availablePlayerCommands(
                    controls = playbackState().controls,
                    hasSeekableTimeline = queue.hasSeekableTimeline()
                )
            )
            .build()
    }

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> = PandaTrace.section("PW.Media3.Callback.getRoot") {
        val hints = browseHints(params, session, browser)
        Futures.immediateFuture(LibraryResult.ofItem(catalog.root(hints), params))
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
            LibraryResult.ofItem(item, null)
        }
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = asyncCatalog("PW.Media3.Callback.getChildren") {
        val hints = browseHints(params, session, browser)
        LibraryResult.ofItemList(
            catalog.children(parentId = parentId, page = page, pageSize = pageSize, hints = hints),
            params
        )
    }

    override fun onSubscribe(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> = Futures.immediateFuture(LibraryResult.ofVoid())

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> = asyncCatalog("PW.Media3.Callback.search") {
        val hints = browseHints(params, session, browser)
        val page = catalog.searchPage(query, page = 0, pageSize = DEFAULT_SEARCH_PAGE_SIZE, hints = hints)
        val resultCount = when {
            hints.ignoreHostPagination -> page.totalCount
            page.hasNextPage -> Int.MAX_VALUE
            else -> page.totalCount
        }
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
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = asyncCatalog("PW.Media3.Callback.getSearchResult") {
        LibraryResult.ofItemList(
            catalog.search(
                query = query,
                page = page,
                pageSize = pageSize,
                hints = browseHints(params, session, browser)
            ),
            params
        )
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> = PandaTrace.section("PW.Media3.Callback.addMediaItems") {
        Futures.immediateFuture(dispatchPlayback(mediaItems, startIndex = 0).toMutableList())
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
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
        }
        val items = stored.mediaIds.mapNotNull { mediaId -> catalog.item(mediaId) }
        if (items.isEmpty()) {
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
        }
        val startIndex = stored.startIndex.coerceIn(0, items.lastIndex)
        val current = playbackState()
        val positionMillis = if (current.mediaId == PandaMediaSelectionId.engineMediaId(items[startIndex].mediaId)) {
            current.positionMillis.coerceAtLeast(0L)
        } else {
            stored.positionMillis
        }
        if (isForPlayback) {
            dispatchPlayback(items = items, startIndex = startIndex, persist = false)
        }
        return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(items, startIndex, positionMillis))
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

    private fun dispatchPlayback(items: List<MediaItem>, startIndex: Int, persist: Boolean = true): List<MediaItem> {
        val resolved = BambooMediaLibraryPlaybackSelection.withoutLocalConfiguration(items)
        when (val request = BambooMediaLibraryPlaybackSelection.classify(resolved, startIndex)) {
            is Media3PlaybackRequest.Selection -> dispatchSelection(request.items, request.startIndex)

            is Media3PlaybackRequest.Search -> dispatchSearchPlayback(request.query)

            is Media3PlaybackRequest.Uri -> playbackBridge.dispatchPlayFromContext(
                BambooMediaLibraryPlaybackSelection.fromUri(request.uri)
            )

            Media3PlaybackRequest.EmptyVoice -> dispatchEmptyVoice()
        }
        val mediaIds = BambooMediaLibraryPlaybackSelection.mediaIds(resolved)
        if (persist && mediaIds.isNotEmpty()) {
            resumptionStore?.save(
                mediaIds = mediaIds,
                startIndex = startIndex.coerceIn(0, mediaIds.lastIndex.coerceAtLeast(0)),
                positionMillis = 0L
            )
        }
        return resolved
    }

    private fun dispatchSelection(items: List<MediaItem>, startIndex: Int) {
        val mediaIds = BambooMediaLibraryPlaybackSelection.mediaIds(items)
        val intent = BambooMediaLibraryPlaybackSelection.playbackIntent(mediaIds, startIndex) ?: return
        seedQueue(items, startIndex)
        playbackBridge.dispatchPlayFromContext(intent)
    }

    private fun dispatchSearchPlayback(query: String) {
        val results = catalog.search(
            query = query,
            page = 0,
            pageSize = AAOS_MATERIALIZED_LIMIT,
            hints = PandaLibraryBrowseHints(ignoreHostPagination = true)
        )
        if (results.isEmpty()) return
        dispatchSelection(results, startIndex = 0)
    }

    private fun dispatchEmptyVoice() {
        val recent = catalog.children(
            parentId = PandaMediaLibraryIds.PLATFORM_RECENT,
            page = 0,
            pageSize = AAOS_MATERIALIZED_LIMIT,
            hints = PandaLibraryBrowseHints(isRecent = true, ignoreHostPagination = true)
        )
        val playable = recent.ifEmpty {
            catalog.children(
                parentId = PandaMediaLibraryIds.PLATFORM_SUGGESTED,
                page = 0,
                pageSize = AAOS_MATERIALIZED_LIMIT,
                hints = PandaLibraryBrowseHints(isSuggested = true, ignoreHostPagination = true)
            )
        }
        if (playable.isEmpty()) {
            playbackBridge.dispatchPlayFromContext(
                BambooPlaybackIntent.PlayFromContext(
                    context = PandaPlaybackContext.VoiceDefault,
                    selectedMediaId = ""
                )
            )
            return
        }
        dispatchSelection(playable, startIndex = 0)
    }

    private fun seedQueue(items: List<MediaItem>, startIndex: Int) {
        queue.replace(
            nextItems = items.map { item ->
                Media3QueueItem(
                    queueItemId = item.mediaId,
                    mediaId = PandaMediaSelectionId.engineMediaId(item.mediaId),
                    title = item.mediaMetadata.title?.toString() ?: item.mediaId,
                    artist = item.mediaMetadata.artist?.toString(),
                    album = item.mediaMetadata.albumTitle?.toString(),
                    artworkUri = item.mediaMetadata.artworkUri?.toString(),
                    durationMs = item.mediaMetadata.durationMs?.takeIf { value -> value > 0L }
                )
            },
            currentIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        )
    }

    private fun <T> asyncCatalog(traceName: String, block: () -> T): ListenableFuture<T> = Futures.submit(
        Callable { PandaTrace.section(traceName, block) },
        catalogExecutor
    )

    private fun availableSessionCommands() =
        MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .add(SessionCommand(PandaWaveMediaSessionContract.COMMAND_OPEN_NOW_PLAYING, Bundle.EMPTY))
            .build()
}

internal fun browseHints(
    params: MediaLibraryService.LibraryParams?,
    session: MediaLibrarySession,
    browser: MediaSession.ControllerInfo
): PandaLibraryBrowseHints {
    val extras = params?.extras
    val limit = extras?.takeIf { bundle -> bundle.containsKey(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT) }
        ?.getInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT)
        ?.takeIf { value -> value > 0 }
    val browsableOnly = extras?.getBoolean(MediaConstants.EXTRA_KEY_ROOT_CHILDREN_BROWSABLE_ONLY) == true
    val automotive = session.isAutomotiveController(browser) || session.isAutoCompanionController(browser)
    return PandaLibraryBrowseHints(
        isRecent = params?.isRecent == true,
        isSuggested = params?.isSuggested == true,
        isOffline = params?.isOffline == true,
        childrenLimit = limit,
        browsableOnly = browsableOnly,
        ignoreHostPagination = automotive
    )
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

private const val DEFAULT_SEARCH_PAGE_SIZE = 50
