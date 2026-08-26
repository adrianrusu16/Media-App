package com.adrianrusu.pandawave.core.media.adapter.playback.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Handles Android Audio Focus requests and changes for the media app.
 */
class BambooAudioFocusHandler(context: Context, private val onFocusChange: (BambooAudioFocusChange) -> Unit) :
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
        onFocusChange(BambooAudioFocusChange.fromAndroid(focusChange))
    }

    override fun requestAudioFocus(): BambooAudioFocusRequestResult {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(this)
            .build()
        focusRequest = request
        return when (audioManager.requestAudioFocus(request)) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> BambooAudioFocusRequestResult.Granted
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> BambooAudioFocusRequestResult.Delayed
            else -> BambooAudioFocusRequestResult.Failed
        }
    }

    override fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }
}

interface BambooAudioFocusController {
    fun requestAudioFocus(): BambooAudioFocusRequestResult

    fun abandonAudioFocus()
}

enum class BambooAudioFocusRequestResult(val wireValue: String) {
    Granted("granted"),
    Delayed("delayed"),
    Failed("failed")
}

enum class BambooAudioFocusChange(val wireValue: String) {
    Gain("gain"),
    Loss("loss"),
    LossTransient("loss_transient"),
    Duck("duck"),
    Unknown("unknown");

    companion object {
        fun fromAndroid(focusChange: Int): BambooAudioFocusChange = when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> Gain
            AudioManager.AUDIOFOCUS_LOSS -> Loss
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> LossTransient
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Duck
            else -> Unknown
        }
    }
}
