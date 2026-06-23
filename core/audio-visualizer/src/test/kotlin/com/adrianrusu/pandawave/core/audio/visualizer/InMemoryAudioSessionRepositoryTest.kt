package com.adrianrusu.pandawave.core.audio.visualizer

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class InMemoryAudioSessionRepositoryTest {
    @Test
    fun `publishes valid session and clears invalid session`() {
        val repository = InMemoryAudioSessionRepository()

        repository.publish(42)

        assertEquals(42, repository.audioSessionId.value)

        repository.publish(0)

        assertEquals(null, repository.audioSessionId.value)
    }
}
