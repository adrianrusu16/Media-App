package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.adrianrusu.pandawave.core.playback.BambooPlaybackControls

@UnstableApi
internal class Media3SessionCommandAvailabilitySink(
    private val sessionProvider: () -> MediaLibrarySession?,
    private val hasSeekableTimeline: () -> Boolean = { false }
) : BambooMediaSessionCommandAvailabilitySink {
    override fun project(controls: BambooPlaybackControls) {
        val session = sessionProvider() ?: return
        val playerCommands = BambooMediaSessionCommandPolicy.availablePlayerCommands(
            controls = controls,
            hasSeekableTimeline = hasSeekableTimeline()
        )

        session.connectedControllers.forEach { controller ->
            session.setAvailableCommands(
                controller,
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS,
                playerCommands
            )
        }
    }
}
