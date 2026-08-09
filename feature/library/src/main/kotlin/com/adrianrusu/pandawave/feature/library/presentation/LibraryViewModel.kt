package com.adrianrusu.pandawave.feature.library.presentation

import androidx.lifecycle.ViewModel
import com.adrianrusu.pandawave.feature.library.domain.LibraryRepository
import com.adrianrusu.pandawave.feature.library.domain.LibraryTab
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
) : ViewModel() {
    val state = repository.state

    init {
        repository.start()
    }

    fun selectTab(tab: LibraryTab) = repository.selectTab(tab)
    fun refresh() = repository.refresh()
    fun loadNext() = repository.loadNext(state.value.selectedTab)
    fun save(mediaId: String) = repository.save(mediaId)
    fun removeSaved(mediaId: String) = repository.removeSaved(mediaId)
    fun like(mediaId: String) = repository.like(mediaId)
    fun unlike(mediaId: String) = repository.unlike(mediaId)

    override fun onCleared() {
        repository.close()
    }
}
