package com.adrianrusu.pandawave.feature.search.domain

import kotlinx.coroutines.flow.StateFlow

interface SearchRepository {
    val state: StateFlow<SearchState>

    fun start()
    fun setQuery(query: String)
    fun search(query: String)
    fun clearResults()
    fun loadNext()
    fun close()
}

data class SearchState(
    val query: String = "",
    val results: List<SearchTrack> = emptyList(),
    val isLoading: Boolean = false,
    val errorType: String? = null,
    val isRetryableError: Boolean = false,
    val hasNextPage: Boolean = false,
    val generation: Long = 0L
)

data class SearchTrack(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkId: String? = null,
    val artworkVersion: String? = null,
    val artworkUri: String? = null
)
