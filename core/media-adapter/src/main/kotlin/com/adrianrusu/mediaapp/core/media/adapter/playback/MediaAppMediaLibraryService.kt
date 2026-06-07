package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.adrianrusu.mediaapp.core.rust.bridge.engine.FakeRustEngineFactory

/**
 * Media3 service that exposes playback to AAOS media center, system controls,
 * widgets, and other platform media controllers.
 *
 * The service owns Android platform objects only. Catalog, user, and playback
 * decisions remain behind the Rust engine boundary and will be wired through
 * the media adapter as the engine grows.
 */
class MediaAppMediaLibraryService : MediaLibraryService() {
    private var player: ExoPlayer? = null
    private var session: MediaLibrarySession? = null
    private var engineBridge: Media3PlaybackEngineBridge? = null

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this).build()
        val playbackEngineBridge = Media3PlaybackEngineBridge(
            engine = FakeRustEngineFactory.create()
        )
        playbackEngineBridge.bootstrap()
        exoPlayer.addListener(playbackEngineBridge)

        player = exoPlayer
        engineBridge = playbackEngineBridge
        session = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            EmptyMediaLibrarySessionCallback
        ).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        engineBridge?.let { bridge ->
            player?.removeListener(bridge)
        }
        engineBridge = null
        session?.release()
        session = null
        player?.release()
        player = null

        super.onDestroy()
    }
}
