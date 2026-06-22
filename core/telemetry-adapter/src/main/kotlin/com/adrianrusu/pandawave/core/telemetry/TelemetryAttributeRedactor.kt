package com.adrianrusu.pandawave.core.telemetry

class TelemetryAttributeRedactor(private val sensitiveKeyFragments: Set<String> = DEFAULT_SENSITIVE_KEY_FRAGMENTS) {
    fun redact(attributes: Map<String, String>): Map<String, String> = attributes.mapValues { (key, value) ->
        if (key.isSensitiveKey()) REDACTED_VALUE else value.redactInlineSecrets()
    }

    private fun String.isSensitiveKey(): Boolean {
        val normalizedKey = lowercase()

        return sensitiveKeyFragments.any { fragment ->
            normalizedKey.contains(fragment)
        }
    }

    private fun String.redactInlineSecrets(): String {
        val words = split(WHITESPACE_REGEX)

        return words.joinToString(separator = " ") { word ->
            if (word.looksSensitive()) REDACTED_VALUE else word
        }
    }

    private fun String.looksSensitive(): Boolean {
        val normalized = lowercase()

        return normalized.startsWith("bearer ") ||
            normalized.startsWith("token=") ||
            normalized.startsWith("apikey=") ||
            normalized.startsWith("password=") ||
            JWT_LIKE_REGEX.matches(this)
    }

    companion object {
        const val REDACTED_VALUE = "[REDACTED]"

        private val DEFAULT_SENSITIVE_KEY_FRAGMENTS = setOf(
            "authorization",
            "token",
            "secret",
            "password",
            "session",
            "jwt",
            "apikey",
            "api_key",
            "user_id",
            "email"
        )
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val JWT_LIKE_REGEX = Regex(
            pattern = "[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}"
        )
    }
}
