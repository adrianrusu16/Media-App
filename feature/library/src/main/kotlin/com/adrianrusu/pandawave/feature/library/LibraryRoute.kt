package com.adrianrusu.pandawave.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.cardResting
import com.adrianrusu.pandawave.core.designsystem.tokens.md
import com.adrianrusu.pandawave.core.designsystem.tokens.sm
import com.adrianrusu.pandawave.core.ui.discovery.BambooSectionHeader
import com.adrianrusu.pandawave.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.pandawave.feature.library.domain.LibraryState
import com.adrianrusu.pandawave.feature.library.domain.LibraryTab
import com.adrianrusu.pandawave.feature.library.domain.LibraryTrack
import com.adrianrusu.pandawave.feature.library.presentation.LibraryViewModel

@Composable
fun LibraryRoute(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryRoute(
        state = state,
        modifier = modifier,
        onSelectTab = viewModel::selectTab,
        onRefresh = viewModel::refresh,
        onLoadNext = viewModel::loadNext,
        onSave = viewModel::save,
        onRemoveSaved = viewModel::removeSaved,
        onLike = viewModel::like,
        onUnlike = viewModel::unlike,
    )
}

@Composable
fun LibraryRoute(
    state: LibraryState,
    onSelectTab: (LibraryTab) -> Unit,
    onRefresh: () -> Unit,
    onLoadNext: () -> Unit,
    onSave: (String) -> Unit,
    onRemoveSaved: (String) -> Unit,
    onLike: (String) -> Unit,
    onUnlike: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalPandaWaveDesignTokens.current
    BambooRotaryColumn(
        modifier = modifier
            .fillMaxWidth()
            .testTag("library-route"),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md),
    ) {
        BambooSectionHeader(
            title = stringResource(R.string.pandawave_library_title),
            subtitle = stringResource(R.string.pandawave_library_subtitle),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            LibraryTabButton(
                modifier = Modifier.weight(1f).testTag("library-tab-saved"),
                selected = state.selectedTab == LibraryTab.SAVED,
                text = stringResource(R.string.pandawave_library_saved),
                onClick = { onSelectTab(LibraryTab.SAVED) },
            )
            LibraryTabButton(
                modifier = Modifier.weight(1f).testTag("library-tab-liked"),
                selected = state.selectedTab == LibraryTab.LIKED,
                text = stringResource(R.string.pandawave_library_liked),
                onClick = { onSelectTab(LibraryTab.LIKED) },
            )
        }

        if (state.isSignedOut) {
            Text(
                text = stringResource(R.string.pandawave_library_signed_out),
                modifier = Modifier.testTag("library-signed-out"),
            )
            return@BambooRotaryColumn
        }

        if (state.isLoading && state.selectedTracks.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("library-loading"),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        state.errorType?.let {
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("library-error"),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Row(
                    modifier = Modifier.padding(tokens.spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (state.isRetryableError) R.string.pandawave_library_network_error
                            else R.string.pandawave_library_error
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    if (state.isRetryableError) {
                        OutlinedButton(onClick = onRefresh, modifier = Modifier.testTag("library-retry")) {
                            Text(stringResource(R.string.pandawave_library_retry))
                        }
                    }
                }
            }
        }

        if (!state.isLoading && state.selectedTracks.isEmpty() && state.errorType == null) {
            Text(
                text = stringResource(
                    if (state.selectedTab == LibraryTab.SAVED) R.string.pandawave_library_empty_saved
                    else R.string.pandawave_library_empty_liked
                ),
                modifier = Modifier.testTag("library-empty"),
            )
        }

        val savedIds = state.savedTracks.mapTo(mutableSetOf(), LibraryTrack::mediaId)
        val likedIds = state.likedTracks.mapTo(mutableSetOf(), LibraryTrack::mediaId)
        state.selectedTracks.forEach { track ->
            LibraryTrackRow(
                track = track,
                tab = state.selectedTab,
                pending = track.mediaId in state.pendingMediaIds,
                saved = track.mediaId in savedIds,
                liked = track.mediaId in likedIds,
                onSave = onSave,
                onRemoveSaved = onRemoveSaved,
                onLike = onLike,
                onUnlike = onUnlike,
            )
        }

        if (state.hasSelectedNextPage) {
            Button(
                onClick = onLoadNext,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().testTag("library-next-page"),
            ) {
                Text(stringResource(R.string.pandawave_library_load_more))
            }
        }
    }
}

@Composable
private fun LibraryTabButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

@Composable
private fun LibraryTrackRow(
    track: LibraryTrack,
    tab: LibraryTab,
    pending: Boolean,
    saved: Boolean,
    liked: Boolean,
    onSave: (String) -> Unit,
    onRemoveSaved: (String) -> Unit,
    onLike: (String) -> Unit,
    onUnlike: (String) -> Unit,
) {
    val tokens = LocalPandaWaveDesignTokens.current
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("library-track-${track.mediaId}"),
        tonalElevation = tokens.elevation.cardResting,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        ) {
            Text(track.title, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(track.artist, track.album).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (pending) {
                Text(
                    stringResource(R.string.pandawave_library_pending),
                    modifier = Modifier.testTag("library-pending-${track.mediaId}"),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            ) {
                if (tab == LibraryTab.SAVED) {
                    OutlinedButton(
                        onClick = { onRemoveSaved(track.mediaId) },
                        enabled = !pending,
                        modifier = Modifier.weight(1f).testTag("library-remove-${track.mediaId}"),
                    ) { Text(stringResource(R.string.pandawave_library_remove_saved)) }
                    LibraryLikeButton(track.mediaId, liked, pending, onLike, onUnlike, Modifier.weight(1f))
                } else {
                    OutlinedButton(
                        onClick = { onUnlike(track.mediaId) },
                        enabled = !pending,
                        modifier = Modifier.weight(1f).testTag("library-unlike-${track.mediaId}"),
                    ) { Text(stringResource(R.string.pandawave_library_unlike)) }
                    LibrarySaveButton(track.mediaId, saved, pending, onSave, onRemoveSaved, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LibraryLikeButton(
    mediaId: String,
    liked: Boolean,
    pending: Boolean,
    onLike: (String) -> Unit,
    onUnlike: (String) -> Unit,
    modifier: Modifier,
) {
    Button(
        onClick = { if (liked) onUnlike(mediaId) else onLike(mediaId) },
        enabled = !pending,
        modifier = modifier.testTag("library-${if (liked) "unlike" else "like"}-$mediaId"),
    ) { Text(stringResource(if (liked) R.string.pandawave_library_unlike else R.string.pandawave_library_like)) }
}

@Composable
private fun LibrarySaveButton(
    mediaId: String,
    saved: Boolean,
    pending: Boolean,
    onSave: (String) -> Unit,
    onRemoveSaved: (String) -> Unit,
    modifier: Modifier,
) {
    Button(
        onClick = { if (saved) onRemoveSaved(mediaId) else onSave(mediaId) },
        enabled = !pending,
        modifier = modifier.testTag("library-${if (saved) "remove" else "save"}-$mediaId"),
    ) { Text(stringResource(if (saved) R.string.pandawave_library_remove_saved else R.string.pandawave_library_save)) }
}
