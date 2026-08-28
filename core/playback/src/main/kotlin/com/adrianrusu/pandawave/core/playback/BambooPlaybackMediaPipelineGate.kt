package com.adrianrusu.pandawave.core.playback

/**
 * Keeps the Media3 playback pipeline (service + effect observer) reachable while
 * PandaEngine effects are being emitted.
 */
fun interface BambooPlaybackMediaPipelineGate {
    fun ensureRunning()

    companion object {
        val NoOp = BambooPlaybackMediaPipelineGate { }
    }
}
