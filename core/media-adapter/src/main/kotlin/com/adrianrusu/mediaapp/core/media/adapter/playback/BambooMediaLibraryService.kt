package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.adrianrusu.mediaapp.core.rust.bridge.engine.RustEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Media3 service that exposes playback to AAOS media center, system controls,
 * widgets, and other platform media controllers.
 *
 * The service owns Android platform objects only. Catalog, user, and playback
 * decisions remain behind the Rust engine boundary and will be wired through
 * the media adapter as the engine grows.
 */
@AndroidEntryPoint
class BambooMediaLibraryService : MediaLibraryService() {
    @Inject
    lateinit var engine: RustEngine

    private var player: ExoPlayer? = null
    private var session: MediaLibrarySession? = null
    private var engineBridge: Media3PlaybackEngineBridge? = null

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this).build()
        val playbackEngineBridge = Media3PlaybackEngineBridge(
            engine = engine
        )
        playbackEngineBridge.bootstrap()
        exoPlayer.addListener(playbackEngineBridge)
        val sessionPlayer = BambooMediaSessionPlayer(
            delegate = exoPlayer,
            playbackEngineBridge = playbackEngineBridge
        )

        player = exoPlayer
        engineBridge = playbackEngineBridge
        session = MediaLibrarySession.Builder(
            this,
            sessionPlayer,
            BambooMediaLibrarySessionCallback
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
