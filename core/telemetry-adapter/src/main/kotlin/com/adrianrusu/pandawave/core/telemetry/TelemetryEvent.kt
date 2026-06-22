package com.adrianrusu.pandawave.core.telemetry

data class TelemetryEvent(
    val name: String,
    val module: TelemetryModule,
    val severity: TelemetrySeverity,
    val attributes: Map<String, String> = emptyMap(),
    val throwable: Throwable? = null,
    val timestampEpochMillis: Long
) {
    init {
        require(name.isNotBlank()) {
            "Telemetry event name must not be blank."
        }
    }
}
