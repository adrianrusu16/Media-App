package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.adrianrusu.pandawave.core.audio.visualizer.MutableAudioSessionRepository
import com.adrianrusu.pandawave.core.media.adapter.playback.focus.BambooAudioFocusHandler
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
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
    lateinit var engineGateway: EngineGateway

    @Inject
    lateinit var telemetryLogger: TelemetryLogger

    @Inject
    lateinit var audioSessionRepository: MutableAudioSessionRepository

    private var player: ExoPlayer? = null
    private var session: MediaLibrarySession? = null
    private var engineBridge: Media3PlaybackEngineBridge? = null
    private var stateProjector: BambooMediaSessionStateProjector? = null
    private var commandAvailabilityProjector: BambooMediaSessionCommandAvailabilityProjector? = null
    private var audioFocusHandler: BambooAudioFocusHandler? = null
    private var audioSessionObserver: ExoPlayerAudioSessionObserver? = null

    override fun onCreate() {
        super.onCreate()

        val exoPlayer = ExoPlayer.Builder(this).build()
        val exoPlayerAudioSessionObserver = ExoPlayerAudioSessionObserver(
            player = exoPlayer,
            repository = audioSessionRepository
        ).also(ExoPlayerAudioSessionObserver::start)
        lateinit var playbackEngineBridge: Media3PlaybackEngineBridge
        val focusHandler = BambooAudioFocusHandler(
            context = this,
            onFocusChange = {
                playbackEngineBridge.dispatchPlatformEvent(
                    com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent.TYPE_AUDIO_FOCUS_CHANGED
                )
            }
        )
        val effectExecutor = Media3EngineEffectExecutor(
            player = PlayerMedia3EffectPlayer(exoPlayer),
            audioFocusController = focusHandler,
            telemetryLogger = telemetryLogger,
            currentProjection = { playbackRepository.state.value.toMediaSessionStateProjection() }
        )
        playbackEngineBridge = Media3PlaybackEngineBridge(
            playbackRepository = playbackRepository,
            telemetryLogger = telemetryLogger,
            effectExecutor = effectExecutor,
            playbackMetricsProvider = PlaybackCompletionMetricsProvider {
                PlaybackCompletionMetrics(
                    positionMillis = exoPlayer.currentPosition,
                    durationMillis = exoPlayer.duration,
                )
            },
        )
        val sessionPlayer = BambooMediaSessionPlayer(
            delegate = exoPlayer,
            playbackEngineBridge = playbackEngineBridge,
            controlsEnabled = { playbackRepository.state.value.canDispatchEngineCommands }
        )
        val catalogSource = EngineBambooCatalogSource(
            playbackBridge = playbackEngineBridge,
            engineGateway = engineGateway
        )
        val mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            sessionPlayer,
            BambooMediaLibrarySessionCallback(
                controlsEnabled = { playbackRepository.state.value.canDispatchEngineCommands },
                catalog = BambooMediaLibraryCatalog(
                    source = catalogSource
                ),
                playbackBridge = playbackEngineBridge
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

        player = exoPlayer
        engineBridge = playbackEngineBridge
        session = mediaLibrarySession
        stateProjector = playbackStateProjector
        commandAvailabilityProjector = mediaCommandAvailabilityProjector
        audioFocusHandler = focusHandler
        audioSessionObserver = exoPlayerAudioSessionObserver

        playbackEngineBridge.bootstrap()
        playbackStateProjector.start()
        mediaCommandAvailabilityProjector.start()
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
        audioSessionObserver?.stop()
        audioSessionObserver = null
        player?.release()
        player = null

        super.onDestroy()
    }
}
