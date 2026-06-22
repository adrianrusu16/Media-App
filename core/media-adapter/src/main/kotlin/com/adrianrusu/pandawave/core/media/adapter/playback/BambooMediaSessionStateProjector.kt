package com.adrianrusu.pandawave.core.media.adapter.playback

import com.adrianrusu.pandawave.core.playback.BambooPlaybackRepository

/**
 * Projects Bamboo playback state into the Media3 session-facing player.
 */
internal class BambooMediaSessionStateProjector(
    private val playbackRepository: BambooPlaybackRepository,
    private val sink: BambooMediaSessionStateSink,
    private val playbackEngineBridge: Media3PlaybackEngineBridge
) : AutoCloseable {
    private var subscription: AutoCloseable? = null
    private var lastProjection: BambooMediaSessionStateProjection? = null

    fun start() {
        if (subscription != null) {
            return
        }

        subscription = playbackRepository.observe { playbackState ->
            val projection = playbackState.toMediaSessionStateProjection()

            if (projection == lastProjection) {
                return@observe
            }

            lastProjection = projection
            playbackEngineBridge.projectPlatformPlaybackState {
                sink.project(projection)
            }
        }
    }

    override fun close() {
        subscription?.close()
        subscription = null
        lastProjection = null
    }
}

internal interface BambooMediaSessionStateSink {
    fun project(projection: BambooMediaSessionStateProjection)
}
