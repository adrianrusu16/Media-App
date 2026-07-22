package com.adrianrusu.pandawave.feature.auth.domain

enum class AuthField {
    EMAIL,
    PASSWORD
}

enum class AuthFieldError {
    EMAIL_REQUIRED,
    EMAIL_INVALID,
    PASSWORD_REQUIRED,
    PASSWORD_TOO_SHORT,
    PASSWORD_TOO_LONG
}

data class AuthFieldFeedback(
    val emailError: AuthFieldError? = null,
    val passwordError: AuthFieldError? = null
)

data class AuthInputSubmission(
    val feedback: AuthFieldFeedback,
    val canSubmit: Boolean
)

object AuthInputPolicy {
    const val PASSWORD_MIN_CODE_POINTS = 8
    const val PASSWORD_MAX_CODE_POINTS = 64
    const val EMAIL_MAX_UTF8_BYTES = 254

    private const val EMAIL_LOCAL_MAX_UTF8_BYTES = 64
    private const val EMAIL_DOMAIN_MAX_BYTES = 253
    private const val EMAIL_DOMAIN_LABEL_MAX_BYTES = 63
    private val localPart = Regex("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+")
    private val domainLabel = Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?")

    fun emailError(email: String): AuthFieldError? {
        val normalized = email.trim()
        if (normalized.isEmpty()) return AuthFieldError.EMAIL_REQUIRED
        if (normalized.encodeToByteArray().size > EMAIL_MAX_UTF8_BYTES) {
            return AuthFieldError.EMAIL_INVALID
        }

        val separator = normalized.indexOf('@')
        if (separator <= 0 || separator != normalized.lastIndexOf('@') || separator == normalized.lastIndex) {
            return AuthFieldError.EMAIL_INVALID
        }

        val local = normalized.substring(0, separator)
        val domain = normalized.substring(separator + 1)
        if (local.encodeToByteArray().size > EMAIL_LOCAL_MAX_UTF8_BYTES ||
            domain.encodeToByteArray().size > EMAIL_DOMAIN_MAX_BYTES ||
            !localPart.matches(local) ||
            local.startsWith('.') ||
            local.endsWith('.') ||
            ".." in local
        ) {
            return AuthFieldError.EMAIL_INVALID
        }

        val labels = domain.split('.')
        if (labels.any { label ->
                label.isEmpty() ||
                    label.length > EMAIL_DOMAIN_LABEL_MAX_BYTES ||
                    !domainLabel.matches(label)
            }
        ) {
            return AuthFieldError.EMAIL_INVALID
        }

        return null
    }

    fun passwordError(mode: AuthFormMode, password: String): AuthFieldError? {
        if (password.isEmpty()) return AuthFieldError.PASSWORD_REQUIRED
        if (mode == AuthFormMode.LOGIN) return null

        val codePoints = password.codePointCount(0, password.length)
        return when {
            codePoints < PASSWORD_MIN_CODE_POINTS -> AuthFieldError.PASSWORD_TOO_SHORT
            codePoints > PASSWORD_MAX_CODE_POINTS -> AuthFieldError.PASSWORD_TOO_LONG
            else -> null
        }
    }
}

object AuthFieldFeedbackPolicy {
    fun onFocus(feedback: AuthFieldFeedback, field: AuthField): AuthFieldFeedback =
        clear(feedback, field)

    fun onEdit(feedback: AuthFieldFeedback, field: AuthField): AuthFieldFeedback =
        clear(feedback, field)

    fun onBlur(
        feedback: AuthFieldFeedback,
        field: AuthField,
        mode: AuthFormMode,
        email: String,
        password: String
    ): AuthFieldFeedback = when (field) {
        AuthField.EMAIL -> feedback.copy(emailError = AuthInputPolicy.emailError(email))
        AuthField.PASSWORD -> feedback.copy(
            passwordError = AuthInputPolicy.passwordError(mode, password)
        )
    }

    fun onSubmit(mode: AuthFormMode, email: String, password: String): AuthInputSubmission {
        val feedback = AuthFieldFeedback(
            emailError = AuthInputPolicy.emailError(email),
            passwordError = AuthInputPolicy.passwordError(mode, password)
        )
        return AuthInputSubmission(
            feedback = feedback,
            canSubmit = feedback.emailError == null && feedback.passwordError == null
        )
    }

    private fun clear(feedback: AuthFieldFeedback, field: AuthField): AuthFieldFeedback =
        when (field) {
            AuthField.EMAIL -> feedback.copy(emailError = null)
            AuthField.PASSWORD -> feedback.copy(passwordError = null)
        }
}
