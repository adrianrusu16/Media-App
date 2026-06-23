package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.adrianrusu.pandawave.core.audio.visualizer.MutableAudioSessionRepository

@UnstableApi
class ExoPlayerAudioSessionObserver(
    private val player: ExoPlayer,
    private val repository: MutableAudioSessionRepository
) {
    private var started = false

    private val listener = object : AnalyticsListener {
        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            repository.publish(audioSessionId)
        }
    }

    fun start() {
        if (started) return
        player.addAnalyticsListener(listener)
        started = true
        repository.publish(player.audioSessionId)
    }

    fun stop() {
        if (started) {
            player.removeAnalyticsListener(listener)
            started = false
        }
        repository.publish(null)
    }
}
