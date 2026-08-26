package com.adrianrusu.pandawave.feature.search.di

import com.adrianrusu.pandawave.feature.search.data.PandaEngineSearchRepository
import com.adrianrusu.pandawave.feature.search.domain.SearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {
    @Binds
    @Singleton
    abstract fun bindSearchRepository(repository: PandaEngineSearchRepository): SearchRepository
}
