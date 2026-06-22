package com.adrianrusu.pandawave.core.media.adapter.playback

import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository

/**
 * Projects PandaEngine readiness into Media3 controller command availability.
 */
internal class BambooMediaSessionCommandAvailabilityProjector(
    private val playbackRepository: BambooPlaybackRepository,
    private val sink: BambooMediaSessionCommandAvailabilitySink
) : AutoCloseable {
    private var subscription: AutoCloseable? = null
    private var lastControlsEnabled: Boolean? = null

    fun start() {
        if (subscription != null) {
            return
        }

        subscription = playbackRepository.observe { playbackState ->
            val controlsEnabled = playbackState.canDispatchEngineCommands

            if (controlsEnabled == lastControlsEnabled) {
                return@observe
            }

            lastControlsEnabled = controlsEnabled
            sink.project(controlsEnabled)
        }
    }

    override fun close() {
        subscription?.close()
        subscription = null
        lastControlsEnabled = null
    }
}

internal interface BambooMediaSessionCommandAvailabilitySink {
    fun project(controlsEnabled: Boolean)
}
