package com.adrianrusu.pandawave.core.telemetry.sinks

import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink

class CompositeTelemetrySink(private val sinks: List<TelemetrySink>) : TelemetrySink {
    override fun record(event: TelemetryEvent) {
        sinks.forEach { sink ->
            runCatching {
                sink.record(event)
            }
        }
    }
}
