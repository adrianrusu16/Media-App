package com.adrianrusu.mediaapp.di

import com.adrianrusu.mediaapp.core.rust.bridge.engine.FakeRustEngineFactory
import com.adrianrusu.mediaapp.core.rust.bridge.engine.RustEngine
import com.adrianrusu.mediaapp.core.telemetry.TelemetryLogger
import com.adrianrusu.mediaapp.core.telemetry.TelemetrySink
import com.adrianrusu.mediaapp.core.telemetry.sinks.AndroidLogTelemetrySink
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppCoreModule {
    @Provides
    @Singleton
    fun provideRustEngine(): RustEngine = FakeRustEngineFactory.create()

    @Provides
    @Singleton
    fun provideTelemetrySink(): TelemetrySink = AndroidLogTelemetrySink()

    @Provides
    @Singleton
    fun provideTelemetryLogger(sink: TelemetrySink): TelemetryLogger = TelemetryLogger(sink = sink)
}
