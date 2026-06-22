package com.adrianrusu.pandawave.core.playback

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import kotlinx.coroutines.flow.StateFlow

interface BambooPlaybackRepository : AutoCloseable {
    val state: StateFlow<BambooPlaybackState>

    fun start()

    fun dispatch(intent: BambooPlaybackIntent)

    fun observe(listener: (BambooPlaybackState) -> Unit): AutoCloseable

    fun observeEffects(listener: (List<EngineEffect>) -> Unit): AutoCloseable
}
