package com.adrianrusu.pandawave.feature.nowplaying.di

import com.adrianrusu.pandawave.core.audio.visualizer.AmbientAudioVisualizer
import com.adrianrusu.pandawave.core.audio.visualizer.AndroidFftAudioVisualizer
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.feature.nowplaying.data.InMemoryNowPlayingRepository
import com.adrianrusu.pandawave.feature.nowplaying.domain.DispatchNowPlayingIntentUseCase
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingRepository
import com.adrianrusu.pandawave.feature.nowplaying.domain.ObserveNowPlayingStateUseCase
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

    @Provides
    @ViewModelScoped
    fun provideAmbientAudioVisualizer(): AmbientAudioVisualizer = AndroidFftAudioVisualizer()
}
