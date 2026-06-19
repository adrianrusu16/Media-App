package com.adrianrusu.mediaapp.feature.nowplaying

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.mediaapp.core.designsystem.R
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.cardResting
import com.adrianrusu.mediaapp.core.designsystem.tokens.lg
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.designsystem.tokens.sm
import com.adrianrusu.mediaapp.core.designsystem.tokens.touchTargetLg
import com.adrianrusu.mediaapp.core.designsystem.tokens.touchTargetMd
import com.adrianrusu.mediaapp.core.designsystem.tokens.xl
import com.adrianrusu.mediaapp.core.designsystem.tokens.xs
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackProgress
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackText
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingIntent
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingState
import com.adrianrusu.mediaapp.feature.nowplaying.presentation.NowPlayingViewModel
import kotlinx.coroutines.delay

@Composable
fun NowPlayingRoute(modifier: Modifier = Modifier, onLibraryClick: () -> Unit = {}) {
    val viewModel: NowPlayingViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    NowPlayingScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onLibraryClick = onLibraryClick,
        modifier = modifier
    )
}

@Composable
private fun NowPlayingScreen(
    state: NowPlayingState,
    onIntent: (NowPlayingIntent) -> Unit,
    onLibraryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current
    var nowMillis by remember(state.progressAnchor) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    var volume by remember {
        mutableFloatStateOf(DEFAULT_VOLUME_VALUE)
    }
    val progress = state.progressAt(nowMillis)
    val uiModel = state.toNowPlayingUiModel(volume = volume)

    LaunchedEffect(state.progressAnchor) {
        nowMillis = System.currentTimeMillis()

        while (state.isPlaying) {
            delay(NOW_PLAYING_PROGRESS_TICK_MILLIS)
            nowMillis = System.currentTimeMillis()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg)
    ) {
        NowPlayingArtworkPanel(
            title = uiModel.title,
            detailLabel = uiModel.detailLabel,
            availabilityLabel = uiModel.availabilityLabel,
            showAvailability = !uiModel.controlsEnabled
        )
        NowPlayingProgressRow(progress = progress)
        NowPlayingControls(
            uiModel = uiModel,
            onSkipPreviousClick = {
                onIntent(NowPlayingIntent.SkipPrevious)
            },
            onPlayPauseClick = {
                onIntent(NowPlayingIntent.TogglePlayback)
            },
            onSkipNextClick = {
                onIntent(NowPlayingIntent.SkipNext)
            }
        )
        NowPlayingFooter(
            volume = uiModel.volume,
            onVolumeChange = { nextVolume ->
                volume = nextVolume
            },
            onLibraryClick = onLibraryClick
        )
    }
}

