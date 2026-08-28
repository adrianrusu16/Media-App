package com.adrianrusu.pandawave.core.ui.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.cardPadding
import com.adrianrusu.pandawave.core.designsystem.tokens.feedbackMaxWidth
import com.adrianrusu.pandawave.core.designsystem.tokens.feedbackSpacing
import com.adrianrusu.pandawave.core.designsystem.tokens.xs
import com.adrianrusu.pandawave.core.ui.R
import com.adrianrusu.pandawave.core.ui.components.BambooCard
import com.adrianrusu.pandawave.core.ui.components.BambooEmptyState
import com.adrianrusu.pandawave.core.ui.components.BambooTitleBody

@Composable
fun FeatureOverviewScreen(
    items: List<FeatureOverviewItem>,
    modifier: Modifier = Modifier,
    emptyTitle: String? = null,
    emptyBody: String? = null,
    compact: Boolean = false
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val resolvedEmptyTitle = emptyTitle ?: stringResource(R.string.pandawave_empty_state_title)
    val resolvedEmptyBody = emptyBody ?: stringResource(R.string.pandawave_empty_state_body)
    val rowSpacing = if (compact) tokens.spacing.xs else tokens.components.feedbackSpacing
    val cardPadding = if (compact) tokens.spacing.xs else tokens.components.cardPadding

    if (items.isEmpty()) {
        BambooEmptyState(
            title = resolvedEmptyTitle,
            body = resolvedEmptyBody,
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = tokens.components.feedbackMaxWidth)
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rowSpacing)
    ) {
        items.forEach { item ->
            FeatureOverviewRow(item = item, contentPadding = cardPadding)
        }
    }
}

@Composable
private fun FeatureOverviewRow(item: FeatureOverviewItem, contentPadding: Dp) {
    BambooCard(contentPadding = contentPadding) {
        BambooTitleBody(
            title = item.title,
            body = item.body
        )
    }
}
