@file:OptIn(UnstableApi::class)

package com.adrianrusu.pandawave.core.media.adapter.playback

import android.app.PendingIntent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
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
import java.util.concurrent.Executors
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
    private var catalogExecutor: java.util.concurrent.ExecutorService? = null
    private var resumptionStore: MediaSessionPlaybackResumptionStore? = null
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
                        durationMillis = currentPlayer.duration
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
                }
            )
            // The player owns the initial value; keep shared playback state in sync before projection starts.
            playbackEngineBridge.dispatchVolume(exoPlayer.volume)
            val sessionPlayer = BambooMediaSessionPlayer(
                delegate = exoPlayer,
                playbackEngineBridge = playbackEngineBridge,
                controlsEnabled = { playbackRepository.state.value.canDispatchEngineCommands },
                controls = { playbackRepository.state.value.controls }
            )
            val catalogDispatcher = this.catalogExecutor ?: Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "pw-media-catalog").apply { isDaemon = true }
            }.also { this.catalogExecutor = it }
            val playbackResumptionStore = this.resumptionStore ?: MediaSessionPlaybackResumptionStore(
                getSharedPreferences(RESUMPTION_PREFERENCES, MODE_PRIVATE)
            ).also { this.resumptionStore = it }
            val catalogSource = EngineBambooCatalogSource(engineGateway = engineGateway)
            val sessionBuilder = MediaLibrarySession.Builder(
                this,
                sessionPlayer,
                sessionCallback(
                    catalogSource = catalogSource,
                    playbackEngineBridge = playbackEngineBridge,
                    catalogExecutor = catalogDispatcher,
                    resumptionStore = playbackResumptionStore
                )
            ).setId(SESSION_ID)
            sessionActivity()?.let(sessionBuilder::setSessionActivity)
            val mediaLibrarySession = sessionBuilder.build()
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
     * A fatal decoder error leaves Media3 in an unusable idle state. Swap the
     * session player in place so controllers keep the same MediaLibrarySession,
     * then let the explicit engine effect load the already-resolved source.
     */
    private fun recreatePlayerForDecoderFailure() {
        val bridge = engineBridge ?: return
        val previousPlayer = player ?: return
        val mediaLibrarySession = session ?: return

        PandaTrace.section("PW.Media3.Service.recreatePlayer") {
            previousPlayer.removeListener(bridge)
            audioSessionObserver?.stop()
            audioSessionObserver = null
            stateProjector?.close()
            stateProjector = null
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
            mediaLibrarySession.setPlayer(sessionPlayer)
            val projector = BambooMediaSessionStateProjector(
                playbackRepository = playbackRepository,
                sink = Media3PlayerStateSink(exoPlayer),
                playbackEngineBridge = bridge
            )

            player = exoPlayer
            audioSessionObserver = observer
            stateProjector = projector
            exoPlayer.addListener(bridge)
            projector.start()
        }
    }

    private fun sessionCallback(
        catalogSource: EngineBambooCatalogSource,
        playbackEngineBridge: Media3PlaybackEngineBridge,
        catalogExecutor: java.util.concurrent.Executor,
        resumptionStore: MediaSessionPlaybackResumptionStore
    ) = BambooMediaLibrarySessionCallback(
        controlsEnabled = { playbackRepository.state.value.canDispatchEngineCommands },
        controls = { playbackRepository.state.value.controls },
        catalog = BambooMediaLibraryCatalog(source = catalogSource),
        playbackBridge = playbackEngineBridge,
        sessionPackageName = packageName,
        catalogExecutor = catalogExecutor,
        resumptionStore = resumptionStore,
        playbackState = { playbackRepository.state.value },
        openNowPlaying = {
            nowPlayingLaunchIntent(this)?.let { intent ->
                startActivity(intent)
            }
        }
    )

    private fun sessionActivity(): PendingIntent? {
        val intent = nowPlayingLaunchIntent(this) ?: return null
        return PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun newPlayer(): ExoPlayer = PandaTrace.section("PW.Media3.Player.create") {
        ExoPlayer.Builder(this)
            // Try an alternate platform decoder before full player recreation.
            .setRenderersFactory(DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */
                false
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                        HTTP_MAX_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                        HTTP_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                    )
                    .build()
            )
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
            catalogExecutor?.shutdownNow()
            catalogExecutor = null
            resumptionStore = null
        }

        super.onDestroy()
    }
}

private const val HTTP_MAX_BUFFER_MS = 120_000
private const val HTTP_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 10_000
private const val SESSION_ID = "pandawave.media.session"
private const val SESSION_ACTIVITY_REQUEST_CODE = 1
private const val RESUMPTION_PREFERENCES = "pandawave_media_session_resumption"
