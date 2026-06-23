package com.adrianrusu.pandawave.core.audio.visualizer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryAudioSessionRepository : MutableAudioSessionRepository {
    private val mutableAudioSessionId = MutableStateFlow<Int?>(null)

    override val audioSessionId: StateFlow<Int?> = mutableAudioSessionId.asStateFlow()

    override fun publish(audioSessionId: Int?) {
        mutableAudioSessionId.value = audioSessionId?.takeIf { it > 0 }
    }
}
