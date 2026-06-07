package com.adrianrusu.mediaapp.appshell.di

import android.content.Context
import com.adrianrusu.mediaapp.appshell.data.InMemoryAppShellRepository
import com.adrianrusu.mediaapp.appshell.domain.AppShellRepository
import com.adrianrusu.mediaapp.appshell.domain.DispatchAppShellIntentUseCase
import com.adrianrusu.mediaapp.appshell.domain.ObserveAppShellStateUseCase
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.PlatformAutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.rust.bridge.engine.RustEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object AppShellModule {
    @Provides
    @ViewModelScoped
    fun provideAutomotiveUxRestrictionObserver(@ApplicationContext context: Context): AutomotiveUxRestrictionObserver =
        PlatformAutomotiveUxRestrictionObserver(context)

    @Provides
    @ViewModelScoped
    fun provideAppShellRepository(
        uxRestrictionObserver: AutomotiveUxRestrictionObserver,
        engine: RustEngine
    ): AppShellRepository = InMemoryAppShellRepository(
        uxRestrictionObserver = uxRestrictionObserver,
        engine = engine
    )

    @Provides
    @ViewModelScoped
    fun provideObserveAppShellStateUseCase(repository: AppShellRepository): ObserveAppShellStateUseCase =
        ObserveAppShellStateUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideDispatchAppShellIntentUseCase(repository: AppShellRepository): DispatchAppShellIntentUseCase =
        DispatchAppShellIntentUseCase(repository)
}
