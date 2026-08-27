package com.adrianrusu.pandawave.core.rust.bridge.engine

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `queued emit uses the deliver from that emit`() {
        val fanout = QueuedCallbackFanout<Int>()
        val fromOuter = mutableListOf<Int>()
        val fromInner = mutableListOf<Int>()

        fanout.emit(1) { value ->
            fromOuter += value
            if (value == 1) {
                fanout.emit(2) { nested -> fromInner += nested }
            }
        }

        assertEquals(listOf(1), fromOuter)
        assertEquals(listOf(2), fromInner)
    }

    @Test
    fun `concurrent emit is serialized and delivers every value once`() {
        val fanout = QueuedCallbackFanout<Int>()
        val threads = 8
        val perThread = 50
        val inDeliver = AtomicInteger(0)
        val maxDepth = AtomicInteger(0)
        val delivered = Collections.synchronizedList(mutableListOf<Int>())
        val start = CyclicBarrier(threads)
        val done = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)

        try {
            repeat(threads) { threadIndex ->
                executor.execute {
                    start.await()
                    repeat(perThread) { itemIndex ->
                        fanout.emit(threadIndex * perThread + itemIndex) { value ->
                            val depth = inDeliver.incrementAndGet()
                            maxDepth.accumulateAndGet(depth, ::maxOf)
                            delivered += value
                            inDeliver.decrementAndGet()
                        }
                    }
                    done.countDown()
                }
            }

            assertTrue(done.await(5, TimeUnit.SECONDS))
            assertEquals(1, maxDepth.get())
            assertEquals(0, inDeliver.get())
            val expected = (0 until threads * perThread).toSet()
            assertEquals(expected, delivered.toSet())
            assertEquals(expected.size, delivered.size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `failed deliver does not wedge later emits`() {
        val fanout = QueuedCallbackFanout<Int>()
        val delivered = mutableListOf<Int>()

        try {
            fanout.emit(1) { value ->
                if (value == 1) {
                    fanout.emit(3) { nested -> delivered += nested }
                    error("boom")
                }
                delivered += value
            }
        } catch (_: IllegalStateException) {
        }

        fanout.emit(2) { value -> delivered += value }

        assertEquals(listOf(3, 2), delivered)
    }
}
