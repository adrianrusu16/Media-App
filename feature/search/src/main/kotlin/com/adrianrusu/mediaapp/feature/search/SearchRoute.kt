package com.adrianrusu.mediaapp.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.adrianrusu.mediaapp.core.ui.overview.FeatureOverviewItem
import com.adrianrusu.mediaapp.core.ui.overview.FeatureOverviewScreen

@Composable
fun SearchRoute(modifier: Modifier = Modifier) {
    FeatureOverviewScreen(
        modifier = modifier,
        items = listOf(
            FeatureOverviewItem(
                title = "Safe search",
                body = "Search adapts to the current driving safety state."
            ),
            FeatureOverviewItem(
                title = "Providers",
                body = "More music sources can be enabled as the catalog grows."
            )
        )
    )
}
