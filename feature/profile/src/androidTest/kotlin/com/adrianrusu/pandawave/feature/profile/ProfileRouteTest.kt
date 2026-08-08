package com.adrianrusu.pandawave.feature.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.feature.profile.domain.ProfileDetails
import com.adrianrusu.pandawave.feature.profile.domain.ProfileState
import org.junit.Assert.assertEquals
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
    @Test
    fun authenticatedProfileProjectsDisplayNameAndForwardsUpdate() {
        val updates = mutableListOf<String?>()
        compose.setContent {
            MaterialTheme {
                ProfileRoute(
                    account = authenticatedAccount(),
                    accountActionsEnabled = true,
                    logoutInProgress = false,
                    logoutWarning = null,
                    profileState = ProfileState.Ready(
                        profile = ProfileDetails(
                            id = "profile-1",
                            externalUserId = "account-1",
                            displayName = "Rin",
                            createdAtEpochMillis = null,
                            updatedAtEpochMillis = null
                        ),
                        theme = PandaWaveThemePreference.SystemDefault
                    ),
                    onLoginClick = { },
                    onRegisterClick = { },
                    onLogoutClick = { },
                    onSettingsClick = { },
                    onRefreshProfile = { },
                    onUpsertProfile = { },
                    onUpdateProfileDisplayName = updates::add,
                    onDeleteProfile = { }
                )
            }
        }

        compose.onNodeWithText("Rin").assertIsDisplayed()
        compose.onNodeWithTag("profile-save-display-name").performClick()
        assertEquals(listOf("Rin"), updates)
    }

    private fun authenticatedAccount() = ProfileUiAccount.Authenticated(
        email = "driver@example.com",
        accountStatus = "active",
        deviceLabel = "Panda Emulator",
        sessionCreatedAtEpochMillis = 1_700_000_000_000,
        sessionLastActiveAtEpochMillis = 1_700_000_100_000
    )
}
