package com.adrianrusu.mediaapp.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

@Composable
fun SearchRoute(modifier: Modifier = Modifier) {
    val tokens = LocalPandaWaveDesignTokens.current
    val categories = searchCategories()
    val recent = searchRecentItems()
    var query by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg)
    ) {
        BambooSectionHeader(
            title = "Search",
            subtitle = "Type or use voice to find music, stations, and saved collections."
        )

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
            BambooSearchBar(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search PandaWave",
                onVoiceClick = {}
            )
            BambooWaveform(active = query.isBlank())
        }

        Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)) {
            BambooSectionHeader(title = "Browse by mood")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                contentPadding = PaddingValues(horizontal = tokens.spacing.md)
            ) {
                items(categories, key = { it.id }) { category ->
                    BambooCategoryCard(
                        category = category,
                        icon = when (category.id) {
                            "chill" -> Icons.Filled.Spa
                            "focus" -> Icons.Filled.Eco
                            "energy" -> Icons.Filled.Bolt
                            else -> Icons.Filled.GraphicEq
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
                    item = item,
                    icon = Icons.Filled.LibraryMusic,
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
