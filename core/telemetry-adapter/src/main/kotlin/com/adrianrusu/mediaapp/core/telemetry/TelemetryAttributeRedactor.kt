package com.adrianrusu.mediaapp.core.telemetry

class TelemetryAttributeRedactor(
    private val sensitiveKeyFragments: Set<String> = DefaultSensitiveKeyFragments,
) {
    fun redact(attributes: Map<String, String>): Map<String, String> =
        attributes.mapValues { (key, value) ->
            if (key.isSensitiveKey()) RedactedValue else value.redactInlineSecrets()
        }

    private fun String.isSensitiveKey(): Boolean {
        val normalizedKey = lowercase()

        return sensitiveKeyFragments.any { fragment ->
            normalizedKey.contains(fragment)
        }
    }

    private fun String.redactInlineSecrets(): String {
        val words = split(WhitespaceRegex)

        return words.joinToString(separator = " ") { word ->
            if (word.looksSensitive()) RedactedValue else word
        }
    }

    private fun String.looksSensitive(): Boolean {
        val normalized = lowercase()

        return normalized.startsWith("bearer ") ||
            normalized.startsWith("token=") ||
            normalized.startsWith("apikey=") ||
            normalized.startsWith("password=") ||
            JwtLikeRegex.matches(this)
    }

    companion object {
        const val RedactedValue = "[REDACTED]"

        private val DefaultSensitiveKeyFragments = setOf(
            "authorization",
            "token",
            "secret",
            "password",
            "session",
            "jwt",
            "apikey",
            "api_key",
            "user_id",
            "email",
        )
        private val WhitespaceRegex = Regex("\\s+")
        private val JwtLikeRegex = Regex(
            pattern = "[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}",
        )
    }
}
