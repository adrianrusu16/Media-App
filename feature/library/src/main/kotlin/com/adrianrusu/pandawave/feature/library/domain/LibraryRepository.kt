package com.adrianrusu.pandawave.feature.library.domain

import kotlinx.coroutines.flow.StateFlow

interface LibraryRepository : AutoCloseable {
    val state: StateFlow<LibraryState>

    fun start()
    fun selectTab(tab: LibraryTab)
    fun refresh()
    fun loadNext(tab: LibraryTab)
    fun save(mediaId: String)
    fun removeSaved(mediaId: String)
    fun like(mediaId: String)
    fun unlike(mediaId: String)
}
