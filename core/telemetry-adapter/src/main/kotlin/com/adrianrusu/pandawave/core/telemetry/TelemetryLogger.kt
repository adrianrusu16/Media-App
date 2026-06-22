package com.adrianrusu.pandawave.core.telemetry

class TelemetryLogger(
    private val sink: TelemetrySink,
    private val redactor: TelemetryAttributeRedactor = TelemetryAttributeRedactor(),
    private val policy: TelemetryPolicy = TelemetryPolicy.developer(),
    private val module: TelemetryModule = TelemetryModule.App,
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun forModule(module: TelemetryModule): TelemetryLogger = TelemetryLogger(
        sink = sink,
        redactor = redactor,
        policy = policy,
        module = module,
        clock = clock
    )

    fun debug(name: String, attributes: Map<String, String> = emptyMap()) {
        record(name, TelemetrySeverity.Debug, attributes)
    }

    fun debug(name: String, attributes: () -> Map<String, String>) {
        recordLazy(name, TelemetrySeverity.Debug, attributes)
    }

    fun info(name: String, attributes: Map<String, String> = emptyMap()) {
        record(name, TelemetrySeverity.Info, attributes)
    }

    fun info(name: String, attributes: () -> Map<String, String>) {
        recordLazy(name, TelemetrySeverity.Info, attributes)
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
        if (!policy.allows(severity)) return

        val safeAttributes = if (throwable != null && !policy.includeThrowable) {
            attributes + (
                TelemetryAttributeNames.EXCEPTION_TYPE to
                    (throwable::class.simpleName ?: Throwable::class.simpleName.orEmpty())
                )
        } else {
            attributes
        }
        sink.record(
            TelemetryEvent(
                name = name,
                module = module,
                severity = severity,
                attributes = redactor.redact(safeAttributes),
                throwable = throwable.takeIf { policy.includeThrowable },
                timestampEpochMillis = clock()
            )
        )
    }

    private fun recordLazy(name: String, severity: TelemetrySeverity, attributes: () -> Map<String, String>) {
        if (!policy.allows(severity)) return
        record(name = name, severity = severity, attributes = attributes())
    }
}
