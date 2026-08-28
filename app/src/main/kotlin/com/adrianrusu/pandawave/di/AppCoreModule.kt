package com.adrianrusu.pandawave.di

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.adrianrusu.pandawave.core.audio.visualizer.AudioSessionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.InMemoryAudioSessionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.MutableAudioSessionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionRepository
import com.adrianrusu.pandawave.core.automotive.driving.AutomotiveDrivingStateObserver
import com.adrianrusu.pandawave.core.automotive.driving.PlatformAutomotiveDrivingStateObserver
import com.adrianrusu.pandawave.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.pandawave.core.automotive.ux.PlatformAutomotiveUxRestrictionObserver
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.media.adapter.playback.BambooMediaSessionWarmup
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackMediaPipelineGate
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.DefaultBambooPlaybackRepository
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceRepository
import com.adrianrusu.pandawave.core.preferences.DataStoreAmbientModePreferenceRepository
import com.adrianrusu.pandawave.core.preferences.DataStoreThemePreferenceRepository
import com.adrianrusu.pandawave.core.preferences.DefaultThemePreferenceCoordinator
import com.adrianrusu.pandawave.core.preferences.ThemePreferenceCoordinator
import com.adrianrusu.pandawave.core.rust.bridge.gateway.AidlEngineGateway
import com.adrianrusu.pandawave.core.rust.bridge.gateway.AndroidEngineServiceConnection
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineAuthGateway
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineServiceConnection
import com.adrianrusu.pandawave.core.telemetry.TelemetryBreadcrumbStore
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryPolicy
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import com.adrianrusu.pandawave.core.telemetry.sinks.AndroidLogTelemetrySink
import com.adrianrusu.pandawave.core.telemetry.sinks.CompositeTelemetrySink
import com.adrianrusu.pandawave.core.telemetry.sinks.InMemoryBreadcrumbTelemetrySink
import com.adrianrusu.pandawave.core.ui.interaction.MonotonicClock
import com.adrianrusu.pandawave.core.ui.interaction.SystemMonotonicClock
import com.adrianrusu.pandawave.core.ui.interaction.UserInteractionTracker
import com.adrianrusu.pandawave.permission.DataStoreVisualizerPermissionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object AppCoreModule {
    @Provides
    @Singleton
    fun provideVisualizerPermissionRepository(
        @ApplicationContext context: Context,
        dataStore: DataStore<Preferences>,
        @ApplicationScope scope: CoroutineScope
    ): VisualizerPermissionRepository = DataStoreVisualizerPermissionRepository(
        context = context,
        dataStore = dataStore,
        scope = scope
    )

    @Provides
    @Singleton
    fun provideMonotonicClock(): MonotonicClock = SystemMonotonicClock()

    @Provides
    @Singleton
    fun provideUserInteractionTracker(): UserInteractionTracker = UserInteractionTracker()

    @Provides
    @Singleton
    fun provideMutableAudioSessionRepository(): InMemoryAudioSessionRepository = InMemoryAudioSessionRepository()

    @Provides
    @Singleton
    fun provideAudioSessionRepository(repository: InMemoryAudioSessionRepository): AudioSessionRepository = repository

    @Provides
    @Singleton
    fun provideAudioSessionPublisher(repository: InMemoryAudioSessionRepository): MutableAudioSessionRepository =
        repository

    @Provides
    @Singleton
    fun provideEngineServiceConnection(@ApplicationContext context: Context): EngineServiceConnection =
        AndroidEngineServiceConnection(context = context)

    @Provides
    @Singleton
    fun provideAidlEngineGateway(
        connection: EngineServiceConnection,
        telemetryLogger: TelemetryLogger
    ): AidlEngineGateway {
        val mainHandler = Handler(Looper.getMainLooper())
        return AidlEngineGateway(
            connection = connection,
            telemetryLogger = telemetryLogger,
            // AIDL listener calls arrive on Binder threads. All listeners below
            // eventually project into Media3, whose player and session are owned
            // by the main looper, so delivery must cross that boundary here.
            callbackExecutor = java.util.concurrent.Executor { runnable ->
                mainHandler.post(runnable)
            }
        )
    }

    @Provides
    @Singleton
    fun provideEngineGateway(gateway: AidlEngineGateway): EngineGateway = gateway

    @Provides
    @Singleton
    fun provideEngineAuthGateway(gateway: AidlEngineGateway): EngineAuthGateway = gateway

    @Provides
    @Singleton
    fun provideBambooPlaybackMediaPipelineGate(
        mediaSessionWarmup: BambooMediaSessionWarmup
    ): BambooPlaybackMediaPipelineGate = BambooPlaybackMediaPipelineGate {
        mediaSessionWarmup.reconnect()
    }

    @Provides
    @Singleton
    fun provideBambooPlaybackRepository(
        engine: EngineGateway,
        @ApplicationContext context: Context,
        telemetryLogger: TelemetryLogger,
        mediaPipelineGate: BambooPlaybackMediaPipelineGate
    ): BambooPlaybackRepository {
        val isAutomotive = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        val mainHandler = Handler(Looper.getMainLooper())
        val engineDispatchExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "pw-playback-dispatch").apply { isDaemon = true }
        }
        return DefaultBambooPlaybackRepository(
            engine = engine,
            uxRestrictionObserver = if (isAutomotive) {
                PlatformAutomotiveUxRestrictionObserver(context)
            } else {
                AutomotiveUxRestrictionObserver.Unavailable
            },
            drivingStateObserver = if (isAutomotive) {
                PlatformAutomotiveDrivingStateObserver(context)
            } else {
                AutomotiveDrivingStateObserver.Unavailable
            },
            telemetryLogger = telemetryLogger,
            engineDispatchExecutor = engineDispatchExecutor,
            resultExecutor = java.util.concurrent.Executor { runnable ->
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    runnable.run()
                } else {
                    mainHandler.post(runnable)
                }
            },
            mediaPipelineGate = mediaPipelineGate
        )
    }

    @Provides
    @Singleton
    fun provideBreadcrumbSink(): InMemoryBreadcrumbTelemetrySink = InMemoryBreadcrumbTelemetrySink()

    @Provides
    @Singleton
    fun provideTelemetryBreadcrumbStore(sink: InMemoryBreadcrumbTelemetrySink): TelemetryBreadcrumbStore = sink

    @Provides
    @Singleton
    fun provideTelemetrySink(breadcrumbSink: InMemoryBreadcrumbTelemetrySink): TelemetrySink = CompositeTelemetrySink(
        sinks = listOf(
            AndroidLogTelemetrySink(),
            breadcrumbSink
        )
    )

    @Provides
    @Singleton
    @AppDebuggable
    fun provideAppDebuggable(@ApplicationContext context: Context): Boolean =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    @Provides
    @Singleton
    fun provideTelemetryPolicy(@AppDebuggable isDebuggable: Boolean): TelemetryPolicy {
        PandaLog.setDebuggable(isDebuggable)
        return if (isDebuggable) {
            TelemetryPolicy.developer()
        } else {
            TelemetryPolicy.production()
        }
    }

    @Provides
    @Singleton
    fun provideTelemetryLogger(sink: TelemetrySink, policy: TelemetryPolicy): TelemetryLogger = TelemetryLogger(
        sink = sink,
        policy = policy
    )

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope
    ): DataStore<Preferences> = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { context.preferencesDataStoreFile(PREFERENCES_FILE_NAME) }
    )

    @Provides
    @Singleton
    fun provideThemePreferenceRepository(
        dataStore: DataStore<Preferences>,
        @ApplicationScope scope: CoroutineScope,
        telemetryLogger: TelemetryLogger
    ): ThemePreferenceRepository = DataStoreThemePreferenceRepository(
        dataStore = dataStore,
        scope = scope,
        telemetryLogger = telemetryLogger
    )

    @Provides
    @Singleton
    fun provideAmbientModePreferenceRepository(
        dataStore: DataStore<Preferences>,
        @ApplicationScope scope: CoroutineScope,
        telemetryLogger: TelemetryLogger
    ): AmbientModePreferenceRepository = DataStoreAmbientModePreferenceRepository(
        dataStore = dataStore,
        scope = scope,
        telemetryLogger = telemetryLogger
    )

    @Provides
    @Singleton
    fun provideThemePreferenceCoordinator(
        repository: ThemePreferenceRepository,
        engineGateway: EngineGateway,
        @ApplicationScope scope: CoroutineScope
    ): ThemePreferenceCoordinator = DefaultThemePreferenceCoordinator(
        repository = repository,
        engineGateway = engineGateway,
        scope = scope
    )

    private const val PREFERENCES_FILE_NAME = "pandawave.preferences_pb"
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class AppDebuggable
