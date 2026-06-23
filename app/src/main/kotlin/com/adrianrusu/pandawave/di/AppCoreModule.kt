package com.adrianrusu.pandawave.di

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.adrianrusu.pandawave.core.audio.visualizer.AudioSessionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.InMemoryAudioSessionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.MutableAudioSessionRepository
import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionRepository
import com.adrianrusu.pandawave.core.automotive.driving.PlatformAutomotiveDrivingStateObserver
import com.adrianrusu.pandawave.core.automotive.ux.PlatformAutomotiveUxRestrictionObserver
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.DefaultBambooPlaybackRepository
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceRepository
import com.adrianrusu.pandawave.core.preferences.DataStoreAmbientModePreferenceRepository
import com.adrianrusu.pandawave.core.preferences.DataStoreThemePreferenceRepository
import com.adrianrusu.pandawave.core.preferences.DefaultThemePreferenceCoordinator
import com.adrianrusu.pandawave.core.preferences.ThemePreferenceCoordinator
import com.adrianrusu.pandawave.core.rust.bridge.gateway.AidlEngineGateway
import com.adrianrusu.pandawave.core.rust.bridge.gateway.AndroidEngineServiceConnection
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
    fun provideEngineGateway(connection: EngineServiceConnection, telemetryLogger: TelemetryLogger): EngineGateway =
        AidlEngineGateway(
            connection = connection,
            telemetryLogger = telemetryLogger
        )

    @Provides
    @Singleton
    fun provideBambooPlaybackRepository(
        engine: EngineGateway,
        @ApplicationContext context: Context,
        telemetryLogger: TelemetryLogger
    ): BambooPlaybackRepository = DefaultBambooPlaybackRepository(
        engine = engine,
        uxRestrictionObserver = PlatformAutomotiveUxRestrictionObserver(context),
        drivingStateObserver = PlatformAutomotiveDrivingStateObserver(context),
        telemetryLogger = telemetryLogger
    )

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
    fun provideTelemetryPolicy(@AppDebuggable isDebuggable: Boolean): TelemetryPolicy = if (isDebuggable) {
        TelemetryPolicy.developer()
    } else {
        TelemetryPolicy.production()
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
