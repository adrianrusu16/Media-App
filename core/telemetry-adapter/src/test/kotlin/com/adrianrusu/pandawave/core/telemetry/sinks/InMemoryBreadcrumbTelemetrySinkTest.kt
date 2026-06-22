package com.adrianrusu.pandawave.core.telemetry.sinks

import com.adrianrusu.pandawave.core.telemetry.TelemetryAttributeRedactor
import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetryLogger
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import com.adrianrusu.pandawave.core.telemetry.TelemetrySeverity
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InMemoryBreadcrumbTelemetrySinkTest {
    @Test
    fun `breadcrumb store requires positive capacity`() {
        assertFailsWith<IllegalArgumentException> {
            InMemoryBreadcrumbTelemetrySink(capacity = 0)
        }
    }

    @Test
    fun `breadcrumb store evicts the oldest event at capacity`() {
        val sink = InMemoryBreadcrumbTelemetrySink(capacity = 2)

        sink.record(event("first"))
        sink.record(event("second"))
        sink.record(event("third"))

        assertEquals(listOf("second", "third"), sink.snapshot().map(TelemetryEvent::name))
    }

    @Test
    fun `breadcrumb store accepts concurrent events without loss`() {
        val sink = InMemoryBreadcrumbTelemetrySink(capacity = 400)
        val workers = List(4) { worker ->
            thread(start = false) {
                repeat(100) { index ->
                    sink.record(event("worker-$worker-event-$index"))
                }
            }
        }

        workers.forEach(Thread::start)
        workers.forEach(Thread::join)

        val events = sink.snapshot()
        assertEquals(400, events.size)
        assertEquals(400, events.map(TelemetryEvent::name).toSet().size)
    }

    @Test
    fun `breadcrumbs receive redacted events from the logger`() {
        val sink = InMemoryBreadcrumbTelemetrySink(capacity = 2)
        val logger = TelemetryLogger(sink = sink).forModule(TelemetryModule.App)

        logger.info(
            name = "app.session",
            attributes = mapOf("token" to "real-token")
        )

        assertEquals(
            TelemetryAttributeRedactor.REDACTED_VALUE,
            sink.snapshot().single().attributes["token"]
        )
    }

    private fun event(name: String): TelemetryEvent = TelemetryEvent(
        name = name,
        module = TelemetryModule.App,
        severity = TelemetrySeverity.Info,
        timestampEpochMillis = 1L
    )
}
