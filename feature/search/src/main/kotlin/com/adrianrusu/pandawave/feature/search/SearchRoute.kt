package com.adrianrusu.pandawave.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.md
import com.adrianrusu.pandawave.core.designsystem.tokens.mediaCarouselSpacing
import com.adrianrusu.pandawave.core.designsystem.tokens.mediaSectionSpacing
import com.adrianrusu.pandawave.core.designsystem.tokens.sm
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
import com.adrianrusu.pandawave.feature.search.domain.SearchState
import com.adrianrusu.pandawave.feature.search.domain.SearchTrack
import com.adrianrusu.pandawave.feature.search.presentation.SearchViewModel

@Composable
fun SearchRoute(
    modifier: Modifier = Modifier,
    onOpenNowPlaying: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchRoute(
        state = state,
        modifier = modifier,
        onQueryChange = viewModel::onQueryChange,
        onLoadNext = viewModel::loadNext,
        onRetry = viewModel::retry,
        onPlay = viewModel::play,
        onOpenNowPlaying = onOpenNowPlaying
    )
}

@Composable
fun SearchRoute(
    state: SearchState,
    onQueryChange: (String) -> Unit,
    onLoadNext: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (mediaId: String, title: String) -> Unit,
    onOpenNowPlaying: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val categories = searchCategories()

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
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.pandawave_search_placeholder),
                onVoiceClick = {}
            )
            BambooWaveform(active = state.query.isBlank())
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
                        onClick = { onQueryChange(category.title) }
                    )
                }
            }
        }

        state.errorType?.let {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("search-error"),
                color = Color(tokens.colors.error),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(tokens.spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            if (state.isRetryableError) {
                                R.string.pandawave_search_network_error
                            } else {
                                R.string.pandawave_search_error
                            }
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (state.isRetryableError) {
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag("search-retry")
                        ) {
                            Text(stringResource(R.string.pandawave_search_retry))
                        }
                    }
                }
            }
        }

        if (state.isLoading && state.results.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("search-loading"),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        if (!state.isLoading &&
            state.results.isEmpty() &&
            state.query.trim().length >= SearchViewModel.MIN_QUERY_LENGTH &&
            state.errorType == null
        ) {
            Text(
                text = stringResource(R.string.pandawave_search_empty),
                modifier = Modifier.testTag("search-empty")
            )
        }

        if (state.results.isNotEmpty()) {
            BambooSectionHeader(title = stringResource(R.string.pandawave_search_results))
            state.results.forEach { track ->
                BambooMediaListRow(
                    modifier = Modifier.testTag("search-result-${track.mediaId}"),
                    item = track.toMediaItem(),
                    icon = PandaWaveIcons.MusicLibrary,
                    accentColor = Color(tokens.colors.secondary),
                    onClick = {
                        onPlay(track.mediaId, track.title)
                        onOpenNowPlaying()
                    }
                )
            }
        }

        if (state.hasNextPage) {
            Button(
                onClick = onLoadNext,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().testTag("search-next-page")
            ) {
                Text(stringResource(R.string.pandawave_search_load_more))
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

private fun SearchTrack.toMediaItem() = BambooMediaItem(
    id = mediaId,
    title = title,
    subtitle = artist.ifBlank { "" },
    description = album.orEmpty(),
    action = BambooMediaAction.Play
)
