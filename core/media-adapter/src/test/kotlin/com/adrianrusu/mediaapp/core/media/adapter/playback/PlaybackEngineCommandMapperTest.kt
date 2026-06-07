package com.adrianrusu.mediaapp.core.media.adapter.playback

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackEngineCommandMapperTest {
    @Test
    fun playWhenReadyMapsToPlayCommand() {
        val command = PlaybackEngineCommandMapper.fromPlayWhenReady(true)

        assertEquals(EngineCommand.TYPE_PLAY, command.type)
        assertNull(command.payload)
    }

    @Test
    fun notPlayWhenReadyMapsToPauseCommand() {
        val command = PlaybackEngineCommandMapper.fromPlayWhenReady(false)

        assertEquals(EngineCommand.TYPE_PAUSE, command.type)
        assertNull(command.payload)
    }
}
