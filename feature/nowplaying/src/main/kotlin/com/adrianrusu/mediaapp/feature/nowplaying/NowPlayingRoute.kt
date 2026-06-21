package com.adrianrusu.mediaapp.feature.nowplaying

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.mediaapp.core.designsystem.R as DesignSystemR
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.cardResting
import com.adrianrusu.mediaapp.core.designsystem.tokens.iconMedium
import com.adrianrusu.mediaapp.core.designsystem.tokens.iconSmall
import com.adrianrusu.mediaapp.core.designsystem.tokens.lg
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.designsystem.tokens.nowPlayingArtworkCompact
import com.adrianrusu.mediaapp.core.designsystem.tokens.nowPlayingArtworkStandard
import com.adrianrusu.mediaapp.core.designsystem.tokens.nowPlayingCompactHeightThreshold
import com.adrianrusu.mediaapp.core.designsystem.tokens.nowPlayingFooterHeight
import com.adrianrusu.mediaapp.core.designsystem.tokens.nowPlayingQuickActionHeight
import com.adrianrusu.mediaapp.core.designsystem.tokens.nowPlayingQuickActionWidth
import com.adrianrusu.mediaapp.core.designsystem.tokens.nowPlayingScrollHeightThreshold
import com.adrianrusu.mediaapp.core.designsystem.tokens.nowPlayingSecondaryTransportSize
import com.adrianrusu.mediaapp.core.designsystem.tokens.nowPlayingTransportSpacing
import com.adrianrusu.mediaapp.core.designsystem.tokens.progressThumbSize
import com.adrianrusu.mediaapp.core.designsystem.tokens.progressTrackHeight
import com.adrianrusu.mediaapp.core.designsystem.tokens.sm
import com.adrianrusu.mediaapp.core.designsystem.tokens.touchTargetLg
import com.adrianrusu.mediaapp.core.designsystem.tokens.touchTargetMd
import com.adrianrusu.mediaapp.core.designsystem.tokens.volumeControlHeight
import com.adrianrusu.mediaapp.core.designsystem.tokens.volumeControlMaxWidth
import com.adrianrusu.mediaapp.core.designsystem.tokens.xs
import com.adrianrusu.mediaapp.core.playback.BambooPlaybackProgress
import com.adrianrusu.mediaapp.core.ui.components.BambooVoiceIndicator
import com.adrianrusu.mediaapp.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.mediaapp.core.ui.focus.bambooBringIntoViewOnFocus
import com.adrianrusu.mediaapp.core.ui.icons.PandaWaveIcons
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlayPauseButton
import com.adrianrusu.mediaapp.core.ui.playback.BambooPlaybackControlSize
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingIntent
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingPlaybackState
import com.adrianrusu.mediaapp.feature.nowplaying.domain.NowPlayingState
import com.adrianrusu.mediaapp.feature.nowplaying.presentation.NowPlayingViewModel
import kotlin.time.Duration.Companion.milliseconds
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
    val fallbackTitle = when (state.playbackState) {
        NowPlayingPlaybackState.Playing -> stringResource(R.string.pandawave_now_playing_playing_title)
        NowPlayingPlaybackState.Paused -> stringResource(R.string.pandawave_now_playing_paused_title)
        NowPlayingPlaybackState.Idle -> stringResource(R.string.pandawave_now_playing_idle_title)
    }
    val fallbackDetail = when (state.playbackState) {
        NowPlayingPlaybackState.Playing -> stringResource(R.string.pandawave_now_playing_playing_subtitle)
        NowPlayingPlaybackState.Paused -> stringResource(R.string.pandawave_now_playing_paused_subtitle)
        NowPlayingPlaybackState.Idle -> stringResource(R.string.pandawave_now_playing_idle_subtitle)
    }
    val uiModel = state.toNowPlayingUiModel(
        volume = volume,
        playLabel = stringResource(R.string.pandawave_now_playing_action_play),
        pauseLabel = stringResource(R.string.pandawave_now_playing_action_pause),
        controlsUnavailableLabel = stringResource(R.string.pandawave_now_playing_controls_unavailable),
        fallbackTitle = fallbackTitle,
        fallbackDetail = fallbackDetail
    )

    LaunchedEffect(state.progressAnchor) {
        nowMillis = System.currentTimeMillis()

        while (state.isPlaying) {
            delay(NOW_PLAYING_PROGRESS_TICK_MILLIS.milliseconds)
            nowMillis = System.currentTimeMillis()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val layoutMode = resolveNowPlayingLayout(
            availableHeight = maxHeight,
            compactHeightThreshold = tokens.layout.nowPlayingCompactHeightThreshold,
            scrollHeightThreshold = tokens.layout.nowPlayingScrollHeightThreshold
        )
        val isCompact = layoutMode != NowPlayingLayoutMode.Standard
        BambooRotaryColumn(
            modifier = (
                if (layoutMode == NowPlayingLayoutMode.ScrollableCompact) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.fillMaxSize()
                }
                ).testTag("now-playing-route"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                if (isCompact) tokens.spacing.sm else tokens.spacing.lg
            ),
            scrollEnabled = layoutMode == NowPlayingLayoutMode.ScrollableCompact
        ) {
            NowPlayingArtworkPanel(
                title = uiModel.title,
                detailLabel = uiModel.detailLabel,
                availabilityLabel = uiModel.availabilityLabel,
                showAvailability = !uiModel.controlsEnabled,
                artworkHeight = if (isCompact) {
                    tokens.layout.nowPlayingArtworkCompact
                } else {
                    tokens.layout.nowPlayingArtworkStandard
                },
                isCompact = isCompact
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
}

@Composable
private fun NowPlayingArtworkPanel(
    title: String,
    detailLabel: String,
    availabilityLabel: String,
    showAvailability: Boolean,
    artworkHeight: androidx.compose.ui.unit.Dp,
    isCompact: Boolean
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val panelPadding = if (isCompact) tokens.spacing.sm else tokens.spacing.lg
    val artworkSize = artworkHeight - (panelPadding * 2)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(artworkHeight),
        color = Color(tokens.colors.surfaceVariant),
        tonalElevation = tokens.elevation.cardResting,
        shape = MaterialTheme.shapes.medium
    ) {
        Box(
            modifier = Modifier.padding(panelPadding),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(artworkSize),
                color = Color(tokens.colors.surface),
                tonalElevation = tokens.elevation.cardResting,
                shape = MaterialTheme.shapes.medium
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = DesignSystemR.drawable.pandawave_ic_logo),
                        contentDescription = null,
                        modifier = Modifier.size(
                            tokens.sizing.touchTargetLg * if (isCompact) {
                                COMPACT_LOGO_TOUCH_TARGET_MULTIPLIER
                            } else {
                                STANDARD_LOGO_TOUCH_TARGET_MULTIPLIER
                            }
                        )
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(),
                        color = Color(tokens.colors.surface).copy(alpha = ARTWORK_TEXT_PANEL_ALPHA)
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                if (isCompact) tokens.spacing.sm else tokens.spacing.lg
                            ),
                            verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
                        ) {
                            Text(
                                text = title,
                                color = Color(tokens.colors.onSurface),
                                style = if (isCompact) {
                                    tokens.typography.sectionTitle
                                } else {
                                    tokens.typography.display
                                },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = detailLabel,
                                color = Color(tokens.colors.primary),
                                style = if (isCompact) {
                                    tokens.typography.sectionTitle
                                } else {
                                    tokens.typography.sectionTitle
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (showAvailability) {
                                Text(
                                    text = availabilityLabel,
                                    color = Color(tokens.colors.onSurfaceVariant),
                                    style = tokens.typography.controlLabel
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
            color = Color(tokens.colors.onSurfaceVariant),
            style = tokens.typography.controlLabel
        )
        LeafProgressTrack(
            progress = progress.fraction,
            modifier = Modifier.weight(1f)
        )
        Text(
            modifier = Modifier.width(tokens.sizing.touchTargetMd),
            text = progress.durationMillis?.toPlaybackTimeLabel()
                ?: stringResource(R.string.pandawave_now_playing_unknown_time),
            color = Color(tokens.colors.onSurfaceVariant),
            style = tokens.typography.controlLabel
        )
    }
}

@Composable
private fun LeafProgressTrack(progress: Float, modifier: Modifier = Modifier) {
    val tokens = LocalPandaWaveDesignTokens.current
    val clampedProgress = progress.coerceIn(MIN_PROGRESS_FRACTION, MAX_PROGRESS_FRACTION)

    Box(
        modifier = modifier.height(tokens.components.progressThumbSize),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(tokens.components.progressTrackHeight)
                .clip(CircleShape)
                .background(Color(tokens.colors.surfaceVariant))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .height(tokens.components.progressTrackHeight)
                .clip(CircleShape)
                .background(Color(tokens.colors.primary).copy(alpha = PROGRESS_ACTIVE_ALPHA))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .height(tokens.components.progressThumbSize),
            contentAlignment = Alignment.CenterEnd
        ) {
            Surface(
                modifier = Modifier.size(tokens.components.progressThumbSize),
                color = Color(tokens.colors.primary),
                shape = CircleShape,
                shadowElevation = tokens.elevation.cardResting
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = PandaWaveIcons.Nature,
                        contentDescription = null,
                        tint = Color(tokens.colors.onPrimary),
                        modifier = Modifier.size(tokens.components.iconSmall)
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
            icon = PandaWaveIcons.Shuffle,
            contentDescription = stringResource(R.string.pandawave_now_playing_shuffle_unavailable),
            enabled = false,
            onClick = {}
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(tokens.components.nowPlayingTransportSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TransportRoundAction(
                icon = PandaWaveIcons.SkipPrevious,
                contentDescription = stringResource(R.string.pandawave_now_playing_action_skip_previous),
                enabled = uiModel.controlsEnabled,
                onClick = onSkipPreviousClick
            )
            PrimaryPlaybackButton(
                uiModel = uiModel,
                onClick = onPlayPauseClick
            )
            TransportRoundAction(
                icon = PandaWaveIcons.SkipNext,
                contentDescription = stringResource(R.string.pandawave_now_playing_action_skip_next),
                enabled = uiModel.controlsEnabled,
                onClick = onSkipNextClick
            )
        }
        SecondaryRoundAction(
            icon = PandaWaveIcons.Favorite,
            contentDescription = stringResource(R.string.pandawave_now_playing_favorite_unavailable),
            enabled = false,
            onClick = {}
        )
    }
}

@Composable
private fun PrimaryPlaybackButton(uiModel: NowPlayingUiModel, onClick: () -> Unit) {
    BambooPlayPauseButton(
        playing = uiModel.primaryControlIcon == NowPlayingPrimaryControlIcon.Pause,
        enabled = uiModel.controlsEnabled,
        size = BambooPlaybackControlSize.NowPlaying,
        playContentDescription = uiModel.primaryActionLabel,
        pauseContentDescription = uiModel.primaryActionLabel,
        onClick = onClick,
        modifier = Modifier.testTag("now-playing-play-pause")
    )
}

@Composable
private fun TransportRoundAction(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        modifier = Modifier.size(tokens.components.nowPlayingSecondaryTransportSize),
        color = Color(tokens.colors.surfaceVariant),
        shape = CircleShape,
        tonalElevation = tokens.elevation.cardResting
    ) {
        IconButton(
            modifier = Modifier
                .fillMaxSize()
                .bambooBringIntoViewOnFocus(),
            enabled = enabled,
            onClick = onClick
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(tokens.components.iconMedium)
            )
        }
    }
}

@Composable
private fun SecondaryRoundAction(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        modifier = Modifier.size(tokens.components.nowPlayingSecondaryTransportSize),
        color = Color(tokens.colors.surface),
        shape = CircleShape,
        tonalElevation = tokens.elevation.cardResting
    ) {
        IconButton(
            modifier = Modifier
                .fillMaxSize()
                .bambooBringIntoViewOnFocus(),
            enabled = enabled,
            onClick = onClick
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(tokens.components.iconMedium)
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
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = tokens.components.nowPlayingFooterHeight),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickActionButton(
            icon = PandaWaveIcons.Queue,
            label = stringResource(R.string.pandawave_now_playing_library),
            enabled = true,
            onClick = onLibraryClick
        )
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            VolumeControl(
                modifier = Modifier
                    .widthIn(max = tokens.components.volumeControlMaxWidth)
                    .fillMaxWidth()
                    .heightIn(min = tokens.components.volumeControlHeight),
                volume = volume,
                onVolumeChange = onVolumeChange
            )
        }
        BambooVoiceIndicator()
    }
}

