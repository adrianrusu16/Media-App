package com.adrianrusu.pandawave.feature.settings.di

import com.adrianrusu.pandawave.core.audio.visualizer.VisualizerPermissionRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.preferences.AmbientModePreferenceRepository
import com.adrianrusu.pandawave.core.preferences.ThemePreferenceCoordinator
import com.adrianrusu.pandawave.feature.settings.data.InMemorySettingsRepository
import com.adrianrusu.pandawave.feature.settings.domain.DispatchSettingsIntentUseCase
import com.adrianrusu.pandawave.feature.settings.domain.ObserveSettingsStateUseCase
import com.adrianrusu.pandawave.feature.settings.domain.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object SettingsModule {
    @Provides
    @ViewModelScoped
    fun provideSettingsRepository(
        playbackRepository: BambooPlaybackRepository,
        themePreferenceCoordinator: ThemePreferenceCoordinator,
        ambientModePreferenceRepository: AmbientModePreferenceRepository,
        visualizerPermissionRepository: VisualizerPermissionRepository
    ): SettingsRepository = InMemorySettingsRepository(
        playbackRepository = playbackRepository,
        themePreferenceCoordinator = themePreferenceCoordinator,
        ambientModePreferenceRepository = ambientModePreferenceRepository,
        visualizerPermissionRepository = visualizerPermissionRepository
    )

    @Provides
    @ViewModelScoped
    fun provideObserveSettingsStateUseCase(repository: SettingsRepository): ObserveSettingsStateUseCase =
        ObserveSettingsStateUseCase(repository)

    @Provides
    @ViewModelScoped
    fun provideDispatchSettingsIntentUseCase(repository: SettingsRepository): DispatchSettingsIntentUseCase =
        DispatchSettingsIntentUseCase(repository)
}
