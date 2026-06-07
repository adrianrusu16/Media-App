package com.adrianrusu.mediaapp.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.adrianrusu.mediaapp.core.ui.overview.FeatureOverviewItem
import com.adrianrusu.mediaapp.core.ui.overview.FeatureOverviewScreen

@Composable
fun ProfileRoute(modifier: Modifier = Modifier) {
    FeatureOverviewScreen(
        modifier = modifier,
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
}
