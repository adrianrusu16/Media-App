package com.adrianrusu.mediaapp.core.ui.miniplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.lg
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.designsystem.tokens.miniPlayerHeight
import com.adrianrusu.mediaapp.core.designsystem.tokens.xs
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText
import kotlinx.coroutines.delay

@Composable
fun BambooMiniPlayer(
    state: MiniPlayerState,
    onSkipPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    controlsEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current
    var nowMillis by remember(state.progressAnchor) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    val progress = state.progressAt(nowMillis)

    LaunchedEffect(state.progressAnchor) {
        nowMillis = System.currentTimeMillis()

        while (state.progressAnchor.isPlaying) {
            delay(MINI_PLAYER_PROGRESS_TICK_MILLIS)
            nowMillis = System.currentTimeMillis()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = tokens.shape.miniPlayerHeight),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = tokens.spacing.xs
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = tokens.spacing.lg,
                vertical = tokens.spacing.md
            ),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
            ) {
                Text(
                    text = state.title,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.isRestricted) {
                    Text(
                        text = BambooPlaybackText.DRIVER_SAFE_MODE,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)) {
                IconButton(
                    enabled = controlsEnabled,
                    onClick = onSkipPreviousClick
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = BambooPlaybackText.ACTION_SKIP_PREVIOUS
                    )
                }
                IconButton(
                    enabled = controlsEnabled,
                    onClick = onPlayPauseClick
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) {
                            BambooPlaybackText.ACTION_PAUSE
                        } else {
                            BambooPlaybackText.ACTION_PLAY
                        }
                    )
                }
                IconButton(
                    enabled = controlsEnabled,
                    onClick = onSkipNextClick
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = BambooPlaybackText.ACTION_SKIP_NEXT
                    )
                }
            }
        }
    }
}

private const val MINI_PLAYER_PROGRESS_TICK_MILLIS = 1_000L
