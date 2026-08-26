package com.adrianrusu.pandawave.feature.search.presentation

import com.adrianrusu.pandawave.core.playback.BambooPlaybackIntent
import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.feature.search.domain.SearchRepository
import com.adrianrusu.pandawave.feature.search.domain.SearchState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `queries shorter than two characters do not search`() = runTest(dispatcher) {
        val repository = RecordingSearchRepository()
        val viewModel = SearchViewModel(repository, RecordingPlaybackRepository())
        runCurrent()

        viewModel.onQueryChange("a")
        advanceTimeBy(SearchViewModel.QUERY_DEBOUNCE_MS)
        runCurrent()

        assertEquals(listOf("a"), repository.queries)
        assertEquals(emptyList(), repository.searches)
        assertEquals(1, repository.clearCount)
    }

    @Test
    fun `debounced query of at least two characters searches once`() = runTest(dispatcher) {
        val repository = RecordingSearchRepository()
        val viewModel = SearchViewModel(repository, RecordingPlaybackRepository())
        runCurrent()
        viewModel.onQueryChange("a")
        viewModel.onQueryChange("ab")
        viewModel.onQueryChange("abc")
        advanceTimeBy(SearchViewModel.QUERY_DEBOUNCE_MS - 1)
        runCurrent()
        assertEquals(emptyList(), repository.searches)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("abc"), repository.searches)
        assertEquals(1L, repository.state.value.generation)
    }

    @Test
    fun `play dispatches engine-backed media playback`() = runTest(dispatcher) {
        val playback = RecordingPlaybackRepository()
        val viewModel = SearchViewModel(RecordingSearchRepository(), playback)

        viewModel.play("track-1", "Track")

        assertEquals(listOf<BambooPlaybackIntent>(BambooPlaybackIntent.PlayMedia("track-1")), playback.intents)
    }
}

private class RecordingSearchRepository : SearchRepository {
    private val mutableState = MutableStateFlow(SearchState())
    override val state: StateFlow<SearchState> = mutableState
    val queries = mutableListOf<String>()
    val searches = mutableListOf<String>()
    var clearCount = 0
    var generation = 0L

    override fun start() = Unit

    override fun setQuery(query: String) {
        queries += query
        mutableState.value = mutableState.value.copy(query = query)
    }

    override fun search(query: String) {
        generation += 1L
        searches += query
        mutableState.value = mutableState.value.copy(query = query, generation = generation)
    }

    override fun clearResults() {
        clearCount += 1
        generation += 1L
        mutableState.value = mutableState.value.copy(results = emptyList(), generation = generation)
    }

    override fun loadNext() = Unit

    override fun close() = Unit
}

private class RecordingPlaybackRepository : BambooPlaybackRepository {
    override val state: StateFlow<BambooPlaybackState> = MutableStateFlow(BambooPlaybackState())
    val intents = mutableListOf<BambooPlaybackIntent>()

    override fun start() = Unit
    override fun dispatch(intent: BambooPlaybackIntent) {
        intents += intent
    }
    override fun observe(listener: (BambooPlaybackState) -> Unit) = AutoCloseable { }
    override fun observeEffects(listener: (List<EngineEffect>) -> Unit) = AutoCloseable { }
    override fun close() = Unit
}
