package com.adrianrusu.pandawave.feature.library.di

import com.adrianrusu.pandawave.feature.library.data.PandaEngineLibraryRepository
import com.adrianrusu.pandawave.feature.library.domain.LibraryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {
    @Binds
    @Singleton
    abstract fun bindLibraryRepository(repository: PandaEngineLibraryRepository): LibraryRepository
}
