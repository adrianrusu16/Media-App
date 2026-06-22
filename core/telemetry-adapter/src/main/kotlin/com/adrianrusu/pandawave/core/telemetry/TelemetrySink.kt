package com.adrianrusu.pandawave.core.telemetry

fun interface TelemetrySink {
    fun record(event: TelemetryEvent)
}
