package com.adrianrusu.pandawave.feature.library.presentation

import com.adrianrusu.pandawave.feature.library.domain.LibraryRepository
import com.adrianrusu.pandawave.feature.library.domain.LibraryState
import com.adrianrusu.pandawave.feature.library.domain.LibraryTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LibraryViewModelTest {
    @Test
    fun `view model starts repository and forwards library actions`() {
        val repository = RecordingLibraryRepository()
        val viewModel = LibraryViewModel(repository)

        viewModel.selectTab(LibraryTab.LIKED)
        viewModel.loadNext()
        viewModel.removeSaved("saved-1")
        viewModel.like("saved-1")
        viewModel.unlike("liked-1")
        viewModel.save("liked-1")
        viewModel.createPlaylist("Road trip", "For the drive")
        viewModel.updatePlaylist("playlist-1", "Road trip 2", null, 7)
        viewModel.deletePlaylist("playlist-1")
        viewModel.selectPlaylist("playlist-1")
        viewModel.addPlaylistTrack("playlist-1", "media-1")
        viewModel.removePlaylistTrack("playlist-1", "media-1")
        viewModel.reorderPlaylist("playlist-1", listOf("member-3", "member-1", "member-2"), 9)

        assertEquals(1, repository.startCount)
        assertEquals(
            listOf(
                "select:LIKED",
                "next:LIKED",
                "remove:saved-1",
                "like:saved-1",
                "unlike:liked-1",
                "save:liked-1",
                "create:Road trip:For the drive",
                "update:playlist-1:Road trip 2:<null>:7",
                "delete:playlist-1",
                "playlist:playlist-1",
                "add:playlist-1:media-1",
                "remove:playlist-1:media-1",
                "reorder:playlist-1:member-3,member-1,member-2:9",
            ),
            repository.actions,
        )
    }
}

private class RecordingLibraryRepository : LibraryRepository {
    private val mutableState = MutableStateFlow(LibraryState())
    override val state: StateFlow<LibraryState> = mutableState
    var startCount = 0
    val actions = mutableListOf<String>()

    override fun start() { startCount += 1 }
    override fun selectTab(tab: LibraryTab) {
        mutableState.value = mutableState.value.copy(selectedTab = tab)
        actions += "select:$tab"
    }
    override fun refresh() { actions += "refresh" }
    override fun loadNext(tab: LibraryTab) { actions += "next:$tab" }
    override fun save(mediaId: String) { actions += "save:$mediaId" }
    override fun removeSaved(mediaId: String) { actions += "remove:$mediaId" }
    override fun like(mediaId: String) { actions += "like:$mediaId" }
    override fun unlike(mediaId: String) { actions += "unlike:$mediaId" }
    override fun createPlaylist(name: String, description: String?) { actions += "create:$name:${description ?: "<null>"}" }
    override fun updatePlaylist(playlistId: String, name: String, description: String?, expectedRevision: Long) {
        actions += "update:$playlistId:$name:${description ?: "<null>"}:$expectedRevision"
    }
    override fun deletePlaylist(playlistId: String) { actions += "delete:$playlistId" }
    override fun selectPlaylist(playlistId: String) { actions += "playlist:$playlistId" }
    override fun addPlaylistTrack(playlistId: String, mediaId: String) { actions += "add:$playlistId:$mediaId" }
    override fun removePlaylistTrack(playlistId: String, mediaId: String) { actions += "remove:$playlistId:$mediaId" }
    override fun reorderPlaylist(playlistId: String, membershipIds: List<String>, expectedRevision: Long) {
        actions += "reorder:$playlistId:${membershipIds.joinToString(",")}:$expectedRevision"
    }
    override fun close() = Unit
}
