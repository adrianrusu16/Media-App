package com.adrianrusu.mediaapp.feature.settings.di

import android.content.Context
import com.adrianrusu.mediaapp.core.automotive.ux.PlatformAutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.feature.settings.data.InMemorySettingsRepository
import com.adrianrusu.mediaapp.feature.settings.domain.DispatchSettingsIntentUseCase
import com.adrianrusu.mediaapp.feature.settings.domain.ObserveSettingsStateUseCase
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object SettingsModule {
    @Provides
    @ViewModelScoped
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        InMemorySettingsRepository(
            uxRestrictionObserver = PlatformAutomotiveUxRestrictionObserver(context)
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
