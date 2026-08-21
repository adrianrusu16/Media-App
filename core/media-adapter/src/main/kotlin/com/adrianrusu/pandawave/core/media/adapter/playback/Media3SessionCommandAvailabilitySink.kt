package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.adrianrusu.pandawave.core.playback.BambooPlaybackControls

@UnstableApi
internal class Media3SessionCommandAvailabilitySink(private val sessionProvider: () -> MediaLibrarySession?) :
    BambooMediaSessionCommandAvailabilitySink {
    override fun project(controls: BambooPlaybackControls) {
        val session = sessionProvider() ?: return
        val playerCommands = BambooMediaSessionCommandPolicy.availablePlayerCommands(
            playerCommands = session.player.availableCommands,
            controls = controls
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
