package com.adrianrusu.pandawave.core.media.adapter.playback

import android.net.PandawaveTestUri
import androidx.media3.common.MediaItem
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

        assertEquals(
            emptyList(),
            calls.filter { it == "setMediaItem" || it == "prepare" || it == "seekTo" || it == "setPlayWhenReady" }
        )
    }

    @Test
    fun `volume projection does not seek to a stale playback position`() {
        val mediaItem = mediaItem()
        val player = RecordingProjectionPlayer(
            currentMediaItem = mediaItem,
            currentPosition = 0L,
            volume = 1F,
            playbackState = Player.STATE_READY,
            playWhenReady = true
        )
        val sink = Media3PlayerStateSink(player.proxy)
        val initialProjection = BambooMediaSessionStateProjection(
            mediaItem = mediaItem,
            playWhenReady = true,
            positionMillis = 0L,
            volume = 1F
        )
        sink.project(initialProjection)
        player.calls.clear()
        player.currentPosition = 45_000L

        sink.project(initialProjection.copy(volume = 0.25F))

        assertEquals(listOf("setVolume:0.25"), player.calls)
    }

    @Test
    fun `play pause projection does not seek to a stale playback position`() {
        val mediaItem = mediaItem()
        val player = RecordingProjectionPlayer(
            currentMediaItem = mediaItem,
            currentPosition = 0L,
            volume = 1F,
            playbackState = Player.STATE_READY,
            playWhenReady = true
        )
        val sink = Media3PlayerStateSink(player.proxy)
        val initialProjection = BambooMediaSessionStateProjection(
            mediaItem = mediaItem,
            playWhenReady = true,
            positionMillis = 0L,
            volume = 1F
        )
        sink.project(initialProjection)
        player.calls.clear()
        player.currentPosition = 45_000L

        sink.project(initialProjection.copy(playWhenReady = false))

        assertEquals(listOf("setPlayWhenReady:false"), player.calls)
    }

    @Test
    fun `position projection seeks when the projected playback position changes`() {
        val mediaItem = mediaItem()
        val player = RecordingProjectionPlayer(
            currentMediaItem = mediaItem,
            currentPosition = 0L,
            volume = 1F,
            playbackState = Player.STATE_READY,
            playWhenReady = true
        )
        val sink = Media3PlayerStateSink(player.proxy)
        val initialProjection = BambooMediaSessionStateProjection(
            mediaItem = mediaItem,
            playWhenReady = true,
            positionMillis = 0L,
            volume = 1F
        )
        sink.project(initialProjection)
        player.calls.clear()

        sink.project(initialProjection.copy(positionMillis = 12_345L))

        assertEquals(listOf("seekTo:12345"), player.calls)
    }
}

private fun mediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId("track-1")
    .setUri(PandawaveTestUri)
    .build()

private class RecordingProjectionPlayer(
    var currentMediaItem: MediaItem?,
    var currentPosition: Long,
    var volume: Float,
    var playbackState: Int,
    var playWhenReady: Boolean
) {
    val calls = mutableListOf<String>()
    val proxy: Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { _, method, args ->
        when (method.name) {
            "getCurrentMediaItem" -> currentMediaItem
            "getCurrentPosition" -> currentPosition
            "getVolume" -> volume
            "setVolume" -> {
                val arguments = checkNotNull(args)
                val nextVolume = arguments[0] as Float
                volume = nextVolume
                calls += "setVolume:$nextVolume"
                null
            }

            "getPlaybackState" -> playbackState
            "prepare" -> {
                calls += "prepare"
                playbackState = Player.STATE_BUFFERING
                null
            }

            "getPlayWhenReady" -> playWhenReady
            "setPlayWhenReady" -> {
                val arguments = checkNotNull(args)
                val nextPlayWhenReady = arguments[0] as Boolean
                playWhenReady = nextPlayWhenReady
                calls += "setPlayWhenReady:$nextPlayWhenReady"
                null
            }

            "setMediaItem" -> {
                val arguments = checkNotNull(args)
                currentMediaItem = arguments[0] as MediaItem
                currentPosition = arguments.getOrNull(1) as? Long ?: currentPosition
                calls += "setMediaItem:${currentMediaItem?.mediaId}:$currentPosition"
                null
            }

            "seekTo" -> {
                val arguments = checkNotNull(args)
                val nextPosition = if (arguments.size == 1) arguments[0] as Long else arguments[1] as Long
                currentPosition = nextPosition
                calls += "seekTo:$nextPosition"
                null
            }

            else -> defaultReturnValue(method.returnType)
        }
    } as Player
}

private fun defaultReturnValue(returnType: Class<*>): Any? = when (returnType) {
    java.lang.Boolean.TYPE -> false
    java.lang.Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Float.TYPE -> 0F
    java.lang.Double.TYPE -> 0.0
    java.lang.Void.TYPE -> null
    else -> null
}
