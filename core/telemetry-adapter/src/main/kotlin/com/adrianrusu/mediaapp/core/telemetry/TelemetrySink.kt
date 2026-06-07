package com.adrianrusu.mediaapp.core.telemetry

fun interface TelemetrySink {
    fun record(event: TelemetryEvent)
}
