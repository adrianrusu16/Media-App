package com.adrianrusu.pandawave.core.media.adapter.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class BambooMediaSessionWarmupTest {
    @Test
    fun `start connects once until closed`() {
        val connector = RecordingMediaSessionControllerConnector()
        val warmup = BambooMediaSessionWarmup(connector)

        warmup.start()
        warmup.start()

        assertEquals(1, connector.connectCount)

        warmup.close()

        assertEquals(1, connector.connections.single().closeCount)

        warmup.start()

        assertEquals(2, connector.connectCount)
    }

    @Test
    fun `close before start is safe`() {
        val connector = RecordingMediaSessionControllerConnector()
        val warmup = BambooMediaSessionWarmup(connector)

        warmup.close()

        assertEquals(0, connector.connectCount)
    }
}

private class RecordingMediaSessionControllerConnector : MediaSessionControllerConnector {
    val connections = mutableListOf<RecordingConnection>()
    var connectCount = 0

    override fun connect(): AutoCloseable {
        connectCount += 1
        return RecordingConnection().also(connections::add)
    }
}

private class RecordingConnection : AutoCloseable {
    var closeCount = 0

    override fun close() {
        closeCount += 1
    }
}
