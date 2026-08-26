package com.adrianrusu.pandawave.core.media.adapter.playback

/**
 * Media3 session extras and custom commands shared with the app process.
 *
 * Automotive media hosts should be accepted via Media3 controller trust APIs,
 * not a hardcoded package name.
 */
object PandaWaveMediaSessionContract {
    const val COMMAND_OPEN_NOW_PLAYING = "com.adrianrusu.pandawave.session.OPEN_NOW_PLAYING"
    const val EXTRA_OPEN_NOW_PLAYING = "com.adrianrusu.pandawave.extra.OPEN_NOW_PLAYING"
}
