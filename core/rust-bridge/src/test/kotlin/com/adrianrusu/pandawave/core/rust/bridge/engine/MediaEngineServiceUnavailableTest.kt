package com.adrianrusu.pandawave.core.rust.bridge.engine

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MediaEngineServiceUnavailableTest {
    @Test
    fun `backend startup failure rejects dispatch as gateway unavailable`() {
        val snapshot = EngineSnapshot.idle(nowMillis = 1L).copy(canDispatch = false)

        val result = backendUnavailableResult(snapshot)

        assertEquals(EngineEvent.TYPE_GATEWAY_UNAVAILABLE, result.event.type)
        assertEquals("backend_unavailable", result.event.message)
        assertFalse(result.snapshot.canDispatch)
        assertEquals(emptyList<Any>(), result.effects)
    }
}
