package com.adrianrusu.pandawave.core.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelemetryAttributeRedactorTest {
    private val redactor = TelemetryAttributeRedactor()

    @Test
    fun `sensitive keys are redacted`() {
        val redacted = redactor.redact(
            mapOf(
                "authorization" to bearerCredential("real-token"),
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

    @Test
    fun `canopy sensitive attributes are always redacted`() {
        val redacted = redactor.redact(
            mapOf(
                "Authorization" to bearerCredential("credential"),
                "accessToken" to "access-credential",
                "refresh-token" to "refresh-credential",
                "password" to "password-value",
                "streamUrl" to "https://stream.example/capability",
                "playback_url" to "https://playback.example/capability",
                "nonce" to "nonce-value",
                "challengeId" to "challenge-value",
                "cipherText" to "ciphertext-value",
                "iv" to "iv-value",
                "grpcEndpoint" to "https://private.example",
                "streamAuthAddr" to "private-stream-authorizer:9000",
                "smtpPassword" to "smtp-credential",
                "databaseUrl" to "postgres://private",
                "privateCaPem" to "certificate-material",
                "tlsServerName" to "private.example",
                "requestBody" to "opaque-wire-request"
            )
        )

        assertTrue(redacted.values.all { value ->
            value == TelemetryAttributeRedactor.REDACTED_VALUE
        })
    }

    @Test
    fun `bearer credentials are redacted inside otherwise safe attributes`() {
        val redacted = redactor.redact(
            mapOf("message" to "request failed with ${bearerCredential("opaque-credential")}")
        )

        assertEquals(
            "request failed with ${TelemetryAttributeRedactor.REDACTED_VALUE}",
            redacted["message"]
        )
    }

    @Test
    fun `composite iv keys are redacted`() {
        val redacted = redactor.redact(
            mapOf(
                "cipher_iv" to "cipher-iv-value",
                "payloadIv" to "payload-iv-value",
                "session-iv" to "session-iv-value"
            )
        )

        assertTrue(redacted.values.all { value ->
            value == TelemetryAttributeRedactor.REDACTED_VALUE
        })
    }

    @Test
    fun `unrelated words containing iv letters remain visible`() {
        val input = mapOf(
            "driverState" to "parked",
            "activity" to "browse",
            "navigation" to "library"
        )

        assertEquals(input, redactor.redact(input))
    }

    private fun bearerCredential(value: String): String = "Bearer $value"
}
