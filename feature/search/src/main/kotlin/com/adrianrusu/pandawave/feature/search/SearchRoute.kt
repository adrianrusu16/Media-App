package com.adrianrusu.pandawave.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.adrianrusu.pandawave.core.designsystem.tokens.sm
import com.adrianrusu.pandawave.core.ui.artwork.BambooArtworkFallback
import com.adrianrusu.pandawave.core.ui.artwork.toBambooArtworkModel
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaAction
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaItem
import com.adrianrusu.pandawave.core.ui.discovery.BambooMediaListRow
import com.adrianrusu.pandawave.core.ui.discovery.BambooSearchBar
import com.adrianrusu.pandawave.core.ui.discovery.BambooSectionHeader
import com.adrianrusu.pandawave.core.ui.focus.BambooRotaryColumn
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

    BambooRotaryColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("search-route"),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
    ) {
        BambooSearchBar(
            modifier = Modifier.testTag("search-input"),
            query = state.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.pandawave_search_placeholder),
            textStyle = tokens.typography.body,
            onVoiceClick = {}
        )

        state.errorType?.let {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("search-error"),
                color = Color(tokens.colors.error),
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(tokens.spacing.sm),
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
                        style = tokens.typography.body,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.isRetryableError) {
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag("search-retry")
                        ) {
                            Text(
                                text = stringResource(R.string.pandawave_search_retry),
                                style = tokens.typography.body
                            )
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
                style = tokens.typography.metadata,
                modifier = Modifier.testTag("search-empty")
            )
        }

        if (state.results.isNotEmpty()) {
            BambooSectionHeader(
                title = stringResource(R.string.pandawave_search_results),
                titleStyle = tokens.typography.body
            )
            state.results.forEach { track ->
                BambooMediaListRow(
                    modifier = Modifier.testTag("search-result-${track.mediaId}"),
                    item = track.toMediaItem(),
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
                Text(
                    text = stringResource(R.string.pandawave_search_load_more),
                    style = tokens.typography.body
                )
            }
        }
    }
}

private fun SearchTrack.toMediaItem() = BambooMediaItem(
    id = mediaId,
    title = title,
    subtitle = artist.ifBlank { "" },
    description = album.orEmpty(),
    action = BambooMediaAction.Play,
    artwork = toBambooArtworkModel(
        id = artworkId,
        version = artworkVersion,
        uri = artworkUri
    ),
    artworkFallback = BambooArtworkFallback.Track
)
