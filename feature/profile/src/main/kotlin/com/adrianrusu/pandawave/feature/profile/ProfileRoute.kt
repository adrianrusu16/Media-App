package com.adrianrusu.pandawave.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.sm
import com.adrianrusu.pandawave.core.designsystem.tokens.touchTargetMd
import com.adrianrusu.pandawave.core.designsystem.tokens.xs
import com.adrianrusu.pandawave.core.ui.components.BambooActionCard
import com.adrianrusu.pandawave.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.pandawave.core.ui.overview.FeatureOverviewItem
import com.adrianrusu.pandawave.core.ui.overview.FeatureOverviewScreen
import com.adrianrusu.pandawave.feature.profile.domain.AccountSessionsState
import com.adrianrusu.pandawave.feature.profile.domain.ProfileState
import java.text.DateFormat
import java.util.Date

sealed interface ProfileUiAccount {
    data object Anonymous : ProfileUiAccount

    data class Authenticated(
        val email: String,
        val accountStatus: String,
        val deviceLabel: String,
        val sessionCreatedAtEpochMillis: Long,
        val sessionLastActiveAtEpochMillis: Long
    ) : ProfileUiAccount
}

@Composable
fun ProfileRoute(
    account: ProfileUiAccount,
    accountActionsEnabled: Boolean,
    logoutInProgress: Boolean,
    logoutWarning: String?,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    profileState: ProfileState = ProfileState.SignedOut,
    onRefreshProfile: () -> Unit = { },
    onUpsertProfile: (String?) -> Unit = { },
    onUpdateProfileDisplayName: (String?) -> Unit = { },
    onDeleteProfile: () -> Unit = { },
    accountSessionsState: AccountSessionsState = AccountSessionsState.SignedOut,
    onRefreshAccountSessions: () -> Unit = { },
    onLoadNextDeviceSessionsPage: () -> Unit = { },
    onRevokeDeviceSession: (String) -> Unit = { },
    onDeleteAccount: () -> Unit = { }
) {
    val tokens = LocalPandaWaveDesignTokens.current

    BambooRotaryColumn(
        modifier = modifier.testTag("profile-route"),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
    ) {
        when (account) {
            ProfileUiAccount.Anonymous -> AnonymousAccountCards(
                actionsEnabled = accountActionsEnabled,
                onLoginClick = onLoginClick,
                onRegisterClick = onRegisterClick
            )

            is ProfileUiAccount.Authenticated -> {
                AuthenticatedAccountCards(
                    account = account,
                    actionsEnabled = accountActionsEnabled,
                    logoutInProgress = logoutInProgress,
                    onLogoutClick = onLogoutClick
                )
                ProfileProjectionCards(
                    state = profileState,
                    actionsEnabled = accountActionsEnabled,
                    onRefresh = onRefreshProfile,
                    onUpsert = onUpsertProfile,
                    onUpdateDisplayName = onUpdateProfileDisplayName,
                    onDelete = onDeleteProfile
                )
                AccountSessionCards(
                    state = accountSessionsState,
                    actionsEnabled = accountActionsEnabled,
                    onRefresh = onRefreshAccountSessions,
                    onLoadNextPage = onLoadNextDeviceSessionsPage,
                    onRevoke = onRevokeDeviceSession,
                    onDeleteAccount = onDeleteAccount
                )
            }
        }
        if (logoutWarning != null) {
            Text(
                text = logoutWarning,
                modifier = Modifier.testTag("profile-logout-warning"),
                color = Color(tokens.colors.error)
            )
        }
        ProfileActionCard(
            modifier = Modifier.testTag("profile-settings"),
            title = stringResource(R.string.pandawave_profile_preferences_title),
            body = stringResource(R.string.pandawave_profile_preferences_body),
            actionLabel = stringResource(R.string.pandawave_profile_open),
            actionEnabled = true,
            onActionClick = onSettingsClick
        )
    }
}

