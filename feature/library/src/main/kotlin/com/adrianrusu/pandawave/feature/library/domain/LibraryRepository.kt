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
    fun createPlaylist(name: String, description: String?)
    fun updatePlaylist(playlistId: String, name: String, description: String?, expectedRevision: Long)
    fun deletePlaylist(playlistId: String)
    fun selectPlaylist(playlistId: String)
    fun addPlaylistTrack(playlistId: String, mediaId: String)
    fun removePlaylistTrack(playlistId: String, mediaId: String)
    fun reorderPlaylist(playlistId: String, membershipIds: List<String>, expectedRevision: Long)
}
