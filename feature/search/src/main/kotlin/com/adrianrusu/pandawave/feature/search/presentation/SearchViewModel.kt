package com.adrianrusu.pandawave.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.feature.search.domain.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: SearchRepository,
    private val playbackRepository: BambooPlaybackRepository,
) : ViewModel() {
    val state = repository.state
    private val queries = MutableStateFlow("")

    init {
        repository.start()
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            queries
                .drop(1)
                .debounce(QUERY_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { query ->
                    val normalized = query.trim()
                    if (normalized.length < MIN_QUERY_LENGTH) {
                        repository.clearResults()
                    } else {
                        repository.search(normalized)
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        repository.setQuery(query)
        queries.value = query
    }

    fun loadNext() = repository.loadNext()

    fun retry() {
        val query = repository.state.value.query.trim()
        if (query.length >= MIN_QUERY_LENGTH) {
            repository.search(query)
        }
    }

    fun play(mediaId: String, title: String) {
        PandaLog.v(PandaLog.Tag.SEARCH) {
            "click action=play section=results trackId=$mediaId title=${PandaLog.field(title)}"
        }
        PandaLog.i(PandaLog.Tag.SEARCH) {
            "play_requested section=results trackId=$mediaId title=${PandaLog.field(title)}"
        }
        playbackRepository.dispatch(BambooPlaybackIntent.PlayMedia(mediaId))
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }

    companion object {
        const val QUERY_DEBOUNCE_MS = 225L
        const val MIN_QUERY_LENGTH = 2
    }
}
