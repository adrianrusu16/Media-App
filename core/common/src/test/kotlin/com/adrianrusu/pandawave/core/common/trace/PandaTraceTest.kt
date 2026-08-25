package com.adrianrusu.pandawave.core.common.trace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PandaTraceTest {
    @Test
    fun sectionBalancesBeginAndEndAroundSuccessfulWork() {
        val sink = RecordingTraceSink()

        PandaTrace.withSinkForTest(sink).use {
            val result = PandaTrace.section("PW.Test.success") { "ok" }

            assertEquals("ok", result)
        }

        assertEquals(
            listOf(
                "begin:PW.Test.success",
                "end"
            ),
            sink.events
        )
    }

    @Test
    fun sectionEndsWhenWorkThrows() {
        val sink = RecordingTraceSink()

        PandaTrace.withSinkForTest(sink).use {
            assertFailsWith<IllegalStateException> {
                PandaTrace.section("PW.Test.failure") {
                    error("boom")
                }
            }
        }

        assertEquals(
            listOf(
                "begin:PW.Test.failure",
                "end"
            ),
            sink.events
        )
    }

    @Test
    fun defaultSinkIsBestEffortInLocalUnitTests() {
        val result = PandaTrace.section("PW.Test.default") { "ok" }

        assertEquals("ok", result)
    }
}

private class RecordingTraceSink : TraceSink {
    val events = mutableListOf<String>()

    override fun beginSection(name: String) {
        events += "begin:$name"
    }

    override fun endSection() {
        events += "end"
    }
}
