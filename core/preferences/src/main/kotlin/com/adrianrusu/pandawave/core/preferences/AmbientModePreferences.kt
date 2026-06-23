package com.adrianrusu.pandawave.core.preferences

data class AmbientModePreferences(val enabled: Boolean = true, val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS) {
    companion object {
        const val MIN_TIMEOUT_SECONDS = 5
        const val MAX_TIMEOUT_SECONDS = 60
        const val TIMEOUT_STEP_SECONDS = 5
        const val DEFAULT_TIMEOUT_SECONDS = 15

        fun normalizeTimeoutSeconds(value: Int): Int {
            val clamped = value.coerceIn(MIN_TIMEOUT_SECONDS, MAX_TIMEOUT_SECONDS)
            return ((clamped + TIMEOUT_STEP_SECONDS / 2) / TIMEOUT_STEP_SECONDS) * TIMEOUT_STEP_SECONDS
        }
    }
}
