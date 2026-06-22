package com.adrianrusu.pandawave.core.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class TelemetryLoggerTest {
    @Test
    fun `logger records redacted event with timestamp`() {
        val sink = RecordingTelemetrySink()
        val throwable = IllegalStateException("boom")
        val logger = TelemetryLogger(
            sink = sink,
            clock = { 42L }
        )

        logger.error(
            name = "engine.dispatch.failed",
            attributes = mapOf(
                "screen" to "home",
                "token" to "real-token"
            ),
            throwable = throwable
        )

        val event = sink.events.single()
        assertEquals("engine.dispatch.failed", event.name)
        assertEquals(TelemetrySeverity.Error, event.severity)
        assertEquals("home", event.attributes["screen"])
        assertEquals(TelemetryAttributeRedactor.REDACTED_VALUE, event.attributes["token"])
        assertEquals(42L, event.timestampEpochMillis)
        assertSame(throwable, event.throwable)
    }

    @Test
    fun `scoped logger records its module on every event`() {
        val sink = RecordingTelemetrySink()
        val logger = TelemetryLogger(sink = sink)
            .forModule(TelemetryModule.Playback)

        logger.info(name = "playback.started")

        assertEquals(TelemetryModule.Playback, sink.events.single().module)
    }

    @Test
    fun `disabled severity does not evaluate lazy attributes`() {
        val sink = RecordingTelemetrySink()
        val logger = TelemetryLogger(
            sink = sink,
            policy = TelemetryPolicy.production()
        ).forModule(TelemetryModule.App)
        var attributesEvaluated = false

        logger.debug(name = "app.debug") {
            attributesEvaluated = true
            mapOf("value" to "unused")
        }

        assertFalse(attributesEvaluated)
        assertEquals(emptyList(), sink.events)
    }

    @Test
    fun `production policy suppresses throwable details but keeps exception type`() {
        val sink = RecordingTelemetrySink()
        val logger = TelemetryLogger(
            sink = sink,
            policy = TelemetryPolicy.production()
        ).forModule(TelemetryModule.Preferences)

        logger.error(
            name = "preferences.read_failed",
            throwable = IllegalStateException("secret payload")
        )

        val event = sink.events.single()
        assertNull(event.throwable)
        assertEquals("IllegalStateException", event.attributes[TelemetryAttributeNames.EXCEPTION_TYPE])
    }
}

private class RecordingTelemetrySink : TelemetrySink {
    val events = mutableListOf<TelemetryEvent>()

    override fun record(event: TelemetryEvent) {
        events += event
    }
}
