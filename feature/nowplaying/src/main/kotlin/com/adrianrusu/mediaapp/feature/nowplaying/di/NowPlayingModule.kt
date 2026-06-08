package com.adrianrusu.mediaapp.feature.nowplaying.di

import com.adrianrusu.mediaapp.core.playback.BambooPlaybackRepository
import com.adrianrusu.mediaapp.feature.nowplaying.data.InMemoryNowPlayingRepository
import com.adrianrusu.mediaapp.feature.nowplaying.domain.DispatchNowPlayingIntentUseCase
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingRepository
import com.adrianrusu.mediaapp.feature.nowplaying.domain.ObserveNowPlayingStateUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object NowPlayingModule {
    @Provides
    @ViewModelScoped
    fun provideNowPlayingRepository(playbackRepository: BambooPlaybackRepository): NowPlayingRepository =
        InMemoryNowPlayingRepository(
            playbackRepository = playbackRepository
        )

    @Provides
    @ViewModelScoped
    fun provideObserveNowPlayingStateUseCase(repository: NowPlayingRepository): ObserveNowPlayingStateUseCase =
        ObserveNowPlayingStateUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideDispatchNowPlayingIntentUseCase(repository: NowPlayingRepository): DispatchNowPlayingIntentUseCase =
        DispatchNowPlayingIntentUseCase(repository)
}
