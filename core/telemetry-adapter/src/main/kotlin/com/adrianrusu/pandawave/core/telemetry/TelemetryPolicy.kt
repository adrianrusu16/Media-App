package com.adrianrusu.pandawave.core.telemetry

data class TelemetryPolicy(val minimumSeverity: TelemetrySeverity, val includeThrowable: Boolean) {
    fun allows(severity: TelemetrySeverity): Boolean = severity.ordinal >= minimumSeverity.ordinal

    companion object {
        fun developer(): TelemetryPolicy = TelemetryPolicy(
            minimumSeverity = TelemetrySeverity.Debug,
            includeThrowable = true
        )

        fun production(): TelemetryPolicy = TelemetryPolicy(
            minimumSeverity = TelemetrySeverity.Warning,
            includeThrowable = false
        )
    }
}
