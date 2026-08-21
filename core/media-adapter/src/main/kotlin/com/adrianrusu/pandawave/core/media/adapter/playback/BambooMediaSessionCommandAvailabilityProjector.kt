package com.adrianrusu.pandawave.core.media.adapter.playback

import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository
import com.adrianrusu.pandawave.core.playback.BambooPlaybackControls

/**
 * Projects PandaEngine readiness into Media3 controller command availability.
 */
internal class BambooMediaSessionCommandAvailabilityProjector(
    private val playbackRepository: BambooPlaybackRepository,
    private val sink: BambooMediaSessionCommandAvailabilitySink
) : AutoCloseable {
    private var subscription: AutoCloseable? = null
    private var lastControls: BambooPlaybackControls? = null

    fun start() {
        if (subscription != null) {
            return
        }

        subscription = playbackRepository.observe { playbackState ->
            val controls = playbackState.controls

            if (controls == lastControls) {
                return@observe
            }

            lastControls = controls
            sink.project(controls)
        }
    }

    override fun close() {
        subscription?.close()
        subscription = null
        lastControls = null
    }
}

internal interface BambooMediaSessionCommandAvailabilitySink {
    fun project(controls: BambooPlaybackControls)
}
