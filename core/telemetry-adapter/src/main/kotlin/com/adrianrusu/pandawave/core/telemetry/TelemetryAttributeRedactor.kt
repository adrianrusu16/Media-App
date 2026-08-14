package com.adrianrusu.pandawave.core.telemetry

class TelemetryAttributeRedactor(private val sensitiveKeyFragments: Set<String> = DEFAULT_SENSITIVE_KEY_FRAGMENTS) {
    fun redact(attributes: Map<String, String>): Map<String, String> = attributes.mapValues { (key, value) ->
        if (key.isSensitiveKey()) REDACTED_VALUE else value.redactInlineSecrets()
    }

    private fun String.isSensitiveKey(): Boolean {
        val normalizedKey = filter(Char::isLetterOrDigit).lowercase()

        return normalizedKey in EXACT_SENSITIVE_KEYS ||
            sensitiveKeyFragments.any(normalizedKey::contains)
    }

    private fun String.redactInlineSecrets(): String {
        val bearerRedacted = replace(BEARER_CREDENTIAL_REGEX, REDACTED_VALUE)
        val words = bearerRedacted.split(WHITESPACE_REGEX)

        return words.joinToString(separator = " ") { word ->
            if (word.looksSensitive()) REDACTED_VALUE else word
        }
    }

    private fun String.looksSensitive(): Boolean {
        val normalized = lowercase()

        return normalized.startsWith("token=") ||
            normalized.startsWith("apikey=") ||
            normalized.startsWith("password=") ||
            JWT_LIKE_REGEX.matches(this)
    }

    companion object {
        const val REDACTED_VALUE = "[REDACTED]"

        private val DEFAULT_SENSITIVE_KEY_FRAGMENTS = setOf(
            "authorization",
            "accesstoken",
            "refreshtoken",
            "token",
            "secret",
            "password",
            "session",
            "jwt",
            "apikey",
            "userid",
            "email",
            "streamurl",
            "playbackurl",
            "nonce",
            "challenge",
            "ciphertext",
            "grpcendpoint",
            "streamauthaddr",
            "smtp",
            "databaseurl",
            "privatecapem",
            "tlsservername",
            "requestbody",
            "responsebody"
        )
        private val EXACT_SENSITIVE_KEYS = setOf("iv")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val BEARER_CREDENTIAL_REGEX = Regex(
            pattern = "(?i)\\bbearer\\s+[^\\s,;]+"
        )
        private val JWT_LIKE_REGEX = Regex(
            pattern = "[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}"
        )
    }
}
