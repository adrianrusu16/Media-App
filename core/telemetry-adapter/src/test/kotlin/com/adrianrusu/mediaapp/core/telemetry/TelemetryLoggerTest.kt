package com.adrianrusu.mediaapp.core.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TelemetryLoggerTest {
    @Test
    fun `logger records redacted event with timestamp`() {
        val sink = RecordingTelemetrySink()
        val throwable = IllegalStateException("boom")
        val logger = TelemetryLogger(
            sink = sink,
            clock = { 42L }
        )

        logger.error(
            name = "engine.dispatch.failed",
            attributes = mapOf(
                "screen" to "home",
                "token" to "real-token"
            ),
            throwable = throwable
        )

        val event = sink.events.single()
        assertEquals("engine.dispatch.failed", event.name)
        assertEquals(TelemetrySeverity.Error, event.severity)
        assertEquals("home", event.attributes["screen"])
        assertEquals(TelemetryAttributeRedactor.REDACTED_VALUE, event.attributes["token"])
        assertEquals(42L, event.timestampEpochMillis)
        assertSame(throwable, event.throwable)
    }
}

private class RecordingTelemetrySink : TelemetrySink {
    val events = mutableListOf<TelemetryEvent>()

    override fun record(event: TelemetryEvent) {
        events += event
    }
}