@Composable
private fun QuickActionButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        color = Color(tokens.colors.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = tokens.elevation.cardResting
    ) {
        IconButton(
            modifier = Modifier
                .size(
                    width = tokens.components.nowPlayingQuickActionWidth,
                    height = tokens.components.nowPlayingQuickActionHeight
                )
                .bambooBringIntoViewOnFocus(),
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
                    modifier = Modifier.size(tokens.components.iconMedium)
                )
                Text(
                    text = label,
                    style = tokens.typography.metadata,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun VolumeControl(
    modifier: Modifier = Modifier,
    volume: NowPlayingVolumeUiModel,
    onVolumeChange: (Float) -> Unit
) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        modifier = modifier,
        color = Color(tokens.colors.surfaceVariant),
        shape = CircleShape,
        tonalElevation = tokens.elevation.cardResting
    ) {
        Row(
            modifier = Modifier.padding(horizontal = tokens.spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = PandaWaveIcons.VolumeDown,
                contentDescription = null,
                tint = Color(tokens.colors.onSurfaceVariant)
            )
            Slider(
                modifier = Modifier
                    .weight(1f)
                    .testTag("now-playing-volume")
                    .bambooBringIntoViewOnFocus(),
                value = volume.value,
                valueRange = MIN_VOLUME_VALUE..MAX_VOLUME_VALUE,
                onValueChange = onVolumeChange
            )
            Icon(
                imageVector = PandaWaveIcons.VolumeUp,
                contentDescription = stringResource(
                    R.string.pandawave_now_playing_volume_percent,
                    volume.value.toInt()
                ),
                tint = Color(tokens.colors.onSurfaceVariant)
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
private const val MIN_PROGRESS_FRACTION = 0F
private const val MAX_PROGRESS_FRACTION = 1F
private const val COMPACT_LOGO_TOUCH_TARGET_MULTIPLIER = 2
private const val STANDARD_LOGO_TOUCH_TARGET_MULTIPLIER = 3
private const val ARTWORK_TEXT_PANEL_ALPHA = 0.84F
private const val PROGRESS_ACTIVE_ALPHA = 0.58F
