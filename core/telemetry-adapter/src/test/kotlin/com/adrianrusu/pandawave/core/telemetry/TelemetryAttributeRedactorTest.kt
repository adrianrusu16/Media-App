package com.adrianrusu.pandawave.core.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals

class TelemetryAttributeRedactorTest {
    private val redactor = TelemetryAttributeRedactor()

    @Test
    fun `sensitive keys are redacted`() {
        val redacted = redactor.redact(
            mapOf(
                "authorization" to "Bearer real-token",
                "session_id" to "session-123",
                "screen" to "home"
            )
        )

        assertEquals(TelemetryAttributeRedactor.REDACTED_VALUE, redacted["authorization"])
        assertEquals(TelemetryAttributeRedactor.REDACTED_VALUE, redacted["session_id"])
        assertEquals("home", redacted["screen"])
    }

    @Test
    fun `jwt like values are redacted inline`() {
        val redacted = redactor.redact(
            mapOf(
                "message" to "failed token abcdefghijklmnop.qrstuvwxyzABCDEF.GHIJKLMNOPQRSTUVWX"
            )
        )

        assertEquals(
            "failed token ${TelemetryAttributeRedactor.REDACTED_VALUE}",
            redacted["message"]
        )
    }
}
