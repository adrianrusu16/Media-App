package com.adrianrusu.mediaapp.core.ui.miniplayer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.adrianrusu.mediaapp.core.designsystem.R
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.cardResting
import com.adrianrusu.mediaapp.core.designsystem.tokens.iconLarge
import com.adrianrusu.mediaapp.core.designsystem.tokens.lg
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.designsystem.tokens.miniPlayerArtworkSize
import com.adrianrusu.mediaapp.core.designsystem.tokens.miniPlayerHeight
import com.adrianrusu.mediaapp.core.designsystem.tokens.miniPlayerInternalSpacing
import com.adrianrusu.mediaapp.core.designsystem.tokens.miniPlayerTransportButtonSize
import com.adrianrusu.mediaapp.core.designsystem.tokens.progressTrackHeight
import com.adrianrusu.mediaapp.core.designsystem.tokens.sm
import com.adrianrusu.mediaapp.core.designsystem.tokens.touchTargetLg
import com.adrianrusu.mediaapp.core.designsystem.tokens.xs
import com.adrianrusu.mediaapp.core.ui.focus.bambooBringIntoViewOnFocus
import com.adrianrusu.mediaapp.core.ui.focus.bambooFocusIndicator
import com.adrianrusu.mediaapp.core.ui.icons.PandaWaveIcons
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@Composable
fun BambooMiniPlayer(
    modifier: Modifier = Modifier,
    state: MiniPlayerState,
    onClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    controlsEnabled: Boolean = false
) {
    val tokens = LocalPandaWaveDesignTokens.current
    var nowMillis by remember(state.progressAnchor) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    val progress = state.progressAt(nowMillis)

    LaunchedEffect(state.progressAnchor) {
        nowMillis = System.currentTimeMillis()

        while (state.progressAnchor.isPlaying) {
            delay(MINI_PLAYER_PROGRESS_TICK_MILLIS.milliseconds)
            nowMillis = System.currentTimeMillis()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = tokens.shape.miniPlayerHeight)
            .bambooFocusIndicator()
            .bambooBringIntoViewOnFocus()
            .clickable(
                role = Role.Button,
                onClickLabel = BambooPlaybackText.ACTION_OPEN_NOW_PLAYING,
                onClick = onClick
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = tokens.elevation.cardResting
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = tokens.spacing.lg,
                vertical = tokens.components.miniPlayerInternalSpacing
            ),
            verticalArrangement = Arrangement.spacedBy(tokens.components.miniPlayerInternalSpacing)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniPlayerArtwork()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = tokens.shape.miniPlayerHeight),
                    verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
                ) {
                    Text(
                        text = state.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
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
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        enabled = controlsEnabled,
                        modifier = Modifier
                            .size(tokens.components.miniPlayerTransportButtonSize)
                            .bambooBringIntoViewOnFocus(),
                        onClick = onSkipPreviousClick
                    ) {
                        Icon(
                            imageVector = PandaWaveIcons.SkipPrevious,
                            contentDescription = BambooPlaybackText.ACTION_SKIP_PREVIOUS
                        )
                    }
                    FilledIconButton(
                        enabled = controlsEnabled,
                        modifier = Modifier
                            .size(tokens.components.miniPlayerTransportButtonSize)
                            .bambooBringIntoViewOnFocus(),
                        onClick = onPlayPauseClick
                    ) {
                        if (state.isPlaying) {
                            Icon(
                                imageVector = PandaWaveIcons.Pause,
                                contentDescription = BambooPlaybackText.ACTION_PAUSE
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_panda_paw),
                                contentDescription = BambooPlaybackText.ACTION_PLAY
                            )
                        }
                    }
                    IconButton(
                        enabled = controlsEnabled,
                        modifier = Modifier
                            .size(tokens.components.miniPlayerTransportButtonSize)
                            .bambooBringIntoViewOnFocus(),
                        onClick = onSkipNextClick
                    ) {
                        Icon(
                            imageVector = PandaWaveIcons.SkipNext,
                            contentDescription = BambooPlaybackText.ACTION_SKIP_NEXT
                        )
                    }
                }
            }

            MiniPlayerProgressBar(progress = progress)
        }
    }
}

@Composable
private fun MiniPlayerArtwork() {
    val tokens = LocalPandaWaveDesignTokens.current
    Surface(
        modifier = Modifier.size(tokens.components.miniPlayerArtworkSize),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_pandawave_logo),
                contentDescription = null,
                modifier = Modifier.size(tokens.components.iconLarge)
            )
        }
    }
}

@Composable
private fun MiniPlayerProgressBar(progress: MiniPlayerProgress) {
    val tokens = LocalPandaWaveDesignTokens.current

    Row(
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = progress.positionMillis.toTimestampLabel(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(tokens.components.progressTrackHeight)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.fraction)
                    .height(tokens.components.progressTrackHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            text = progress.durationMillis?.toTimestampLabel() ?: UNKNOWN_TIME_LABEL,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private fun Long.toTimestampLabel(): String {
    val totalSeconds = (coerceAtLeast(0L) / MILLIS_PER_SECOND).toInt()
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE

    return "$minutes:${seconds.toString().padStart(TIMESTAMP_SECOND_DIGITS, '0')}"
}

private const val MINI_PLAYER_PROGRESS_TICK_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60
private const val TIMESTAMP_SECOND_DIGITS = 2
private const val UNKNOWN_TIME_LABEL = "--:--"
