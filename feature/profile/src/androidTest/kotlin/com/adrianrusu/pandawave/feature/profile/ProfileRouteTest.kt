package com.adrianrusu.pandawave.feature.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ProfileRouteTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun authenticatedProfileExposesOnlyApprovedAccountAndSessionFields() {
        compose.setContent {
            MaterialTheme {
                ProfileRoute(
                    account = ProfileUiAccount.Authenticated(
                        email = "driver@example.com",
                        accountStatus = "active",
                        deviceLabel = "Panda Emulator",
                        sessionCreatedAtEpochMillis = 1_700_000_000_000,
                        sessionLastActiveAtEpochMillis = 1_700_000_100_000
                    ),
                    accountActionsEnabled = true,
                    logoutInProgress = false,
                    logoutWarning = null,
                    onLoginClick = { },
                    onRegisterClick = { },
                    onLogoutClick = { },
                    onSettingsClick = { }
                )
            }
        }

        compose.onNodeWithText("driver@example.com").assertIsDisplayed()
        compose.onNodeWithText("Panda Emulator").assertIsDisplayed()
        compose.onNodeWithText("account-internal-id").assertDoesNotExist()
        compose.onNodeWithText("session-internal-id").assertDoesNotExist()
        compose.onNodeWithText("provider-subject").assertDoesNotExist()
        compose.onNodeWithText("token-expiry").assertDoesNotExist()
    }
}
