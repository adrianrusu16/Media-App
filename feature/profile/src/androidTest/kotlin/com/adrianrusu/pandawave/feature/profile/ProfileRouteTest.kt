package com.adrianrusu.pandawave.feature.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAccount
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthSession
import com.adrianrusu.pandawave.feature.profile.domain.AccountSessionsState
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

    @Test
    fun readyAccountSessionsExposePaginationRevocationAndConfirmedDeletion() {
        val revoked = mutableListOf<String>()
        var pageLoads = 0
        var deletes = 0
        compose.setContent {
            MaterialTheme {
                ProfileRoute(
                    account = authenticatedAccount(),
                    accountActionsEnabled = true,
                    logoutInProgress = false,
                    logoutWarning = null,
                    accountSessionsState = AccountSessionsState.Ready(
                        account = EngineAccount("account-1", "driver@example.com", "active", 10),
                        sessions = listOf(
                            EngineAuthSession("session-current", "Panda Emulator", 20, 30, 40, true),
                            EngineAuthSession("session-other", "Garage tablet", 21, 31, 41, false)
                        ),
                        hasNextPage = true
                    ),
                    onLoginClick = { },
                    onRegisterClick = { },
                    onLogoutClick = { },
                    onSettingsClick = { },
                    onLoadNextDeviceSessionsPage = { pageLoads++ },
                    onRevokeDeviceSession = revoked::add,
                    onDeleteAccount = { deletes++ }
                )
            }
        }

        compose.onNodeWithText("Garage tablet").assertIsDisplayed()
        compose.onNodeWithTag("profile-session-revoke-1").performClick()
        compose.onNodeWithTag("profile-sessions-load-more").performClick()
        compose.onNodeWithTag("profile-account-delete").performClick()
        assertEquals(0, deletes)
        compose.onNodeWithTag("profile-account-delete-confirm").performClick()

        assertEquals(listOf("session-other"), revoked)
        assertEquals(1, pageLoads)
        assertEquals(1, deletes)
        compose.onNodeWithText("session-other").assertDoesNotExist()
        compose.onNodeWithText("access_token").assertDoesNotExist()
    }

    @Test
    fun accountSessionPendingAndDeletingStatesDisableDestructiveActions() {
        compose.setContent {
            MaterialTheme {
                ProfileRoute(
                    account = authenticatedAccount(),
                    accountActionsEnabled = true,
                    logoutInProgress = false,
                    logoutWarning = null,
                    accountSessionsState = AccountSessionsState.Ready(
                        account = EngineAccount("account-1", "driver@example.com", "active", 10),
                        sessions = listOf(EngineAuthSession("session-other", "Garage tablet", 21, 31, 41, false)),
                        hasNextPage = true,
                        pendingSessionId = "session-other",
                        deletingAccount = true
                    ),
                    onLoginClick = { }, onRegisterClick = { }, onLogoutClick = { }, onSettingsClick = { }
                )
            }
        }

        compose.onNodeWithTag("profile-session-revoke-0").assertIsNotEnabled()
        compose.onNodeWithTag("profile-sessions-load-more").assertIsNotEnabled()
        compose.onNodeWithTag("profile-account-delete").assertIsNotEnabled()
    }

    @Test
    fun accountSessionLoadingAndFailureExposeOnlyTypedRetryState() {
        compose.setContent {
            MaterialTheme {
                ProfileRoute(
                    account = authenticatedAccount(), accountActionsEnabled = true,
                    logoutInProgress = false, logoutWarning = null,
                    accountSessionsState = AccountSessionsState.Loading,
                    onLoginClick = { }, onRegisterClick = { }, onLogoutClick = { }, onSettingsClick = { }
                )
            }
        }
        compose.onNodeWithTag("profile-sessions-loading").assertIsDisplayed()

        compose.setContent {
            MaterialTheme {
                ProfileRoute(
                    account = authenticatedAccount(), accountActionsEnabled = true,
                    logoutInProgress = false, logoutWarning = null,
                    accountSessionsState = AccountSessionsState.Failure("network", retryable = true),
                    onLoginClick = { }, onRegisterClick = { }, onLogoutClick = { }, onSettingsClick = { }
                )
            }
        }
        compose.onNodeWithTag("profile-sessions-failure").assertIsDisplayed()
        compose.onNodeWithText("network", substring = true).assertIsDisplayed()
    }

    @Test
    fun deleteAccountConfirmationResetsWhenAccountIdentityChanges() {
        val sessionState = mutableStateOf(
            AccountSessionsState.Ready(
                account = EngineAccount("account-1", "first@example.com", "active", 10),
                sessions = emptyList(),
                hasNextPage = false
            )
        )
        compose.setContent {
            MaterialTheme {
                ProfileRoute(
                    account = authenticatedAccount(), accountActionsEnabled = true,
                    logoutInProgress = false, logoutWarning = null,
                    accountSessionsState = sessionState.value,
                    onLoginClick = { }, onRegisterClick = { }, onLogoutClick = { }, onSettingsClick = { }
                )
            }
        }

        compose.onNodeWithTag("profile-account-delete").performClick()
        compose.onNodeWithTag("profile-account-delete-confirm").assertIsDisplayed()
        sessionState.value = AccountSessionsState.Ready(
            account = EngineAccount("account-2", "second@example.com", "active", 11),
            sessions = emptyList(),
            hasNextPage = false
        )
        compose.waitForIdle()

        compose.onNodeWithTag("profile-account-delete-confirm").assertDoesNotExist()
    }

    private fun authenticatedAccount() = ProfileUiAccount.Authenticated(
        email = "driver@example.com",
        accountStatus = "active",
        deviceLabel = "Panda Emulator",
        sessionCreatedAtEpochMillis = 1_700_000_000_000,
        sessionLastActiveAtEpochMillis = 1_700_000_100_000
    )
}
