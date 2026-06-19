package com.adrianrusu.mediaapp.core.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.ui.components.BambooCard
import com.adrianrusu.mediaapp.core.ui.components.BambooEmptyState
import com.adrianrusu.mediaapp.core.ui.components.BambooTitleBody

@Composable
fun FeatureOverviewScreen(
    items: List<FeatureOverviewItem>,
    modifier: Modifier = Modifier,
    emptyTitle: String = "Nothing here yet",
    emptyBody: String = "Content will appear here when it becomes available."
) {
    val tokens = LocalPandaWaveDesignTokens.current

    if (items.isEmpty()) {
        BambooEmptyState(
            title = emptyTitle,
            body = emptyBody,
            modifier = modifier.fillMaxWidth()
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
    ) {
        items.forEach { item ->
            FeatureOverviewRow(item = item)
        }
    }
}

@Composable
private fun FeatureOverviewRow(item: FeatureOverviewItem) {
    BambooCard {
        BambooTitleBody(
            title = item.title,
            body = item.body
        )
    }
}
