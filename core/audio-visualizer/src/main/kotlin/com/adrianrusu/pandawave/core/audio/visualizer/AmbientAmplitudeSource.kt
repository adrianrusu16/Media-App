package com.adrianrusu.pandawave.core.audio.visualizer

import kotlinx.coroutines.flow.StateFlow

interface AmbientAmplitudeSource : AutoCloseable {
    val amplitudes: StateFlow<FloatArray>

    fun start()

    fun stop()
}
