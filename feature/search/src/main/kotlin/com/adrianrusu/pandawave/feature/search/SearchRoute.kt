package com.adrianrusu.pandawave.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.mediaCarouselSpacing
import com.adrianrusu.pandawave.core.designsystem.tokens.mediaSectionSpacing
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.ui.discovery.BambooCategoryCard
import com.adrianrusu.pandawave.core.ui.discovery.BambooCategoryItem
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaAction
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaItem
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaListRow
import com.adrianrusu.pandawave.core.ui.discovery.BambooSearchBar
import com.adrianrusu.pandawave.core.ui.discovery.BambooSectionHeader
import com.adrianrusu.pandawave.core.ui.discovery.BambooWaveform
import com.adrianrusu.pandawave.core.ui.focus.BambooFocusableLazyRow
import com.adrianrusu.pandawave.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.pandawave.core.ui.icons.PandaWaveIcons

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
        verticalArrangement = Arrangement.spacedBy(tokens.components.mediaSectionSpacing)
    ) {
        BambooSectionHeader(
            title = stringResource(R.string.pandawave_search_title),
            subtitle = stringResource(R.string.pandawave_search_subtitle)
        )

        Column(verticalArrangement = Arrangement.spacedBy(tokens.components.mediaCarouselSpacing)) {
            BambooSearchBar(
                modifier = Modifier.testTag("search-input"),
                query = query,
                onQueryChange = { query = it },
                placeholder = stringResource(R.string.pandawave_search_placeholder),
                onVoiceClick = {}
            )
            BambooWaveform(active = query.isBlank())
        }

        Column(verticalArrangement = Arrangement.spacedBy(tokens.components.mediaCarouselSpacing)) {
            BambooSectionHeader(title = stringResource(R.string.pandawave_search_browse_mood))
            BambooFocusableLazyRow(
                horizontalArrangement = Arrangement.spacedBy(tokens.components.mediaCarouselSpacing),
                contentPadding = PaddingValues(horizontal = tokens.components.mediaCarouselSpacing)
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
                            "energy" -> Color(tokens.colors.secondary)
                            else -> Color(tokens.colors.primary)
                        },
                        onClick = {
                            PandaLog.v(PandaLog.Tag.SEARCH) {
                                "click action=select_category section=mood categoryId=${category.id} title=${PandaLog.field(category.title)}"
                            }
                        }
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(tokens.components.mediaCarouselSpacing)) {
            BambooSectionHeader(title = stringResource(R.string.pandawave_search_recent))
            recent.forEach { item ->
                BambooMediaListRow(
                    modifier = Modifier.testTag("search-recent-${item.id}"),
                    item = item,
                    icon = PandaWaveIcons.MusicLibrary,
                    accentColor = Color(tokens.colors.secondary),
                    onClick = {
                        PandaLog.v(PandaLog.Tag.SEARCH) {
                            "click action=play section=recent trackId=${item.id} title=${PandaLog.field(item.title)}"
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun searchCategories(): List<BambooCategoryItem> = listOf(
    BambooCategoryItem(
        id = "chill",
        title = stringResource(R.string.pandawave_search_chill_title),
        description = stringResource(R.string.pandawave_search_chill_description)
    ),
    BambooCategoryItem(
        id = "focus",
        title = stringResource(R.string.pandawave_search_focus_title),
        description = stringResource(R.string.pandawave_search_focus_description)
    ),
    BambooCategoryItem(
        id = "energy",
        title = stringResource(R.string.pandawave_search_energy_title),
        description = stringResource(R.string.pandawave_search_energy_description)
    ),
    BambooCategoryItem(
        id = "nature",
        title = stringResource(R.string.pandawave_search_nature_title),
        description = stringResource(R.string.pandawave_search_nature_description)
    )
)

@Composable
private fun searchRecentItems(): List<BambooMediaItem> = listOf(
    BambooMediaItem(
        id = "green-tea",
        title = stringResource(R.string.pandawave_search_green_tea_title),
        subtitle = stringResource(R.string.pandawave_search_result_type),
        description = stringResource(R.string.pandawave_search_green_tea_description),
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "quiet-highway",
        title = stringResource(R.string.pandawave_search_quiet_highway_title),
        subtitle = stringResource(R.string.pandawave_search_result_type),
        description = stringResource(R.string.pandawave_search_quiet_highway_description),
        action = BambooMediaAction.Unavailable
    ),
    BambooMediaItem(
        id = "forest-rain",
        title = stringResource(R.string.pandawave_search_forest_rain_title),
        subtitle = stringResource(R.string.pandawave_search_result_type),
        description = stringResource(R.string.pandawave_search_forest_rain_description),
        action = BambooMediaAction.Unavailable
    )
)