@Composable
private fun NowPlayingArtworkPanel(
    title: String,
    detailLabel: String,
    availabilityLabel: String,
    showAvailability: Boolean
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val artworkSize = tokens.sizing.touchTargetLg * ARTWORK_TOUCH_TARGET_MULTIPLIER

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = artworkSize),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = tokens.elevation.cardResting,
        shape = MaterialTheme.shapes.medium
    ) {
        Box(
            modifier = Modifier.padding(tokens.spacing.lg),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(artworkSize),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = tokens.elevation.cardResting,
                shape = MaterialTheme.shapes.medium
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_pandawave_logo),
                        contentDescription = null,
                        modifier = Modifier.size(tokens.sizing.touchTargetLg * LOGO_TOUCH_TARGET_MULTIPLIER)
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = ARTWORK_TEXT_PANEL_ALPHA)
                    ) {
                        Column(
                            modifier = Modifier.padding(tokens.spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
                        ) {
                            Text(
                                text = title,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = detailLabel,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (showAvailability) {
                                Text(
                                    text = availabilityLabel,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingProgressRow(progress: BambooPlaybackProgress) {
    val tokens = LocalPandaWaveDesignTokens.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.width(tokens.sizing.touchTargetMd),
            text = progress.positionMillis.toPlaybackTimeLabel(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
        LeafProgressTrack(
            progress = progress.fraction,
            modifier = Modifier.weight(1f)
        )
        Text(
            modifier = Modifier.width(tokens.sizing.touchTargetMd),
            text = progress.durationMillis?.toPlaybackTimeLabel() ?: UNKNOWN_PLAYBACK_TIME_LABEL,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun LeafProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    val tokens = LocalPandaWaveDesignTokens.current
    val clampedProgress = progress.coerceIn(MIN_PROGRESS_FRACTION, MAX_PROGRESS_FRACTION)

    Box(
        modifier = modifier.height(tokens.spacing.xl),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(tokens.spacing.sm)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .height(tokens.spacing.sm)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = PROGRESS_ACTIVE_ALPHA))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .height(tokens.spacing.xl),
            contentAlignment = Alignment.CenterEnd
        ) {
            Surface(
                modifier = Modifier.size(tokens.sizing.touchTargetMd),
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                shadowElevation = tokens.elevation.cardResting
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Eco,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(tokens.spacing.lg)
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingControls(
    uiModel: NowPlayingUiModel,
    onSkipPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit
) {
    val tokens = LocalPandaWaveDesignTokens.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SecondaryRoundAction(
            icon = Icons.Filled.Shuffle,
            contentDescription = "Shuffle unavailable",
            enabled = false,
            onClick = {}
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportRoundAction(
                icon = Icons.Filled.SkipPrevious,
                contentDescription = BambooPlaybackText.ACTION_SKIP_PREVIOUS,
                enabled = uiModel.controlsEnabled,
                onClick = onSkipPreviousClick
            )
            PrimaryPlaybackButton(
                uiModel = uiModel,
                onClick = onPlayPauseClick
            )
            TransportRoundAction(
                icon = Icons.Filled.SkipNext,
                contentDescription = BambooPlaybackText.ACTION_SKIP_NEXT,
                enabled = uiModel.controlsEnabled,
                onClick = onSkipNextClick
            )
        }
        SecondaryRoundAction(
            icon = Icons.Filled.Favorite,
            contentDescription = "Favorite unavailable",
            enabled = false,
            onClick = {}
        )
    }
}

@Composable
private fun PrimaryPlaybackButton(uiModel: NowPlayingUiModel, onClick: () -> Unit) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        modifier = Modifier.size(tokens.sizing.touchTargetLg * PRIMARY_BUTTON_TOUCH_TARGET_MULTIPLIER),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
        shadowElevation = tokens.elevation.cardResting
    ) {
        IconButton(
            modifier = Modifier.fillMaxSize(),
            enabled = uiModel.controlsEnabled,
            onClick = onClick
        ) {
            if (uiModel.primaryControlIcon == NowPlayingPrimaryControlIcon.PandaPaw) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_panda_paw),
                    contentDescription = uiModel.primaryActionLabel,
                    modifier = Modifier.size(tokens.sizing.touchTargetLg)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = uiModel.primaryActionLabel,
                    modifier = Modifier.size(tokens.sizing.touchTargetLg)
                )
            }
        }
    }
}

@Composable
private fun TransportRoundAction(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        modifier = Modifier.size(tokens.sizing.touchTargetLg),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        tonalElevation = tokens.elevation.cardResting
    ) {
        IconButton(
            modifier = Modifier.fillMaxSize(),
            enabled = enabled,
            onClick = onClick
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(tokens.spacing.xl)
            )
        }
    }
}

@Composable
private fun SecondaryRoundAction(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        modifier = Modifier.size(tokens.sizing.touchTargetLg),
        color = MaterialTheme.colorScheme.surface,
        shape = CircleShape,
        tonalElevation = tokens.elevation.cardResting
    ) {
        IconButton(
            modifier = Modifier.fillMaxSize(),
            enabled = enabled,
            onClick = onClick
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(tokens.spacing.xl)
            )
        }
    }
}

@Composable
private fun NowPlayingFooter(
    volume: NowPlayingVolumeUiModel,
    onVolumeChange: (Float) -> Unit,
    onLibraryClick: () -> Unit
) {
    val tokens = LocalPandaWaveDesignTokens.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickActionButton(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            label = "Library",
            enabled = true,
            onClick = onLibraryClick
        )
        QuickActionButton(
            icon = Icons.Filled.Spa,
            label = "Nature",
            enabled = false,
            onClick = {}
        )
        VolumeControl(
            volume = volume,
            onVolumeChange = onVolumeChange,
            modifier = Modifier.weight(1f)
        )
        QuickActionButton(
            icon = Icons.Filled.GraphicEq,
            label = "Hey Panda",
            enabled = false,
            onClick = {}
        )
    }
}

@Composable
private fun QuickActionButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = tokens.elevation.cardResting
    ) {
        IconButton(
            modifier = Modifier.size(
                width = tokens.sizing.touchTargetLg + tokens.spacing.lg,
                height = tokens.sizing.touchTargetLg
            ),
            enabled = enabled,
            onClick = onClick
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(tokens.spacing.xl)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VolumeControl(
    volume: NowPlayingVolumeUiModel,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        modifier = modifier.heightIn(min = tokens.sizing.touchTargetLg),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        tonalElevation = tokens.elevation.cardResting
    ) {
        Row(
            modifier = Modifier.padding(horizontal = tokens.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                modifier = Modifier.weight(1f),
                value = volume.value,
                valueRange = MIN_VOLUME_VALUE..MAX_VOLUME_VALUE,
                onValueChange = onVolumeChange
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Volume ${volume.value.toInt()} percent",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Long.toPlaybackTimeLabel(): String {
    val totalSeconds = (this / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE

    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private const val DEFAULT_VOLUME_VALUE = 45F
private const val MIN_VOLUME_VALUE = 0F
private const val MAX_VOLUME_VALUE = 100F
private const val NOW_PLAYING_PROGRESS_TICK_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val UNKNOWN_PLAYBACK_TIME_LABEL = "--:--"
private const val MIN_PROGRESS_FRACTION = 0F
private const val MAX_PROGRESS_FRACTION = 1F
private const val ARTWORK_TOUCH_TARGET_MULTIPLIER = 6
private const val LOGO_TOUCH_TARGET_MULTIPLIER = 3
private const val PRIMARY_BUTTON_TOUCH_TARGET_MULTIPLIER = 2
private const val ARTWORK_TEXT_PANEL_ALPHA = 0.84F
private const val PROGRESS_ACTIVE_ALPHA = 0.58F
