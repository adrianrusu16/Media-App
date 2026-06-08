package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Media3 service that exposes playback to AAOS media center, system controls,
 * widgets, and other platform media controllers.
 *
 * The service owns Android platform objects only. Catalog, user, and playback
 * decisions flow through Bamboo playback state before crossing into PandaEngine.
 */
@AndroidEntryPoint
class BambooMediaLibraryService : MediaLibraryService() {
    @Inject
    lateinit var playbackRepository: BambooPlaybackRepository

    private var player: ExoPlayer? = null
    private var session: MediaLibrarySession? = null
    private var engineBridge: Media3PlaybackEngineBridge? = null

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this).build()
        val playbackEngineBridge = Media3PlaybackEngineBridge(
            playbackRepository = playbackRepository
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
            bridge.close()
        }
        engineBridge = null
        session?.release()
        session = null
        player?.release()
        player = null

        super.onDestroy()
    }
}
