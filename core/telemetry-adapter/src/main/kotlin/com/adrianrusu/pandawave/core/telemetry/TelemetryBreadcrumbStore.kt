package com.adrianrusu.pandawave.core.telemetry

interface TelemetryBreadcrumbStore {
    fun snapshot(): List<TelemetryEvent>
}
