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
    fun `view model starts repository and forwards both tab actions`() {
        val repository = RecordingLibraryRepository()
        val viewModel = LibraryViewModel(repository)

        viewModel.selectTab(LibraryTab.LIKED)
        viewModel.loadNext()
        viewModel.removeSaved("saved-1")
        viewModel.like("saved-1")
        viewModel.unlike("liked-1")
        viewModel.save("liked-1")

        assertEquals(1, repository.startCount)
        assertEquals(
            listOf(
                "select:LIKED",
                "next:LIKED",
                "remove:saved-1",
                "like:saved-1",
                "unlike:liked-1",
                "save:liked-1",
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
    override fun close() = Unit
}
