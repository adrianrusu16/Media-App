package com.adrianrusu.pandawave.feature.auth.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthInputPolicyTest {
    @Test
    fun `registration password accepts eight through sixty four Unicode code points`() {
        assertNull(AuthInputPolicy.passwordError(AuthFormMode.REGISTER, "12345678"))
        assertNull(AuthInputPolicy.passwordError(AuthFormMode.REGISTER, "a".repeat(64)))
        assertNull(AuthInputPolicy.passwordError(AuthFormMode.REGISTER, "\uD83D\uDC3C".repeat(8)))
    }

    @Test
    fun `registration password reports exact length boundaries`() {
        assertEquals(
            AuthFieldError.PASSWORD_TOO_SHORT,
            AuthInputPolicy.passwordError(AuthFormMode.REGISTER, "1234567")
        )
        assertEquals(
            AuthFieldError.PASSWORD_TOO_LONG,
            AuthInputPolicy.passwordError(AuthFormMode.REGISTER, "a".repeat(65))
        )
    }

    @Test
    fun `login requires a password without applying creation length policy`() {
        assertEquals(
            AuthFieldError.PASSWORD_REQUIRED,
            AuthInputPolicy.passwordError(AuthFormMode.LOGIN, "")
        )
        assertNull(AuthInputPolicy.passwordError(AuthFormMode.LOGIN, "old"))
        assertNull(AuthInputPolicy.passwordError(AuthFormMode.LOGIN, "a".repeat(100)))
    }

    @Test
    fun `email policy rejects missing malformed and oversized addresses`() {
        assertEquals(AuthFieldError.EMAIL_REQUIRED, AuthInputPolicy.emailError("   "))
        assertEquals(AuthFieldError.EMAIL_INVALID, AuthInputPolicy.emailError("foo"))
        assertEquals(AuthFieldError.EMAIL_INVALID, AuthInputPolicy.emailError("a@@example.com"))
        assertEquals(AuthFieldError.EMAIL_INVALID, AuthInputPolicy.emailError("a b@example.com"))
        assertEquals(AuthFieldError.EMAIL_INVALID, AuthInputPolicy.emailError(".a@example.com"))
        assertEquals(AuthFieldError.EMAIL_INVALID, AuthInputPolicy.emailError("a@example..com"))
        assertEquals(
            AuthFieldError.EMAIL_INVALID,
            AuthInputPolicy.emailError("${"a".repeat(245)}@example.com")
        )
    }

    @Test
    fun `email policy accepts trimmed common addresses and test domains`() {
        assertNull(AuthInputPolicy.emailError(" driver+car@example.com "))
        assertNull(AuthInputPolicy.emailError("driver@canopy.test"))
    }

    @Test
    fun `field feedback appears on blur and clears on focus or edit`() {
        val initial = AuthFieldFeedback()
        val blurred = AuthFieldFeedbackPolicy.onBlur(
            feedback = initial,
            field = AuthField.EMAIL,
            mode = AuthFormMode.REGISTER,
            email = "foo",
            password = "12345678"
        )
        assertEquals(AuthFieldError.EMAIL_INVALID, blurred.emailError)

        val focused = AuthFieldFeedbackPolicy.onFocus(blurred, AuthField.EMAIL)
        assertNull(focused.emailError)

        val edited = AuthFieldFeedbackPolicy.onBlur(
            feedback = initial,
            field = AuthField.PASSWORD,
            mode = AuthFormMode.REGISTER,
            email = "driver@example.com",
            password = "short"
        ).let { AuthFieldFeedbackPolicy.onEdit(it, AuthField.PASSWORD) }
        assertNull(edited.passwordError)
    }

    @Test
    fun `submit feedback validates both fields and gates submission`() {
        val invalid = AuthFieldFeedbackPolicy.onSubmit(
            mode = AuthFormMode.REGISTER,
            email = "foo",
            password = "short"
        )
        assertFalse(invalid.canSubmit)
        assertEquals(AuthFieldError.EMAIL_INVALID, invalid.feedback.emailError)
        assertEquals(AuthFieldError.PASSWORD_TOO_SHORT, invalid.feedback.passwordError)

        val valid = AuthFieldFeedbackPolicy.onSubmit(
            mode = AuthFormMode.REGISTER,
            email = "driver@example.com",
            password = "12345678"
        )
        assertTrue(valid.canSubmit)
        assertEquals(AuthFieldFeedback(), valid.feedback)
    }
}
