package com.adrianrusu.pandawave.core.telemetry.sinks

import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetrySeverity
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink
import kotlin.test.Test
import kotlin.test.assertEquals

class CompositeTelemetrySinkTest {
    @Test
    fun `event is forwarded to every sink`() {
        val first = RecordingTelemetrySink()
        val second = RecordingTelemetrySink()
        val event = TelemetryEvent(
            name = "app.started",
            severity = TelemetrySeverity.Info,
            timestampEpochMillis = 1L
        )

        CompositeTelemetrySink(listOf(first, second)).record(event)

        assertEquals(listOf(event), first.events)
        assertEquals(listOf(event), second.events)
    }
}

private class RecordingTelemetrySink : TelemetrySink {
    val events = mutableListOf<TelemetryEvent>()

    override fun record(event: TelemetryEvent) {
        events += event
    }
}
