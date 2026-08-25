package com.adrianrusu.pandawave.core.media.adapter.playback

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.adrianrusu.pandawave.core.audio.visualizer.MutableAudioSessionRepository
import com.adrianrusu.pandawave.core.common.trace.PandaTrace
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
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        PandaTrace.section("PW.Media3.Service.onCreate") {
            super.onCreate()
        }
    }

    private fun ensureSession(): MediaLibrarySession {
        session?.let { return it }
        return PandaTrace.section("PW.Media3.Service.ensureSession") {
            val exoPlayer = newPlayer()
            val exoPlayerAudioSessionObserver = ExoPlayerAudioSessionObserver(
                player = exoPlayer,
                repository = audioSessionRepository
            ).also(ExoPlayerAudioSessionObserver::start)
            lateinit var playbackEngineBridge: Media3PlaybackEngineBridge
            val focusHandler = BambooAudioFocusHandler(
                context = this,
                onFocusChange = { change -> playbackEngineBridge.dispatchAudioFocusChange(change) }
            )
            val effectExecutor = Media3EngineEffectExecutor(
                player = { PlayerMedia3EffectPlayer(checkNotNull(player)) },
                audioFocusController = focusHandler,
                telemetryLogger = telemetryLogger,
                currentProjection = { playbackRepository.state.value.toMediaSessionStateProjection() },
                recreatePlayer = ::recreatePlayerForDecoderFailure,
                notifyUser = ::showPlaybackFailure,
                onAudioFocusRequestResult = { result ->
                    playbackEngineBridge.dispatchAudioFocusRequestResult(result)
                }
            )
            playbackEngineBridge = Media3PlaybackEngineBridge(
                playbackRepository = playbackRepository,
                telemetryLogger = telemetryLogger,
                effectExecutor = effectExecutor,
                playbackMetricsProvider = PlaybackCompletionMetricsProvider {
                    val currentPlayer = checkNotNull(player)
                    PlaybackCompletionMetrics(
                        positionMillis = currentPlayer.currentPosition,
                        durationMillis = currentPlayer.duration,
                    )
                },
                playbackInstanceIdProvider = {
                    player?.currentMediaItem?.localConfiguration?.tag as? Long
                },
                playerSnapshotProvider = {
                    player?.let { currentPlayer ->
                        Media3PlayerSnapshot(
                            positionMillis = currentPlayer.currentPosition,
                            playWhenReady = currentPlayer.playWhenReady
                        )
                    }
                },
                checkpointScheduler = PlaybackCheckpointScheduler { delayMillis, action ->
                    val runnable = Runnable(action)
                    mainThreadHandler.postDelayed(runnable, delayMillis)
                    AutoCloseable { mainThreadHandler.removeCallbacks(runnable) }
                },
            )
            // The player owns the initial value; keep shared playback state in sync before projection starts.
            playbackEngineBridge.dispatchVolume(exoPlayer.volume)
            val sessionPlayer = BambooMediaSessionPlayer(
                delegate = exoPlayer,
                playbackEngineBridge = playbackEngineBridge,
                controlsEnabled = { playbackRepository.state.value.canDispatchEngineCommands },
                controls = { playbackRepository.state.value.controls }
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
                    controls = { playbackRepository.state.value.controls },
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
            mediaLibrarySession
        }
    }

    /**
     * A fatal decoder error leaves Media3 in an unusable idle state. Rebuild every
     * Android-owned object that holds the old player, then let the explicit engine
     * effect load the same already-resolved source under a new instance id.
     */
    private fun recreatePlayerForDecoderFailure() {
        val bridge = engineBridge ?: return
        val focusHandler = audioFocusHandler ?: return
        val previousPlayer = player ?: return

        PandaTrace.section("PW.Media3.Service.recreatePlayer") {
            previousPlayer.removeListener(bridge)
            audioSessionObserver?.stop()
            audioSessionObserver = null
            stateProjector?.close()
            stateProjector = null
            session?.release()
            session = null
            previousPlayer.release()

            val exoPlayer = newPlayer()
            val observer = ExoPlayerAudioSessionObserver(
                player = exoPlayer,
                repository = audioSessionRepository
            ).also(ExoPlayerAudioSessionObserver::start)
            val sessionPlayer = BambooMediaSessionPlayer(
                delegate = exoPlayer,
                playbackEngineBridge = bridge,
                controlsEnabled = { playbackRepository.state.value.canDispatchEngineCommands },
                controls = { playbackRepository.state.value.controls }
            )
            val mediaLibrarySession = MediaLibrarySession.Builder(
                this,
                sessionPlayer,
                BambooMediaLibrarySessionCallback(
                    controlsEnabled = { playbackRepository.state.value.canDispatchEngineCommands },
                    controls = { playbackRepository.state.value.controls },
                    catalog = BambooMediaLibraryCatalog(
                        source = EngineBambooCatalogSource(
                            playbackBridge = bridge,
                            engineGateway = engineGateway
                        )
                    ),
                    playbackBridge = bridge
                )
            ).build()
            val projector = BambooMediaSessionStateProjector(
                playbackRepository = playbackRepository,
                sink = Media3PlayerStateSink(exoPlayer),
                playbackEngineBridge = bridge
            )

            player = exoPlayer
            session = mediaLibrarySession
            audioSessionObserver = observer
            stateProjector = projector
            exoPlayer.addListener(bridge)
            projector.start()
        }
    }

    private fun newPlayer(): ExoPlayer = PandaTrace.section("PW.Media3.Player.create") {
        ExoPlayer.Builder(this)
            // Try an alternate platform decoder before full player recreation.
            .setRenderersFactory(DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .build()
    }

    private fun showPlaybackFailure(message: String) {
        mainThreadHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        PandaTrace.section("PW.Media3.Service.onGetSession") {
            ensureSession()
        }

    override fun onDestroy() {
        PandaTrace.section("PW.Media3.Service.onDestroy") {
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
        }

        super.onDestroy()
    }
}
