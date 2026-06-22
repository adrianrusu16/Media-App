package com.adrianrusu.pandawave.feature.nowplaying.domain

import kotlinx.coroutines.flow.StateFlow

interface NowPlayingRepository : AutoCloseable {
    val state: StateFlow<NowPlayingState>

    fun start()

    fun dispatch(intent: NowPlayingIntent)
}
