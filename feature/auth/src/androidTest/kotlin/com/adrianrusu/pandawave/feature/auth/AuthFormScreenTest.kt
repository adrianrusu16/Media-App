package com.adrianrusu.pandawave.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.adrianrusu.pandawave.core.designsystem.theme.PandaWaveTheme
import com.adrianrusu.pandawave.feature.auth.domain.AuthFormState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuthFormScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun invalidEmailAppearsOnBlurAndClearsOnFocus() {
        setRegisterContent()

        compose.onNodeWithTag("auth-email").performTextInput("foo")
        compose.onNodeWithTag("auth-password").performClick()
        compose.onNodeWithText("Enter a valid email address.").assertIsDisplayed()

        compose.onNodeWithTag("auth-email").performClick()
        compose.onNodeWithText("Enter a valid email address.").assertDoesNotExist()
    }

    @Test
    fun imeDoneSubmitsAValidFormExactlyOnce() {
        var submissions = 0
        var submittedEmail = ""
        var submittedPassword = ""
        setRegisterContent { email, password ->
            submissions += 1
            submittedEmail = email
            submittedPassword = password.decodeToString()
            password.fill(0)
        }

        compose.onNodeWithTag("auth-email").performTextInput(" driver@example.com ")
        compose.onNodeWithTag("auth-password").performTextInput("12345678")
        compose.onNodeWithTag("auth-password").performImeAction()

        compose.runOnIdle {
            assertEquals(1, submissions)
            assertEquals("driver@example.com", submittedEmail)
            assertEquals("12345678", submittedPassword)
        }
    }

    @Test
    fun imeDoneDoesNotSubmitAnInvalidForm() {
        var submissions = 0
        setRegisterContent { _, password ->
            submissions += 1
            password.fill(0)
        }

        compose.onNodeWithTag("auth-email").performTextInput("foo")
        compose.onNodeWithTag("auth-password").performTextInput("short")
        compose.onNodeWithTag("auth-password").performImeAction()

        compose.onNodeWithText("Enter a valid email address.").assertIsDisplayed()
        compose.onNodeWithText("Password must contain at least 8 characters.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, submissions) }
    }

    private fun setRegisterContent(
        onSubmit: (String, ByteArray) -> Unit = { _, password -> password.fill(0) }
    ) {
        compose.setContent {
            PandaWaveTheme(darkTheme = true) {
                AuthFormScreen(
                    state = AuthFormState.register(),
                    enabled = true,
                    onSubmit = onSubmit,
                    onResend = {},
                    onCancel = {}
                )
            }
        }
    }
}
