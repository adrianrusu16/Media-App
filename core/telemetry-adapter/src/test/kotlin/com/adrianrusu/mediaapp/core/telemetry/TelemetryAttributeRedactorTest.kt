package com.adrianrusu.mediaapp.core.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

class TelemetryAttributeRedactorTest {
    private val redactor = TelemetryAttributeRedactor()

    @Test
    fun sensitiveKeysAreRedacted() {
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
    fun jwtLikeValuesAreRedactedInline() {
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
