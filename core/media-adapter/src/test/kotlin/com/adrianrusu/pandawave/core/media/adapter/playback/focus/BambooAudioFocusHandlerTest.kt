package com.adrianrusu.pandawave.core.media.adapter.playback.focus

import android.media.AudioManager
import kotlin.test.Test
import kotlin.test.assertEquals

class BambooAudioFocusHandlerTest {
    @Test
    fun `android focus callbacks map to typed engine values`() {
        assertEquals(
            BambooAudioFocusChange.Gain,
            BambooAudioFocusChange.fromAndroid(AudioManager.AUDIOFOCUS_GAIN)
        )
        assertEquals(
            BambooAudioFocusChange.Loss,
            BambooAudioFocusChange.fromAndroid(AudioManager.AUDIOFOCUS_LOSS)
        )
        assertEquals(
            BambooAudioFocusChange.LossTransient,
            BambooAudioFocusChange.fromAndroid(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        )
        assertEquals(
            BambooAudioFocusChange.Duck,
            BambooAudioFocusChange.fromAndroid(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        )
        assertEquals(BambooAudioFocusChange.Unknown, BambooAudioFocusChange.fromAndroid(123_456))
    }
}
