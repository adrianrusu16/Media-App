package com.adrianrusu.mediaapp.core.media.adapter.playback

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.adrianrusu.mediaapp.core.media.adapter.playback.focus.BambooAudioFocusHandler
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import com.adrianrusu.mediaapp.core.telemetry.TelemetryLogger
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

    @Inject
    lateinit var telemetryLogger: TelemetryLogger

    private var player: ExoPlayer? = null
    private var session: MediaLibrarySession? = null
    private var engineBridge: Media3PlaybackEngineBridge? = null
    private var stateProjector: BambooMediaSessionStateProjector? = null
    private var commandAvailabilityProjector: BambooMediaSessionCommandAvailabilityProjector? = null
    private var audioFocusHandler: BambooAudioFocusHandler? = null

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this).build()
        val playbackEngineBridge = Media3PlaybackEngineBridge(
            playbackRepository = playbackRepository,
            telemetryLogger = telemetryLogger
        )
        val sessionPlayer = BambooMediaSessionPlayer(
            delegate = exoPlayer,
            playbackEngineBridge = playbackEngineBridge,
            controlsEnabled = { playbackRepository.state.value.canDispatchEngineCommands }
        )
        val catalogSource = EngineBambooCatalogSource(playbackEngineBridge)
        val mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            sessionPlayer,
            BambooMediaLibrarySessionCallback(
                controlsEnabled = { playbackRepository.state.value.canDispatchEngineCommands },
                catalog = BambooMediaLibraryCatalog(
                    source = catalogSource
                )
            )
        ).build()
        val playbackStateProjector = BambooMediaSessionStateProjector(
            playbackRepository = playbackRepository,
            sink = Media3PlayerStateSink(exoPlayer),
            playbackEngineBridge = playbackEngineBridge
        )
        val mediaCommandAvailabilityProjector = BambooMediaSessionCommandAvailabilityProjector(
            playbackRepository = playbackRepository,
            sink = Media3SessionCommandAvailabilitySink(
                sessionProvider = { session }
            )
        )

        val focusHandler = BambooAudioFocusHandler(
            context = this,
            onFocusChange = {
                playbackEngineBridge.dispatchPlatformEvent(
                    com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent.TYPE_AUDIO_FOCUS_CHANGED
                )
            }
        )

        player = exoPlayer
        engineBridge = playbackEngineBridge
        session = mediaLibrarySession
        stateProjector = playbackStateProjector
        commandAvailabilityProjector = mediaCommandAvailabilityProjector
        audioFocusHandler = focusHandler

        playbackEngineBridge.bootstrap()
        playbackStateProjector.start()
        mediaCommandAvailabilityProjector.start()
        focusHandler.start()
        exoPlayer.addListener(playbackEngineBridge)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        audioFocusHandler?.stop()
        audioFocusHandler = null
        commandAvailabilityProjector?.close()
        commandAvailabilityProjector = null
        stateProjector?.close()
        stateProjector = null
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
