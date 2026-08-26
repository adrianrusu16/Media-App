package com.adrianrusu.pandawave.core.telemetry.sinks

import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import com.adrianrusu.pandawave.core.telemetry.TelemetrySeverity
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import com.adrianrusu.pandawave.core.testing.RecordingTelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals

class CompositeTelemetrySinkTest {
    @Test
    fun `event is forwarded to every sink`() {
        val first = RecordingTelemetrySink()
        val second = RecordingTelemetrySink()
        val event = TelemetryEvent(
            name = "app.started",
            module = TelemetryModule.App,
            severity = TelemetrySeverity.Info,
            timestampEpochMillis = 1L
        )

        CompositeTelemetrySink(listOf(first, second)).record(event)

        assertEquals(listOf(event), first.events)
        assertEquals(listOf(event), second.events)
    }

    @Test
    fun `failing sink does not block remaining sinks`() {
        val recording = RecordingTelemetrySink()
        val event = TelemetryEvent(
            name = "app.started",
            module = TelemetryModule.App,
            severity = TelemetrySeverity.Info,
            timestampEpochMillis = 1L
        )
        val failing = TelemetrySink { error("sink unavailable") }

        CompositeTelemetrySink(listOf(failing, recording)).record(event)

        assertEquals(listOf(event), recording.events)
    }
}
