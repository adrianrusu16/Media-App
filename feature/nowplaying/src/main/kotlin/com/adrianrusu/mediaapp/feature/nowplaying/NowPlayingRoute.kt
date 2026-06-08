package com.adrianrusu.mediaapp.feature.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.lg
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.designsystem.tokens.sm
import com.adrianrusu.mediaapp.core.designsystem.tokens.touchTargetLg
import com.adrianrusu.mediaapp.core.designsystem.tokens.touchTargetMd
import com.adrianrusu.mediaapp.core.designsystem.tokens.xs
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingIntent
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingState
import com.adrianrusu.mediaapp.feature.nowplaying.presentation.NowPlayingViewModel

@Composable
fun NowPlayingRoute(modifier: Modifier = Modifier) {
    val viewModel: NowPlayingViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    NowPlayingScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier
    )
}

@Composable
private fun NowPlayingScreen(
    state: NowPlayingState,
    onIntent: (NowPlayingIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
    ) {
        NowPlayingStatusCard(state = state)
        PlaybackControls(
            state = state,
            onIntent = onIntent
        )
        EngineStateCard(state = state)
    }
}

@Composable
private fun NowPlayingStatusCard(state: NowPlayingState) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = tokens.spacing.xs,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
        ) {
            Text(
                text = "Now playing",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = state.detailLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlaybackControls(state: NowPlayingState, onIntent: (NowPlayingIntent) -> Unit) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = tokens.spacing.xs,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
            ) {
                Text(
                    text = state.playbackState.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.primaryActionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    modifier = Modifier.size(tokens.sizing.touchTargetMd),
                    enabled = state.canDispatchEngineCommands,
                    onClick = {
                        onIntent(NowPlayingIntent.SkipPrevious)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = BambooPlaybackText.ACTION_SKIP_PREVIOUS
                    )
                }
                FilledIconButton(
                    modifier = Modifier.size(tokens.sizing.touchTargetLg),
                    enabled = state.canDispatchEngineCommands,
                    onClick = {
                        onIntent(NowPlayingIntent.TogglePlayback)
                    }
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = state.primaryActionLabel
                    )
                }
                IconButton(
                    modifier = Modifier.size(tokens.sizing.touchTargetMd),
                    enabled = state.canDispatchEngineCommands,
                    onClick = {
                        onIntent(NowPlayingIntent.SkipNext)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = BambooPlaybackText.ACTION_SKIP_NEXT
                    )
                }
                Box(modifier = Modifier.size(tokens.spacing.xs))
                IconButton(
                    modifier = Modifier.size(tokens.sizing.touchTargetMd),
                    enabled = state.canDispatchEngineCommands,
                    onClick = {
                        onIntent(NowPlayingIntent.Refresh)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh"
                    )
                }
            }
        }
    }
}

@Composable
private fun EngineStateCard(state: NowPlayingState) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        color = if (state.restriction.isRestricted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = tokens.spacing.xs,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
        ) {
            Text(
                text = "Engine state",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = state.playbackState.name,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = state.engineConnection.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = state.restriction.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
