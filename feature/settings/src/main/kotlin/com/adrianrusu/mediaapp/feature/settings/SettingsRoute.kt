package com.adrianrusu.mediaapp.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.adrianrusu.mediaapp.core.ui.overview.FeatureOverviewItem
import com.adrianrusu.mediaapp.core.ui.overview.FeatureOverviewScreen

@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
) {
    FeatureOverviewScreen(
        modifier = modifier,
        items = listOf(
            FeatureOverviewItem(
                title = "Privacy",
                body = "Control diagnostics, personalization, and data choices.",
            ),
            FeatureOverviewItem(
                title = "Vehicle mode",
                body = "Review display, safety, and vehicle-specific behavior.",
            ),
        ),
    )
}
