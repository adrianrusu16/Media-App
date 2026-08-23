package com.adrianrusu.pandawave.core.media.adapter.playback

import androidx.media3.common.Player
import com.adrianrusu.pandawave.core.playback.BambooPlaybackState
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

class Media3PlayerStateSinkTest {
    @Test
    fun `empty playback projection does not prepare a media source`() {
        val calls = mutableListOf<String>()
        val player = Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
        ) { _, method, _ ->
            calls += method.name
            when (method.name) {
                "getCurrentMediaItem" -> null
                "getCurrentPosition" -> 0L
                "getVolume" -> 1F
                "getPlaybackState" -> Player.STATE_IDLE
                "getPlayWhenReady" -> false
                else -> null
            }
        } as Player
        val sink = Media3PlayerStateSink(player)

        sink.project(BambooPlaybackState().toMediaSessionStateProjection())

        assertEquals(emptyList(), calls.filter { it == "setMediaItem" || it == "prepare" })
    }
}
