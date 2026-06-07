package com.adrianrusu.mediaapp.core.telemetry.sinks

import com.adrianrusu.mediaapp.core.telemetry.TelemetryEvent
import com.adrianrusu.mediaapp.core.telemetry.TelemetrySeverity
import com.adrianrusu.mediaapp.core.telemetry.TelemetrySink
import org.junit.Assert.assertEquals
import org.junit.Test

class CompositeTelemetrySinkTest {
    @Test
    fun eventIsForwardedToEverySink() {
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
