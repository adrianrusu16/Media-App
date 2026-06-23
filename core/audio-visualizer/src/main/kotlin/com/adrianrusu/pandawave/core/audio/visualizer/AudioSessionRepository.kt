package com.adrianrusu.pandawave.core.audio.visualizer

import kotlinx.coroutines.flow.StateFlow

interface AudioSessionRepository {
    val audioSessionId: StateFlow<Int?>
}

interface MutableAudioSessionRepository : AudioSessionRepository {
    fun publish(audioSessionId: Int?)
}
