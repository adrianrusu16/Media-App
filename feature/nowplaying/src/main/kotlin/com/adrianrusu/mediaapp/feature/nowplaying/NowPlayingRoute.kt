package com.adrianrusu.mediaapp.feature.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    onIntent(NowPlayingIntent.TogglePlayback)
                }
            ) {
                Text(text = state.primaryActionLabel)
            }
            OutlinedButton(
                onClick = {
                    onIntent(NowPlayingIntent.Refresh)
                }
            ) {
                Text(text = "Refresh")
            }
        }
    }
}

@Composable
private fun EngineStateCard(state: NowPlayingState) {
    Surface(
        color = if (state.restriction.isRestricted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                text = state.restriction.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
