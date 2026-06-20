package com.adrianrusu.mediaapp.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.lg
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.ui.discovery.BambooCategoryCard
import com.adrianrusu.mediaapp.core.ui.discovery.BambooCategoryItem
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaAction
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaItem
import com.adrianrusu.mediaapp.core.ui.discovery.BambooMediaListRow
import com.adrianrusu.mediaapp.core.ui.discovery.BambooSearchBar
import com.adrianrusu.mediaapp.core.ui.discovery.BambooSectionHeader
import com.adrianrusu.mediaapp.core.ui.discovery.BambooWaveform
import com.adrianrusu.mediaapp.core.ui.focus.BambooFocusableLazyRow
import com.adrianrusu.mediaapp.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.mediaapp.core.ui.icons.PandaWaveIcons

@Composable
fun SearchRoute(modifier: Modifier = Modifier) {
    val tokens = LocalPandaWaveDesignTokens.current
    val categories = searchCategories()
    val recent = searchRecentItems()
    var query by remember { mutableStateOf("") }

    BambooRotaryColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("search-route"),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg)
    ) {
        BambooSectionHeader(
            title = "Search",
            subtitle = "Type or use voice to find music, stations, and saved collections."
        )

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
            BambooSearchBar(
                modifier = Modifier.testTag("search-input"),
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search PandaWave",
                onVoiceClick = {}
            )
            BambooWaveform(active = query.isBlank())
        }

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
            BambooSectionHeader(title = "Browse by mood")
            BambooFocusableLazyRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                contentPadding = PaddingValues(horizontal = tokens.spacing.md)
            ) {
                items(categories, key = { it.id }) { category ->
                    BambooCategoryCard(
                        modifier = Modifier.testTag("search-category-${category.id}"),
                        category = category,
                        icon = when (category.id) {
                            "chill" -> PandaWaveIcons.Relax
                            "focus" -> PandaWaveIcons.Nature
                            "energy" -> PandaWaveIcons.Energy
                            else -> PandaWaveIcons.Equalizer
                        },
                        accentColor = when (category.id) {
                            "energy" -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        onClick = {}
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
            BambooSectionHeader(title = "Recent searches")
            recent.forEach { item ->
                BambooMediaListRow(
                    modifier = Modifier.testTag("search-recent-${item.id}"),
                    item = item,
                    icon = PandaWaveIcons.MusicLibrary,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    onClick = {}
                )
            }
        }
    }
}

private fun searchCategories(): List<BambooCategoryItem> = listOf(
    BambooCategoryItem(
        id = "chill",
        title = "Chill",
        description = "Soft, slow, and easy."
    ),
    BambooCategoryItem(
        id = "focus",
        title = "Focus",
        description = "Instrumental and low-distraction."
    ),
    BambooCategoryItem(
        id = "energy",
        title = "High Energy",
        description = "Upbeat without clutter."
    ),
    BambooCategoryItem(
        id = "nature",
        title = "Nature Sounds",
        description = "Rain, leaves, and open air."
    )
)

private fun searchRecentItems(): List<BambooMediaItem> = listOf(
    BambooMediaItem(
        id = "green-tea",
        title = "Green tea lo-fi",
        subtitle = "Search",
        description = "Last opened today",
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "quiet-highway",
        title = "Quiet highway",
        subtitle = "Search",
        description = "Recently explored station",
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "forest-rain",
        title = "Forest rain",
        subtitle = "Search",
        description = "Nature category result",
        action = BambooMediaAction.Unavailable
    )
)
