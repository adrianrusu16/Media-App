package com.adrianrusu.pandawave.feature.profile.di

import com.adrianrusu.pandawave.feature.profile.data.PandaEngineProfileRepository
import com.adrianrusu.pandawave.feature.profile.domain.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileModule {
    @Binds
    @Singleton
    abstract fun bindProfileRepository(repository: PandaEngineProfileRepository): ProfileRepository
}
