package com.adrianrusu.mediaapp.appshell.di

import com.adrianrusu.mediaapp.appshell.data.InMemoryAppShellRepository
import com.adrianrusu.mediaapp.appshell.domain.AppShellRepository
import com.adrianrusu.mediaapp.appshell.domain.DispatchAppShellIntentUseCase
import com.adrianrusu.mediaapp.appshell.domain.ObserveAppShellStateUseCase
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object AppShellModule {
    @Provides
    @ViewModelScoped
    fun provideAppShellRepository(playbackRepository: BambooPlaybackRepository): AppShellRepository =
        InMemoryAppShellRepository(
            playbackRepository = playbackRepository
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
