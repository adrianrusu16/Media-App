package com.adrianrusu.pandawave.feature.home.domain

import kotlinx.coroutines.flow.StateFlow

interface HomeRepository {
    val state: StateFlow<HomeState>

    fun start()
    fun refresh()
    fun close()
}

data class HomeState(
    val forYou: List<HomeTrack> = emptyList(),
    val recommendations: List<HomeTrack> = emptyList(),
    val discovery: List<HomeTrack> = emptyList(),
    val isLoading: Boolean = false
)

data class HomeTrack(val id: String, val title: String, val artist: String, val album: String?)
