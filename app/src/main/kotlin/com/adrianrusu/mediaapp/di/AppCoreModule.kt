package com.adrianrusu.mediaapp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.adrianrusu.mediaapp.core.automotive.ux.PlatformAutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.model.theme.ThemePreferenceRepository
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import com.adrianrusu.mediaapp.core.playback.DefaultBambooPlaybackRepository
import com.adrianrusu.mediaapp.core.preferences.DataStoreThemePreferenceRepository
import com.adrianrusu.mediaapp.core.preferences.DefaultThemePreferenceCoordinator
import com.adrianrusu.mediaapp.core.preferences.ThemePreferenceCoordinator
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.AidlEngineGateway
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.AndroidEngineServiceConnection
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.EngineServiceConnection
import com.adrianrusu.mediaapp.core.telemetry.TelemetryLogger
import com.adrianrusu.mediaapp.core.telemetry.TelemetrySink
import com.adrianrusu.mediaapp.core.telemetry.sinks.AndroidLogTelemetrySink
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
        telemetryLogger = telemetryLogger
    )

    @Provides
    @Singleton
    fun provideTelemetrySink(): TelemetrySink = AndroidLogTelemetrySink()

    @Provides
    @Singleton
    fun provideTelemetryLogger(sink: TelemetrySink): TelemetryLogger = TelemetryLogger(sink = sink)

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