@Composable
private fun AccountSessionCards(
    state: AccountSessionsState,
    actionsEnabled: Boolean,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRevoke: (String) -> Unit,
    onDeleteAccount: () -> Unit
) {
    when (state) {
        AccountSessionsState.SignedOut -> Unit

        AccountSessionsState.Loading -> ProfileActionCard(
            modifier = Modifier.testTag("profile-sessions-loading"),
            title = stringResource(R.string.pandawave_profile_sessions_title),
            body = stringResource(R.string.pandawave_profile_sessions_loading),
            actionLabel = stringResource(R.string.pandawave_profile_refresh),
            actionEnabled = false,
            onActionClick = onRefresh
        )

        is AccountSessionsState.Failure -> ProfileActionCard(
            modifier = Modifier.testTag("profile-sessions-failure"),
            title = stringResource(R.string.pandawave_profile_sessions_title),
            body = stringResource(R.string.pandawave_profile_sessions_error, state.errorType),
            actionLabel = stringResource(R.string.pandawave_profile_retry),
            actionEnabled = actionsEnabled && state.retryable,
            onActionClick = onRefresh
        )

        is AccountSessionsState.Ready -> {
            val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            val operationPending = state.pendingSessionId != null || state.deletingAccount
            FeatureOverviewScreen(
                compact = true,
                items = listOf(
                    FeatureOverviewItem(
                        stringResource(R.string.pandawave_profile_email_title),
                        state.account.primaryEmail
                    ),
                    FeatureOverviewItem(
                        stringResource(R.string.pandawave_profile_status_title),
                        state.account.status
                    )
                )
            )
            if (state.sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.pandawave_profile_sessions_empty),
                    modifier = Modifier.testTag("profile-sessions-empty")
                )
            }
            state.sessions.forEachIndexed { index, session ->
                ProfileActionCard(
                    modifier = Modifier.testTag("profile-session-revoke-$index"),
                    title = session.deviceLabel.ifBlank {
                        stringResource(R.string.pandawave_profile_unknown_device)
                    },
                    body = stringResource(
                        R.string.pandawave_profile_session_summary,
                        dateFormat.format(Date(session.lastUsedAtEpochMillis)),
                        if (session.current) {
                            stringResource(R.string.pandawave_profile_current_session)
                        } else {
                            stringResource(R.string.pandawave_profile_other_session)
                        }
                    ),
                    actionLabel = stringResource(
                        if (state.pendingSessionId == session.id) {
                            R.string.pandawave_profile_revoking_session
                        } else {
                            R.string.pandawave_profile_revoke_session
                        }
                    ),
                    actionEnabled = actionsEnabled && !operationPending && !session.current,
                    onActionClick = { onRevoke(session.id) }
                )
            }
            if (state.hasNextPage) {
                ProfileActionCard(
                    modifier = Modifier.testTag("profile-sessions-load-more"),
                    title = stringResource(R.string.pandawave_profile_sessions_more_title),
                    body = stringResource(R.string.pandawave_profile_sessions_more_body),
                    actionLabel = stringResource(R.string.pandawave_profile_load_more),
                    actionEnabled = actionsEnabled && !operationPending,
                    onActionClick = onLoadNextPage
                )
            }
            var confirmDelete by rememberSaveable(state.account.id) { mutableStateOf(false) }
            ProfileActionCard(
                modifier = Modifier.testTag("profile-account-delete"),
                title = stringResource(R.string.pandawave_profile_delete_account_title),
                body = stringResource(R.string.pandawave_profile_delete_account_body),
                actionLabel = stringResource(
                    if (state.deletingAccount) {
                        R.string.pandawave_profile_deleting_account
                    } else {
                        R.string.pandawave_profile_delete_account_action
                    }
                ),
                actionEnabled = actionsEnabled && !operationPending,
                onActionClick = { confirmDelete = true }
            )
            if (confirmDelete) {
                ProfileActionCard(
                    modifier = Modifier.testTag("profile-account-delete-confirm"),
                    title = stringResource(R.string.pandawave_profile_confirm_delete_account_title),
                    body = stringResource(R.string.pandawave_profile_confirm_delete_account_body),
                    actionLabel = stringResource(R.string.pandawave_profile_confirm_delete_account_action),
                    actionEnabled = actionsEnabled && !operationPending,
                    onActionClick = {
                        confirmDelete = false
                        onDeleteAccount()
                    }
                )
            }
        }
    }
}

@Composable
private fun AnonymousAccountCards(actionsEnabled: Boolean, onLoginClick: () -> Unit, onRegisterClick: () -> Unit) {
    ProfileActionCard(
        modifier = Modifier.testTag("profile-login"),
        title = stringResource(R.string.pandawave_profile_login_title),
        body = stringResource(R.string.pandawave_profile_login_body),
        actionLabel = stringResource(R.string.pandawave_profile_login_action),
        actionEnabled = actionsEnabled,
        onActionClick = onLoginClick
    )
    ProfileActionCard(
        modifier = Modifier.testTag("profile-register"),
        title = stringResource(R.string.pandawave_profile_register_title),
        body = stringResource(R.string.pandawave_profile_register_body),
        actionLabel = stringResource(R.string.pandawave_profile_register_action),
        actionEnabled = actionsEnabled,
        onActionClick = onRegisterClick
    )
}

