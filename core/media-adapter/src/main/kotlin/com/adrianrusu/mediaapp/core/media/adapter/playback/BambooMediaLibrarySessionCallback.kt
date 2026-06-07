package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Media3 session callback for platform controllers.
 *
 * Library contents are intentionally minimal until PandaEngine owns catalog
 * and queue state. The root exists so AAOS browsers can connect to PandaWave
 * without receiving an unsupported library response.
 */
internal class BambooMediaLibrarySessionCallback(private val playbackEngineBridge: Media3PlaybackEngineBridge) :
    MediaLibrarySession.Callback {
    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
        LibraryResult.ofItem(
            LibraryItems.Root,
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
            emptyList(),
            params
        )
    )

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int
    ): Int {
        playbackEngineBridge.dispatchPlayerCommand(playerCommand)
        return SessionResult.RESULT_SUCCESS
    }
}

private object LibraryItems {
    const val ROOT_MEDIA_ID = "pandawave.library.root"

    val Root: MediaItem = MediaItem.Builder()
        .setMediaId(ROOT_MEDIA_ID)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("PandaWave")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        )
        .build()
}
