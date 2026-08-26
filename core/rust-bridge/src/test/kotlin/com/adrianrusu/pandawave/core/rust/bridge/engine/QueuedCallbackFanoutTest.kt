package com.adrianrusu.pandawave.core.rust.bridge.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class QueuedCallbackFanoutTest {
    @Test
    fun `nested emit is queued instead of re-entering deliver`() {
        val fanout = QueuedCallbackFanout<Int>()
        val delivered = mutableListOf<Int>()

        fanout.emit(1) { value ->
            delivered += value
            if (value == 1) {
                fanout.emit(2) { nested -> delivered += nested }
            }
        }

        assertEquals(listOf(1, 2), delivered)
    }

    @Test
    fun `deliver is not nested when a listener re-emits`() {
        val fanout = QueuedCallbackFanout<String>()
        var depth = 0
        var maxDepth = 0
        val delivered = mutableListOf<String>()

        fanout.emit("outer") { value ->
            depth += 1
            maxDepth = maxOf(maxDepth, depth)
            delivered += value
            if (value == "outer") {
                fanout.emit("inner") { nested ->
                    depth += 1
                    maxDepth = maxOf(maxDepth, depth)
                    delivered += nested
                    depth -= 1
                }
            }
            depth -= 1
        }

        assertEquals(1, maxDepth)
        assertEquals(listOf("outer", "inner"), delivered)
    }
}