@Composable
private fun AuthenticatedAccountCards(
    account: ProfileUiAccount.Authenticated,
    actionsEnabled: Boolean,
    logoutInProgress: Boolean,
    onLogoutClick: () -> Unit
) {
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    FeatureOverviewScreen(
        compact = true,
        items = listOf(
            FeatureOverviewItem(stringResource(R.string.pandawave_profile_email_title), account.email),
            FeatureOverviewItem(stringResource(R.string.pandawave_profile_status_title), account.accountStatus),
            FeatureOverviewItem(stringResource(R.string.pandawave_profile_device_title), account.deviceLabel),
            FeatureOverviewItem(
                stringResource(R.string.pandawave_profile_session_created_title),
                dateFormat.format(Date(account.sessionCreatedAtEpochMillis))
            ),
            FeatureOverviewItem(
                stringResource(R.string.pandawave_profile_session_active_title),
                dateFormat.format(Date(account.sessionLastActiveAtEpochMillis))
            )
        )
    )
    ProfileActionCard(
        modifier = Modifier.testTag("profile-logout"),
        title = stringResource(R.string.pandawave_profile_logout_title),
        body = stringResource(R.string.pandawave_profile_logout_body),
        actionLabel = stringResource(
            if (logoutInProgress) R.string.pandawave_profile_logging_out else R.string.pandawave_profile_logout_action
        ),
        actionEnabled = actionsEnabled && !logoutInProgress,
        onActionClick = onLogoutClick
    )
}

@Composable
private fun ProfileProjectionCards(
    state: ProfileState,
    actionsEnabled: Boolean,
    onRefresh: () -> Unit,
    onUpsert: (String?) -> Unit,
    onUpdateDisplayName: (String?) -> Unit,
    onDelete: () -> Unit
) {
    when (state) {
        ProfileState.SignedOut -> Unit

        ProfileState.Loading -> ProfileActionCard(
            modifier = Modifier.testTag("profile-loading"),
            title = stringResource(R.string.pandawave_profile_canopy_title),
            body = stringResource(R.string.pandawave_profile_loading),
            actionLabel = stringResource(R.string.pandawave_profile_refresh),
            actionEnabled = actionsEnabled,
            onActionClick = onRefresh
        )

        ProfileState.Missing -> ProfileActionCard(
            modifier = Modifier.testTag("profile-create"),
            title = stringResource(R.string.pandawave_profile_canopy_title),
            body = stringResource(R.string.pandawave_profile_missing),
            actionLabel = stringResource(R.string.pandawave_profile_create_action),
            actionEnabled = actionsEnabled,
            onActionClick = { onUpsert(null) }
        )

        is ProfileState.Failure -> ProfileActionCard(
            modifier = Modifier.testTag("profile-failure"),
            title = stringResource(R.string.pandawave_profile_canopy_title),
            body = stringResource(R.string.pandawave_profile_error, state.errorType),
            actionLabel = stringResource(R.string.pandawave_profile_retry),
            actionEnabled = actionsEnabled && state.retryable,
            onActionClick = onRefresh
        )

        is ProfileState.Ready -> {
            var displayName by rememberSaveable(state.profile.id, state.profile.displayName) {
                mutableStateOf(state.profile.displayName.orEmpty())
            }
            FeatureOverviewScreen(
                compact = true,
                items = listOf(
                    FeatureOverviewItem(
                        title = stringResource(R.string.pandawave_profile_display_name),
                        body = state.profile.displayName
                            ?: stringResource(R.string.pandawave_profile_display_name_absent)
                    )
                )
            )
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                modifier = Modifier.testTag("profile-display-name-input"),
                enabled = actionsEnabled,
                label = { Text(stringResource(R.string.pandawave_profile_display_name)) }
            )
            ProfileActionCard(
                modifier = Modifier.testTag("profile-save-display-name"),
                title = stringResource(R.string.pandawave_profile_display_name),
                body = stringResource(R.string.pandawave_profile_display_name_body),
                actionLabel = stringResource(R.string.pandawave_profile_save),
                actionEnabled = actionsEnabled,
                onActionClick = { onUpdateDisplayName(displayName) }
            )
            ProfileActionCard(
                modifier = Modifier.testTag("profile-clear-display-name"),
                title = stringResource(R.string.pandawave_profile_clear_display_name),
                body = stringResource(R.string.pandawave_profile_clear_display_name_body),
                actionLabel = stringResource(R.string.pandawave_profile_clear),
                actionEnabled = actionsEnabled,
                onActionClick = { onUpdateDisplayName(null) }
            )
            ProfileActionCard(
                modifier = Modifier.testTag("profile-delete"),
                title = stringResource(R.string.pandawave_profile_delete_title),
                body = stringResource(R.string.pandawave_profile_delete_body),
                actionLabel = stringResource(R.string.pandawave_profile_delete_action),
                actionEnabled = actionsEnabled,
                onActionClick = onDelete
            )
        }
    }
}

@Composable
private fun ProfileActionCard(
    title: String,
    body: String,
    actionLabel: String,
    actionEnabled: Boolean,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current

    BambooActionCard(
        title = title,
        body = body,
        actionLabel = actionLabel,
        actionEnabled = actionEnabled,
        onActionClick = onActionClick,
        modifier = modifier,
        contentPadding = tokens.spacing.xs,
        minHeight = tokens.sizing.touchTargetMd
    )
}
