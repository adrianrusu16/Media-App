package com.adrianrusu.pandawave.feature.home.di

import com.adrianrusu.pandawave.feature.home.data.PandaEngineHomeRepository
import com.adrianrusu.pandawave.feature.home.domain.HomeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeModule {
    @Binds
    @Singleton
    abstract fun bindHomeRepository(repository: PandaEngineHomeRepository): HomeRepository
}
