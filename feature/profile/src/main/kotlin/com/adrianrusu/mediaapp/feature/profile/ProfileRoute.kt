package com.adrianrusu.mediaapp.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
                    title = "Account",
                    body = "Sign-in and account details will live here."
                ),
                FeatureOverviewItem(
                    title = "Session",
                    body = "Manage active sessions and trusted devices."
                )
            )
        )
        BambooActionCard(
            modifier = Modifier.testTag("profile-settings"),
            title = "Preferences",
            body = "Appearance, privacy, content, and diagnostics.",
            actionLabel = "Open",
            actionEnabled = true,
            onActionClick = onSettingsClick
        )
    }
}
