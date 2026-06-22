package com.adrianrusu.pandawave.core.telemetry.sinks

import com.adrianrusu.pandawave.core.telemetry.TelemetryBreadcrumbStore
import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink

class InMemoryBreadcrumbTelemetrySink(private val capacity: Int = DEFAULT_CAPACITY) :
    TelemetrySink,
    TelemetryBreadcrumbStore {
    private val lock = Any()
    private val events = ArrayDeque<TelemetryEvent>(capacity)

    init {
        require(capacity > 0) {
            "Telemetry breadcrumb capacity must be positive."
        }
    }

    override fun record(event: TelemetryEvent) {
        synchronized(lock) {
            if (events.size == capacity) {
                events.removeFirst()
            }
            events.addLast(event)
        }
    }

    override fun snapshot(): List<TelemetryEvent> = synchronized(lock) {
        events.toList()
    }

    private companion object {
        const val DEFAULT_CAPACITY = 200
    }
}
