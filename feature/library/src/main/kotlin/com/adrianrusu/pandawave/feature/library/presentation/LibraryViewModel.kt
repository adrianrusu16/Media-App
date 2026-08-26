package com.adrianrusu.pandawave.feature.library.presentation

import androidx.lifecycle.ViewModel
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.feature.library.domain.LibraryRepository
import com.adrianrusu.pandawave.feature.library.domain.LibraryTab
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val playbackRepository: BambooPlaybackRepository
) : ViewModel() {
    val state = repository.state

    init {
        repository.start()
    }

    fun selectTab(tab: LibraryTab) {
        PandaLog.v(PandaLog.Tag.LIBRARY) { "click action=select_tab tab=${tab.name.lowercase()}" }
        repository.selectTab(tab)
    }
    fun refresh() = repository.refresh()
    fun loadNext() = repository.loadNext(state.value.selectedTab)
    fun play(mediaId: String) {
        val current = state.value
        val section = current.selectedTab.name.lowercase()
        val title = current.selectedTracks.find { it.mediaId == mediaId }?.title
            ?: current.historyEntries.find { it.mediaId == mediaId }?.title
            ?: ""
        val queue = current.playlistTracks.map { it.mediaId }
        val selectedIndex = queue.indexOf(mediaId)
        PandaLog.v(PandaLog.Tag.LIBRARY) {
            "click action=play section=$section playlistId=${current.selectedPlaylistId.orEmpty()} " +
                "trackId=$mediaId title=${PandaLog.field(title)}"
        }
        PandaLog.i(PandaLog.Tag.LIBRARY) {
            "play_requested section=$section playlistId=${current.selectedPlaylistId.orEmpty()} " +
                "trackId=$mediaId title=${PandaLog.field(title)}"
        }
        if (current.selectedPlaylistId != null && selectedIndex >= 0) {
            playbackRepository.dispatch(BambooPlaybackIntent.PlayQueue(queue, selectedIndex))
        } else {
            playbackRepository.dispatch(BambooPlaybackIntent.PlayMedia(mediaId))
        }
    }
    fun save(mediaId: String) = repository.save(mediaId)
    fun removeSaved(mediaId: String) = repository.removeSaved(mediaId)
    fun like(mediaId: String) = repository.like(mediaId)
    fun unlike(mediaId: String) = repository.unlike(mediaId)
    fun createPlaylist(name: String, description: String?) = repository.createPlaylist(name, description)
    fun updatePlaylist(playlistId: String, name: String, description: String?, expectedRevision: Long) =
        repository.updatePlaylist(playlistId, name, description, expectedRevision)
    fun deletePlaylist(playlistId: String) = repository.deletePlaylist(playlistId)
    fun selectPlaylist(playlistId: String) = repository.selectPlaylist(playlistId)
    fun addPlaylistTrack(playlistId: String, mediaId: String) = repository.addPlaylistTrack(playlistId, mediaId)
    fun removePlaylistTrack(playlistId: String, mediaId: String) = repository.removePlaylistTrack(playlistId, mediaId)
    fun reorderPlaylist(playlistId: String, membershipIds: List<String>, expectedRevision: Long) =
        repository.reorderPlaylist(playlistId, membershipIds, expectedRevision)

    override fun onCleared() = Unit
}
