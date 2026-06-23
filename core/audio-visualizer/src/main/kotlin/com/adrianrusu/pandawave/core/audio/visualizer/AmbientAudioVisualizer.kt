package com.adrianrusu.pandawave.core.audio.visualizer

import kotlinx.coroutines.flow.StateFlow

sealed interface AmbientVisualizerAvailability {
    data object Ready : AmbientVisualizerAvailability

    data class Unavailable(val reason: Reason) : AmbientVisualizerAvailability

    enum class Reason {
        PermissionDenied,
        Unsupported,
        InvalidSession,
        InitializationFailed,
        RuntimeFailed
    }
}

interface AmbientAudioVisualizer : AutoCloseable {
    val amplitudes: StateFlow<FloatArray>
    val availability: StateFlow<AmbientVisualizerAvailability>

    fun attachToAudioSession(audioSessionId: Int)

    fun start()

    fun stop()

    override fun close()
}
