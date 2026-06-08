package com.adrianrusu.mediaapp.di

import android.content.Context
import com.adrianrusu.mediaapp.core.automotive.ux.PlatformAutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import com.adrianrusu.mediaapp.core.playback.DefaultBambooPlaybackRepository
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
import javax.inject.Singleton

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
        @ApplicationContext context: Context
    ): BambooPlaybackRepository = DefaultBambooPlaybackRepository(
        engine = engine,
        uxRestrictionObserver = PlatformAutomotiveUxRestrictionObserver(context)
    )

    @Provides
    @Singleton
    fun provideTelemetrySink(): TelemetrySink = AndroidLogTelemetrySink()

    @Provides
    @Singleton
    fun provideTelemetryLogger(sink: TelemetrySink): TelemetryLogger = TelemetryLogger(sink = sink)
}
