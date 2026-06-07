package com.adrianrusu.mediaapp.core.telemetry.sinks

import com.adrianrusu.mediaapp.core.telemetry.TelemetryEvent
import com.adrianrusu.mediaapp.core.telemetry.TelemetrySink

class CompositeTelemetrySink(private val sinks: List<TelemetrySink>) : TelemetrySink {
    override fun record(event: TelemetryEvent) {
        sinks.forEach { sink ->
            sink.record(event)
        }
    }
}
