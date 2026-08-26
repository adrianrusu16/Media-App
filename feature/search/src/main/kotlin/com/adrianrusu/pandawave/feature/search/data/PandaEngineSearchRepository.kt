package com.adrianrusu.pandawave.feature.search.data

import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.feature.search.domain.SearchRepository
import com.adrianrusu.pandawave.feature.search.domain.SearchState
import com.adrianrusu.pandawave.feature.search.domain.SearchTrack
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PandaEngineSearchRepository @Inject constructor(private val engineGateway: EngineGateway) : SearchRepository {
    private val mutableState = MutableStateFlow(SearchState())
    override val state: StateFlow<SearchState> = mutableState.asStateFlow()

    private val started = AtomicBoolean(false)
    private var subscription: AutoCloseable? = null
    private var searchGeneration = 0L
    private var operationId: String? = null
    private var lastQuery: String = ""

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        subscription = engineGateway.observeSnapshots(::projectBusy)
    }

    override fun setQuery(query: String) {
        mutableState.value = mutableState.value.copy(query = query)
    }

    override fun search(query: String) {
        val normalized = query.trim()
        lastQuery = normalized
        val generation = nextGeneration()
        operationId = null
        mutableState.value = mutableState.value.copy(
            query = normalized,
            results = emptyList(),
            isLoading = true,
            errorType = null,
            isRetryableError = false,
            hasNextPage = false,
            generation = generation
        )
        dispatch(
            EngineCommand(
                EngineCommand.TYPE_SEARCH,
                EngineCommandPayloads.searchCatalog(normalized, PAGE_SIZE)
            ),
            generation = generation
        )
    }

    override fun clearResults() {
        nextGeneration()
        operationId = null
        lastQuery = ""
        mutableState.value = mutableState.value.copy(
            results = emptyList(),
            isLoading = false,
            errorType = null,
            isRetryableError = false,
            hasNextPage = false,
            generation = searchGeneration
        )
    }

    override fun loadNext() {
        val current = mutableState.value
        if (!current.hasNextPage || current.isLoading) return
        val id = operationId ?: return
        dispatch(
            EngineCommand(
                EngineCommand.TYPE_LOAD_NEXT_CATALOG_PAGE,
                EngineCommandPayloads.loadNextCatalogPage(id)
            ),
            generation = current.generation
        )
    }

    override fun close() {
        subscription?.close()
        subscription = null
        started.set(false)
    }

    private fun dispatch(command: EngineCommand, generation: Long) {
        PandaLog.i(PandaLog.Tag.SEARCH) {
            "search.request generation=$generation type=${command.type} query=${PandaLog.field(lastQuery)}"
        }
        val outcome = engineGateway.dispatch(command)
        if (generation != searchGeneration) return
        if (outcome.event.type == EngineEvent.TYPE_GATEWAY_UNAVAILABLE) {
            mutableState.value = mutableState.value.copy(
                isLoading = false,
                errorType = EngineSnapshot.ERROR_NETWORK,
                isRetryableError = true
            )
            return
        }
        operationId = outcome.event.message?.takeIf(String::isNotBlank) ?: operationId
        projectResults(outcome.snapshot, generation)
    }

    private fun projectBusy(snapshot: EngineSnapshot) {
        val current = mutableState.value
        if (current.generation == 0L || lastQuery.isBlank()) return
        if (current.generation != searchGeneration) return
        mutableState.value = current.copy(
            isLoading = snapshot.isBusy,
            errorType = snapshot.errorType.takeIf { snapshot.hasError },
            isRetryableError = snapshot.hasError && snapshot.errorType == EngineSnapshot.ERROR_NETWORK
        )
    }

    private fun projectResults(snapshot: EngineSnapshot, generation: Long) {
        if (generation != searchGeneration) return
        val total = snapshot.searchResultsCount.coerceAtLeast(0)
        val results = engineGateway.searchPages(total).map(EngineCatalogItem::toSearchTrack)
        mutableState.value = mutableState.value.copy(
            results = results,
            isLoading = snapshot.isBusy,
            errorType = snapshot.errorType.takeIf { snapshot.hasError },
            isRetryableError = snapshot.hasError && snapshot.errorType == EngineSnapshot.ERROR_NETWORK,
            hasNextPage = !snapshot.hasError && results.size >= PAGE_SIZE && total >= PAGE_SIZE,
            generation = generation
        )
        PandaLog.i(PandaLog.Tag.SEARCH) {
            "search.shown generation=$generation count=${results.size}/$total " +
                "titles=${PandaLog.titles(results.map(SearchTrack::title))} busy=${snapshot.isBusy}"
        }
    }

    private fun nextGeneration(): Long {
        searchGeneration += 1L
        return searchGeneration
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

private fun EngineGateway.searchPages(count: Int): List<EngineCatalogItem> {
    val total = count.coerceAtLeast(0)
    if (total == 0) return emptyList()
    return buildList {
        var offset = 0
        while (offset < total) {
            val page = searchResultsPage(offset, minOf(SEARCH_PAGE_QUERY_SIZE, total - offset))
            if (page.isEmpty()) break
            addAll(page)
            offset += page.size
        }
    }
}

private fun EngineCatalogItem.toSearchTrack() = SearchTrack(
    mediaId = mediaId,
    title = title,
    artist = artist.orEmpty(),
    album = album
)

private const val SEARCH_PAGE_QUERY_SIZE = 50
