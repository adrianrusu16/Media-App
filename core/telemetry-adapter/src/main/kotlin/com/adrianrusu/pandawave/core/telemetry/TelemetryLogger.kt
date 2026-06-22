package com.adrianrusu.pandawave.core.telemetry

class TelemetryLogger(
    private val sink: TelemetrySink,
    private val redactor: TelemetryAttributeRedactor = TelemetryAttributeRedactor(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun debug(name: String, attributes: Map<String, String> = emptyMap()) {
        record(name, TelemetrySeverity.Debug, attributes)
    }

    fun info(name: String, attributes: Map<String, String> = emptyMap()) {
        record(name, TelemetrySeverity.Info, attributes)
    }

    fun warning(name: String, attributes: Map<String, String> = emptyMap(), throwable: Throwable? = null) {
        record(name, TelemetrySeverity.Warning, attributes, throwable)
    }

    fun error(name: String, attributes: Map<String, String> = emptyMap(), throwable: Throwable? = null) {
        record(name, TelemetrySeverity.Error, attributes, throwable)
    }

    fun record(
        name: String,
        severity: TelemetrySeverity,
        attributes: Map<String, String> = emptyMap(),
        throwable: Throwable? = null
    ) {
        sink.record(
            TelemetryEvent(
                name = name,
                severity = severity,
                attributes = redactor.redact(attributes),
                throwable = throwable,
                timestampEpochMillis = clock()
            )
        )
    }
}
