package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.adrianrusu.pandawave.core.audio.visualizer.MutableAudioSessionRepository
import com.adrianrusu.pandawave.core.common.log.PandaLog

@UnstableApi
class ExoPlayerAudioSessionObserver(
    private val player: ExoPlayer,
    private val repository: MutableAudioSessionRepository
) {
    private var started = false

    private val listener = object : AnalyticsListener {
        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            PandaLog.i(PandaLog.Tag.PLAYER) { "audio_session sessionId=$audioSessionId" }
            repository.publish(audioSessionId)
        }

        override fun onAudioUnderrun(
            eventTime: AnalyticsListener.EventTime,
            bufferSize: Int,
            bufferSizeMs: Long,
            elapsedSinceLastFeedMs: Long
        ) {
            PandaLog.w(PandaLog.Tag.PLAYER) {
                "underrun bufferSize=$bufferSize bufferSizeMs=$bufferSizeMs elapsedSinceLastFeedMs=$elapsedSinceLastFeedMs"
            }
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
