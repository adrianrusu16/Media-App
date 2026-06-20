package com.adrianrusu.mediaapp.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.ui.components.BambooActionCard
import com.adrianrusu.mediaapp.core.ui.overview.FeatureOverviewItem
import com.adrianrusu.mediaapp.core.ui.overview.FeatureOverviewScreen

@Composable
fun ProfileRoute(onSettingsClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalPandaWaveDesignTokens.current

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
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
            title = "Preferences",
            body = "Appearance, privacy, content, and diagnostics.",
            actionLabel = "Open",
            actionEnabled = true,
            onActionClick = onSettingsClick
        )
    }
}
