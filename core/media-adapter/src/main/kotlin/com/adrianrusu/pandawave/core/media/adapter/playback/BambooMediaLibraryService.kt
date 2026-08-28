package com.adrianrusu.pandawave.core.media.adapter.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.adrianrusu.pandawave.core.audio.visualizer.MutableAudioSessionRepository
import com.adrianrusu.pandawave.core.common.trace.PandaTrace
import com.adrianrusu.pandawave.core.media.adapter.R
import com.adrianrusu.pandawave.core.media.adapter.playback.focus.BambooAudioFocusHandler
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.google.common.collect.ImmutableList
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
@UnstableApi
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
    private var sessionPlayer: PandaMediaSessionPlayer? = null
    private var session: MediaLibrarySession? = null
    private var engineBridge: Media3PlaybackEngineBridge? = null
    private var stateProjector: BambooMediaSessionStateProjector? = null
    private var commandAvailabilityProjector: BambooMediaSessionCommandAvailabilityProjector? = null
    private var audioFocusHandler: BambooAudioFocusHandler? = null
    private var audioSessionObserver: ExoPlayerAudioSessionObserver? = null
    private var catalogExecutor: java.util.concurrent.ExecutorService? = null
    private var resumptionStore: MediaSessionPlaybackResumptionStore? = null
    private var generationSubscription: AutoCloseable? = null
    private var lastGenerations: CatalogGenerations = CatalogGenerations()
    private val queue = Media3QueueProjection()
    private val mainThreadHandler = Handler(Looper.getMainLooper())
    private val exoRuntimeListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sessionPlayer?.invalidatePlaybackState()
        }
    }

    override fun onCreate() {
        PandaTrace.section("PW.Media3.Service.onCreate") {
            super.onCreate()
        }
    }

    private fun ensureSession(): MediaLibrarySession {
        session?.let { return it }
        return PandaTrace.section("PW.Media3.Service.ensureSession") {
            createSession()
        }
    }

    private fun createSession(): MediaLibrarySession {
        val exoPlayer = newPlayer()
        player = exoPlayer
        val exoPlayerAudioSessionObserver = ExoPlayerAudioSessionObserver(
            player = exoPlayer,
            repository = audioSessionRepository
        ).also(ExoPlayerAudioSessionObserver::start)
        lateinit var playbackEngineBridge: Media3PlaybackEngineBridge
        val focusHandler = BambooAudioFocusHandler(
            context = this,
            onFocusChange = { change -> playbackEngineBridge.dispatchAudioFocusChange(change) }
        )
        val artworkUris = MediaHostArtworkUriProjector(packageName)
        val effectExecutor = Media3EngineEffectExecutor(
            player = { PlayerMedia3EffectPlayer(checkNotNull(player)) },
            audioFocusController = focusHandler,
            telemetryLogger = telemetryLogger,
            currentProjection = {
                playbackRepository.state.value.toMediaSessionStateProjection(artworkUris = artworkUris)
            },
            recreatePlayer = ::recreatePlayerForDecoderFailure,
            notifyUser = ::showPlaybackFailure,
            onAudioFocusRequestResult = { result ->
                playbackEngineBridge.dispatchAudioFocusRequestResult(result)
            }
        )
        playbackEngineBridge = createEngineBridge(effectExecutor)
        playbackEngineBridge.dispatchVolume(exoPlayer.volume)
        val pandaPlayer = createSessionPlayer(playbackEngineBridge, artworkUris)
        val catalogDispatcher = this.catalogExecutor ?: Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "pw-media-catalog").apply { isDaemon = true }
        }.also { this.catalogExecutor = it }
        val playbackResumptionStore = this.resumptionStore ?: MediaSessionPlaybackResumptionStore(
            getSharedPreferences(RESUMPTION_PREFERENCES, MODE_PRIVATE)
        ).also { this.resumptionStore = it }
        val catalogSource = EngineBambooCatalogSource(engineGateway = engineGateway)
        val sessionBuilder = MediaLibrarySession.Builder(
            this,
            pandaPlayer,
            sessionCallback(
                catalogSource = catalogSource,
                playbackEngineBridge = playbackEngineBridge,
                catalogExecutor = catalogDispatcher,
                resumptionStore = playbackResumptionStore,
                artworkUris = artworkUris
            )
        ).setId(SESSION_ID)
            .setBitmapLoader(CacheBitmapLoader(DataSourceBitmapLoader.Builder(this).build()))
        sessionActivity()?.let(sessionBuilder::setSessionActivity)
        val mediaLibrarySession = sessionBuilder.build()
        applyMediaButtonPreferences(mediaLibrarySession)
        bindSession(
            exoPlayer = exoPlayer,
            pandaPlayer = pandaPlayer,
            playbackEngineBridge = playbackEngineBridge,
            mediaLibrarySession = mediaLibrarySession,
            artworkUris = artworkUris,
            focusHandler = focusHandler,
            exoPlayerAudioSessionObserver = exoPlayerAudioSessionObserver
        )
        return mediaLibrarySession
    }

    private fun createEngineBridge(effectExecutor: Media3EngineEffectExecutor) = Media3PlaybackEngineBridge(
        playbackRepository = playbackRepository,
        telemetryLogger = telemetryLogger,
        effectExecutor = effectExecutor,
        playbackMetricsProvider = {
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
                    playWhenReady = currentPlayer.playWhenReady,
                    playbackState = currentPlayer.playbackState
                )
            }
        },
        checkpointScheduler = { delayMillis, action ->
            val runnable = Runnable(action)
            mainThreadHandler.postDelayed(runnable, delayMillis)
            AutoCloseable { mainThreadHandler.removeCallbacks(runnable) }
        }
    )

    private fun createSessionPlayer(
        playbackEngineBridge: Media3PlaybackEngineBridge,
        artworkUris: ArtworkUriProjector
    ): PandaMediaSessionPlayer = PandaMediaSessionPlayer(
        looper = Looper.getMainLooper(),
        playbackEngineBridge = playbackEngineBridge,
        model = {
            PandaMediaSessionPlayerState.from(
                playback = playbackRepository.state.value,
                queue = queue,
                exo = player?.let(::exoRuntimeState) ?: PandaExoRuntimeState(),
                artworkUris = artworkUris
            )
        },
        seekToQueueIndex = { index, positionMs ->
            val items = queue.snapshot()
            BambooMediaLibraryPlaybackSelection.playbackIntent(
                mediaIds = items.map { item -> item.queueItemId },
                startIndex = index
            )?.let(playbackEngineBridge::dispatchPlayFromContext)
            if (positionMs > 0L) playbackEngineBridge.dispatchSeek(positionMs)
        }
    )

    private fun bindSession(
        exoPlayer: ExoPlayer,
        pandaPlayer: PandaMediaSessionPlayer,
        playbackEngineBridge: Media3PlaybackEngineBridge,
        mediaLibrarySession: MediaLibrarySession,
        artworkUris: ArtworkUriProjector,
        focusHandler: BambooAudioFocusHandler,
        exoPlayerAudioSessionObserver: ExoPlayerAudioSessionObserver
    ) {
        val playbackStateProjector = BambooMediaSessionStateProjector(
            playbackRepository = playbackRepository,
            sink = Media3PlayerStateSink(exoPlayer) { pandaPlayer.invalidatePlaybackState() },
            playbackEngineBridge = playbackEngineBridge,
            artworkUris = artworkUris
        )
        val mediaCommandAvailabilityProjector = BambooMediaSessionCommandAvailabilityProjector(
            playbackRepository = playbackRepository,
            sink = Media3SessionCommandAvailabilitySink(
                sessionProvider = { session },
                hasSeekableTimeline = queue::hasSeekableTimeline
            )
        )
        player = exoPlayer
        sessionPlayer = pandaPlayer
        engineBridge = playbackEngineBridge
        session = mediaLibrarySession
        stateProjector = playbackStateProjector
        commandAvailabilityProjector = mediaCommandAvailabilityProjector
        audioFocusHandler = focusHandler
        audioSessionObserver = exoPlayerAudioSessionObserver
        playbackEngineBridge.bootstrap()
        startForegroundPlaybackService()
        playbackStateProjector.start()
        mediaCommandAvailabilityProjector.start()
        exoPlayer.addListener(playbackEngineBridge)
        exoPlayer.addListener(exoRuntimeListener)
        observeCatalogGenerations(mediaLibrarySession)
    }

    private fun observeCatalogGenerations(mediaLibrarySession: MediaLibrarySession) {
        generationSubscription?.close()
        lastGenerations = engineGateway.snapshot().toCatalogGenerations()
        generationSubscription = engineGateway.observeSnapshots { snapshot ->
            val next = snapshot.toCatalogGenerations()
            val changed = PandaMediaLibraryInvalidation.changedParents(lastGenerations, next)
            lastGenerations = next
            if (changed.isEmpty()) return@observeSnapshots
            mainThreadHandler.post {
                changed.forEach { parentId ->
                    mediaLibrarySession.notifyChildrenChanged(parentId, Int.MAX_VALUE, null)
                }
            }
        }
    }

    private fun recreatePlayerForDecoderFailure() {
        val bridge = engineBridge ?: return
        val previousPlayer = player ?: return
        val pandaPlayer = sessionPlayer ?: return
        PandaTrace.section("PW.Media3.Service.recreatePlayer") {
            previousPlayer.removeListener(bridge)
            previousPlayer.removeListener(exoRuntimeListener)
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
            val projector = BambooMediaSessionStateProjector(
                playbackRepository = playbackRepository,
                sink = Media3PlayerStateSink(exoPlayer) { pandaPlayer.invalidatePlaybackState() },
                playbackEngineBridge = bridge,
                artworkUris = MediaHostArtworkUriProjector(packageName)
            )
            player = exoPlayer
            audioSessionObserver = observer
            stateProjector = projector
            exoPlayer.addListener(bridge)
            exoPlayer.addListener(exoRuntimeListener)
            projector.start()
            pandaPlayer.invalidatePlaybackState()
        }
    }

    private fun sessionCallback(
        catalogSource: EngineBambooCatalogSource,
        playbackEngineBridge: Media3PlaybackEngineBridge,
        catalogExecutor: java.util.concurrent.Executor,
        resumptionStore: MediaSessionPlaybackResumptionStore,
        artworkUris: ArtworkUriProjector
    ) = BambooMediaLibrarySessionCallback(
        catalog = BambooMediaLibraryCatalog(source = catalogSource, artworkUris = artworkUris),
        playbackBridge = playbackEngineBridge,
        queue = queue,
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

    private fun applyMediaButtonPreferences(mediaLibrarySession: MediaLibrarySession) {
        mediaLibrarySession.setMediaButtonPreferences(
            ImmutableList.of(
                bambooMediaButton(
                    icon = CommandButton.ICON_PREVIOUS,
                    command = Player.COMMAND_SEEK_TO_PREVIOUS,
                    displayName = getString(R.string.pandawave_media_button_previous)
                ),
                bambooMediaButton(
                    icon = CommandButton.ICON_PLAY,
                    command = Player.COMMAND_PLAY_PAUSE,
                    displayName = getString(R.string.pandawave_media_button_play_pause)
                ),
                bambooMediaButton(
                    icon = CommandButton.ICON_NEXT,
                    command = Player.COMMAND_SEEK_TO_NEXT,
                    displayName = getString(R.string.pandawave_media_button_next)
                )
            )
        )
    }

    private fun newPlayer(): ExoPlayer = PandaTrace.section("PW.Media3.Player.create") {
        ExoPlayer.Builder(this)
            .setRenderersFactory(DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
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

    private fun startForegroundPlaybackService() {
        val channelId = PLAYBACK_NOTIFICATION_CHANNEL_ID
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            getString(R.string.pandawave_playback_notification_channel),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(androidx.media3.session.R.drawable.media3_notification_small_icon)
            .setContentTitle(getString(R.string.pandawave_playback_notification_title))
            .setContentText(getString(R.string.pandawave_playback_notification_text))
            .setOngoing(true)
            .build()
        startForeground(
            PLAYBACK_FOREGROUND_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        PandaTrace.section("PW.Media3.Service.onGetSession") {
            ensureSession()
        }

    override fun onDestroy() {
        PandaTrace.section("PW.Media3.Service.onDestroy") {
            generationSubscription?.close()
            generationSubscription = null
            audioFocusHandler?.stop()
            audioFocusHandler = null
            commandAvailabilityProjector?.close()
            commandAvailabilityProjector = null
            stateProjector?.close()
            stateProjector = null
            engineBridge?.let { bridge ->
                player?.removeListener(bridge)
                player?.removeListener(exoRuntimeListener)
                bridge.close()
            }
            engineBridge = null
            session?.release()
            session = null
            sessionPlayer = null
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

private fun exoRuntimeState(player: ExoPlayer): PandaExoRuntimeState = PandaExoRuntimeState(
    playbackState = player.playbackState,
    currentMediaId = player.currentMediaItem?.mediaId,
    positionMs = player.currentPosition,
    durationMs = player.duration,
    bufferedPositionMs = player.bufferedPosition
)

private const val HTTP_MAX_BUFFER_MS = 120_000
private const val HTTP_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 10_000
private const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "pandawave_playback"
private const val PLAYBACK_FOREGROUND_NOTIFICATION_ID = 1001
private const val SESSION_ID = "pandawave.media.session"
private const val SESSION_ACTIVITY_REQUEST_CODE = 1
private const val RESUMPTION_PREFERENCES = "pandawave_media_session_resumption"
