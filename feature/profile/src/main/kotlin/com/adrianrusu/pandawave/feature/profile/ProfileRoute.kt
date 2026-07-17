package com.adrianrusu.pandawave.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.md
import com.adrianrusu.pandawave.core.ui.components.BambooActionCard
import com.adrianrusu.pandawave.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.pandawave.core.ui.overview.FeatureOverviewItem
import com.adrianrusu.pandawave.core.ui.overview.FeatureOverviewScreen
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
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current

    BambooRotaryColumn(
        modifier = modifier.testTag("profile-route"),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
    ) {
        when (account) {
            ProfileUiAccount.Anonymous -> AnonymousAccountCards(
                actionsEnabled = accountActionsEnabled,
                onLoginClick = onLoginClick,
                onRegisterClick = onRegisterClick
            )
            is ProfileUiAccount.Authenticated -> AuthenticatedAccountCards(
                account = account,
                actionsEnabled = accountActionsEnabled,
                logoutInProgress = logoutInProgress,
                onLogoutClick = onLogoutClick
            )
        }
        if (logoutWarning != null) {
            Text(
                text = logoutWarning,
                modifier = Modifier.testTag("profile-logout-warning"),
                color = MaterialTheme.colorScheme.error
            )
        }
        BambooActionCard(
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
private fun AnonymousAccountCards(
    actionsEnabled: Boolean,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit
) {
    BambooActionCard(
        modifier = Modifier.testTag("profile-login"),
        title = stringResource(R.string.pandawave_profile_login_title),
        body = stringResource(R.string.pandawave_profile_login_body),
        actionLabel = stringResource(R.string.pandawave_profile_login_action),
        actionEnabled = actionsEnabled,
        onActionClick = onLoginClick
    )
    BambooActionCard(
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
        items = listOf(
            FeatureOverviewItem(
                title = stringResource(R.string.pandawave_profile_email_title),
                body = account.email
            ),
            FeatureOverviewItem(
                title = stringResource(R.string.pandawave_profile_status_title),
                body = account.accountStatus
            ),
            FeatureOverviewItem(
                title = stringResource(R.string.pandawave_profile_device_title),
                body = account.deviceLabel
            ),
            FeatureOverviewItem(
                title = stringResource(R.string.pandawave_profile_session_created_title),
                body = dateFormat.format(Date(account.sessionCreatedAtEpochMillis))
            ),
            FeatureOverviewItem(
                title = stringResource(R.string.pandawave_profile_session_active_title),
                body = dateFormat.format(Date(account.sessionLastActiveAtEpochMillis))
            )
        )
    )
    BambooActionCard(
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
