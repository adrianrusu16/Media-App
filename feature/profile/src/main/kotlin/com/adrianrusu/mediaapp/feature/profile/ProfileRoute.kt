package com.adrianrusu.mediaapp.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.ui.components.BambooActionCard
import com.adrianrusu.mediaapp.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.mediaapp.core.ui.overview.FeatureOverviewItem
import com.adrianrusu.mediaapp.core.ui.overview.FeatureOverviewScreen

@Composable
fun ProfileRoute(onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalPandaWaveDesignTokens.current

    BambooRotaryColumn(
        modifier = modifier.testTag("profile-route"),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
    ) {
        FeatureOverviewScreen(
            items = listOf(
                FeatureOverviewItem(
                    title = stringResource(R.string.pandawave_profile_account_title),
                    body = stringResource(R.string.pandawave_profile_account_body)
                ),
                FeatureOverviewItem(
                    title = stringResource(R.string.pandawave_profile_session_title),
                    body = stringResource(R.string.pandawave_profile_session_body)
                )
            )
        )
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
