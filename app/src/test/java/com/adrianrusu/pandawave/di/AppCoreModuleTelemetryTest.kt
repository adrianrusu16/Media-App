package com.adrianrusu.pandawave.di

import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import com.adrianrusu.pandawave.core.telemetry.TelemetrySeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AppCoreModuleTelemetryTest {
    @Test
    fun `telemetry sink records redacted events as local breadcrumbs`() {
        val breadcrumbs = AppCoreModule.provideBreadcrumbSink()
        val sink = AppCoreModule.provideTelemetrySink(breadcrumbs)
        val event = TelemetryEvent(
            name = "app.started",
            module = TelemetryModule.App,
            severity = TelemetrySeverity.Info,
            timestampEpochMillis = 1L
        )

        sink.record(event)

        assertEquals(listOf(event), breadcrumbs.snapshot())
    }

    @Test
    fun `breadcrumb store binding shares the singleton sink`() {
        val breadcrumbs = AppCoreModule.provideBreadcrumbSink()

        assertSame(
            breadcrumbs,
            AppCoreModule.provideTelemetryBreadcrumbStore(breadcrumbs)
        )
    }

    @Test
    fun `debuggable app uses developer telemetry policy`() {
        assertEquals(
            TelemetrySeverity.Debug,
            AppCoreModule.provideTelemetryPolicy(isDebuggable = true).minimumSeverity
        )
    }

    @Test
    fun `non debuggable app uses production telemetry policy`() {
        assertEquals(
            TelemetrySeverity.Warning,
            AppCoreModule.provideTelemetryPolicy(isDebuggable = false).minimumSeverity
        )
    }
}
