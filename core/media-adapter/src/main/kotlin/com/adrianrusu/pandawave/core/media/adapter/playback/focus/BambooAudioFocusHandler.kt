package com.adrianrusu.pandawave.core.media.adapter.playback.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Handles Android Audio Focus requests and changes for the media app.
 */
class BambooAudioFocusHandler(context: Context, private val onFocusChange: (Int) -> Unit) :
    AudioManager.OnAudioFocusChangeListener,
    BambooAudioFocusController {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    fun start() {
        requestAudioFocus()
    }

    fun stop() {
        abandonAudioFocus()
    }

    override fun onAudioFocusChange(focusChange: Int) {
        onFocusChange(focusChange)
    }

    override fun requestAudioFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(this)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    override fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }
}

interface BambooAudioFocusController {
    fun requestAudioFocus()

    fun abandonAudioFocus()
}
