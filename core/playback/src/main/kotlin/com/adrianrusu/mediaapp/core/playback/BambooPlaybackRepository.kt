package com.adrianrusu.mediaapp.core.playback

import kotlinx.coroutines.flow.StateFlow

interface BambooPlaybackRepository : AutoCloseable {
    val state: StateFlow<BambooPlaybackState>

    fun start()

    fun dispatch(intent: BambooPlaybackIntent)

    fun observe(listener: (BambooPlaybackState) -> Unit): AutoCloseable
}
