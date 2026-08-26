package com.adrianrusu.pandawave.feature.nowplaying

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.designsystem.R as DesignSystemR
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.cardResting
import com.adrianrusu.pandawave.core.designsystem.tokens.iconMedium
import com.adrianrusu.pandawave.core.designsystem.tokens.iconSmall
import com.adrianrusu.pandawave.core.designsystem.tokens.lg
import com.adrianrusu.pandawave.core.designsystem.tokens.md
import com.adrianrusu.pandawave.core.designsystem.tokens.nowPlayingArtworkCompact
import com.adrianrusu.pandawave.core.designsystem.tokens.nowPlayingArtworkStandard
import com.adrianrusu.pandawave.core.designsystem.tokens.nowPlayingCompactHeightThreshold
import com.adrianrusu.pandawave.core.designsystem.tokens.nowPlayingFooterHeight
import com.adrianrusu.pandawave.core.designsystem.tokens.nowPlayingQuickActionHeight
import com.adrianrusu.pandawave.core.designsystem.tokens.nowPlayingQuickActionWidth
import com.adrianrusu.pandawave.core.designsystem.tokens.nowPlayingScrollHeightThreshold
import com.adrianrusu.pandawave.core.designsystem.tokens.nowPlayingSecondaryTransportSize
import com.adrianrusu.pandawave.core.designsystem.tokens.nowPlayingTransportSpacing
import com.adrianrusu.pandawave.core.designsystem.tokens.progressThumbSize
import com.adrianrusu.pandawave.core.designsystem.tokens.progressTrackHeight
import com.adrianrusu.pandawave.core.designsystem.tokens.sm
import com.adrianrusu.pandawave.core.designsystem.tokens.touchTargetLg
import com.adrianrusu.pandawave.core.designsystem.tokens.touchTargetMd
import com.adrianrusu.pandawave.core.designsystem.tokens.volumeControlHeight
import com.adrianrusu.pandawave.core.designsystem.tokens.volumeControlMaxWidth
import com.adrianrusu.pandawave.core.designsystem.tokens.xs
import com.adrianrusu.pandawave.core.playback.BambooPlaybackProgress
import com.adrianrusu.pandawave.core.ui.audio.visualizer.BambooVoiceIndicator
import com.adrianrusu.pandawave.core.ui.focus.BambooRotaryColumn
import com.adrianrusu.pandawave.core.ui.focus.bambooBringIntoViewOnFocus
import com.adrianrusu.pandawave.core.ui.icons.PandaWaveIcons
import com.adrianrusu.pandawave.core.ui.playback.BambooPlayPauseButton
import com.adrianrusu.pandawave.core.ui.playback.BambooPlaybackControlSize
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingIntent
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingPlaybackState
import com.adrianrusu.pandawave.feature.nowplaying.domain.NowPlayingState
import com.adrianrusu.pandawave.feature.nowplaying.domain.ambient.AmbientModeTransition
import com.adrianrusu.pandawave.feature.nowplaying.presentation.NowPlayingViewModel
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@Composable
fun NowPlayingRoute(
    modifier: Modifier = Modifier,
    onAmbientVisibilityChanged: (Boolean) -> Unit = {},
    onLibraryClick: () -> Unit = {}
) {
    val viewModel: NowPlayingViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ambientModeState by viewModel.ambientModeState.collectAsStateWithLifecycle()
    val ambientTransition by viewModel.ambientTransition.collectAsStateWithLifecycle()
    val nowPlayingMode = ambientModeState.toNowPlayingMode()
    val ambientVisible = nowPlayingMode != NowPlayingMode.Interactive
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        PandaLog.i(PandaLog.Tag.NPS) { "now_playing_opened" }
    }

    LaunchedEffect(state.mediaId, state.title, state.isPlaying) {
        PandaLog.d(PandaLog.Tag.NPS) {
            "snapshot_apply trackId=${state.mediaId.orEmpty()} title=${PandaLog.field(
                state.title
            )} playing=${state.isPlaying}"
        }
    }

    LaunchedEffect(ambientVisible) {
        onAmbientVisibilityChanged(ambientVisible)
    }

    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.onLifecycleResumedChanged(true)
                }

                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> viewModel.onLifecycleResumedChanged(false)

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.onRouteVisibilityChanged(true)
        viewModel.onLifecycleResumedChanged(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onLifecycleResumedChanged(false)
            viewModel.onRouteVisibilityChanged(false)
            onAmbientVisibilityChanged(false)
        }
    }

    NowPlayingScreen(
        modifier = modifier,
        state = state,
        nowPlayingMode = nowPlayingMode,
        ambientTransition = ambientTransition,
        onIntent = viewModel::onIntent,
        viewModel = viewModel,
        onLibraryClick = onLibraryClick
    )
}

@Composable
private fun NowPlayingScreen(
    modifier: Modifier = Modifier,
    state: NowPlayingState,
    nowPlayingMode: NowPlayingMode,
    ambientTransition: AmbientModeTransition,
    viewModel: NowPlayingViewModel,
    onIntent: (NowPlayingIntent) -> Unit,
    onLibraryClick: () -> Unit
) {
    val isAmbientVisible = nowPlayingMode != NowPlayingMode.Interactive
    val tokens = LocalPandaWaveDesignTokens.current
    val playPauseFocusRequester = remember { FocusRequester() }
    val transitionMillis = when {
        ambientTransition == AmbientModeTransition.Immediate -> 0
        isAmbientVisible -> tokens.motion.ambientEntryMillis
        else -> tokens.motion.ambientExitMillis
    }

    LaunchedEffect(isAmbientVisible) {
        if (!isAmbientVisible) {
            playPauseFocusRequester.requestFocus()
        }
    }

    Crossfade(
        targetState = nowPlayingMode,
        animationSpec = tween(durationMillis = transitionMillis),
        label = AMBIENT_TRANSITION_LABEL
    ) { mode ->
        if (mode != NowPlayingMode.Interactive) {
            NowPlayingAmbientRoute(
                modifier = modifier,
                viewModel = viewModel,
                state = state,
                mode = mode
            )
        } else {
            NowPlayingInteractiveScreen(
                state = state,
                onIntent = onIntent,
                playPauseFocusRequester = playPauseFocusRequester,
                onLibraryClick = onLibraryClick,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun NowPlayingAmbientRoute(
    modifier: Modifier,
    viewModel: NowPlayingViewModel,
    state: NowPlayingState,
    mode: NowPlayingMode
) {
    val amplitudes by viewModel.amplitudes.collectAsStateWithLifecycle()
    val isSleeping = mode == NowPlayingMode.SleepingAmbient
    val title = if (isSleeping || state.title.isBlank()) {
        stringResource(R.string.pandawave_now_playing_idle_title)
    } else {
        state.title
    }
    val artist = if (isSleeping || state.artist.isBlank()) {
        stringResource(R.string.pandawave_now_playing_idle_subtitle)
    } else {
        state.artist
    }

    NowPlayingAmbientScreen(
        modifier = modifier,
        amplitudes = amplitudes,
        artworkUri = state.artworkUri.takeUnless { isSleeping },
        title = title,
        artist = artist,
        onShowPlaybackControls = viewModel::onUserInteraction
    )
}

@Composable
private fun NowPlayingInteractiveScreen(
    modifier: Modifier = Modifier,
    state: NowPlayingState,
    onIntent: (NowPlayingIntent) -> Unit,
    playPauseFocusRequester: FocusRequester,
    onLibraryClick: () -> Unit
) {
    val tokens = LocalPandaWaveDesignTokens.current
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
        volume = state.volume * MAX_VOLUME_VALUE,
        playLabel = stringResource(R.string.pandawave_now_playing_action_play),
        pauseLabel = stringResource(R.string.pandawave_now_playing_action_pause),
        controlsUnavailableLabel = stringResource(R.string.pandawave_now_playing_controls_unavailable),
        playbackErrorLabel = stringResource(R.string.pandawave_now_playing_playback_error),
        fallbackTitle = fallbackTitle,
        fallbackDetail = fallbackDetail
    )

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
            NowPlayingProgressTicker(state = state)
            NowPlayingControls(
                uiModel = uiModel,
                playPauseFocusRequester = playPauseFocusRequester,
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
                    onIntent(NowPlayingIntent.SetVolume(nextVolume / MAX_VOLUME_VALUE))
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
private fun NowPlayingProgressTicker(state: NowPlayingState) {
    var nowMillis by remember(state.progressAnchor) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    val progress = state.progressAt(nowMillis)

    LaunchedEffect(state.progressAnchor) {
        nowMillis = System.currentTimeMillis()

        while (state.isPlaying) {
            delay(NOW_PLAYING_PROGRESS_TICK_MILLIS.milliseconds)
            nowMillis = System.currentTimeMillis()
        }
    }

    NowPlayingProgressRow(progress = progress)
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

    BoxWithConstraints(
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
            modifier = Modifier.offset(
                x = (maxWidth - tokens.components.progressThumbSize) * clampedProgress
            ),
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
    playPauseFocusRequester: FocusRequester,
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
                enabled = uiModel.canSkipPrevious,
                onClick = onSkipPreviousClick
            )
            PrimaryPlaybackButton(
                uiModel = uiModel,
                modifier = Modifier.focusRequester(playPauseFocusRequester),
                onClick = onPlayPauseClick
            )
            TransportRoundAction(
                icon = PandaWaveIcons.SkipNext,
                contentDescription = stringResource(R.string.pandawave_now_playing_action_skip_next),
                enabled = uiModel.canSkipNext,
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
private fun PrimaryPlaybackButton(uiModel: NowPlayingUiModel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    BambooPlayPauseButton(
        modifier = modifier.testTag("now-playing-play-pause"),
        playing = uiModel.primaryControlIcon == NowPlayingPrimaryControlIcon.Pause,
        enabled = uiModel.controlsEnabled,
        size = BambooPlaybackControlSize.NowPlaying,
        playContentDescription = uiModel.primaryActionLabel,
        pauseContentDescription = uiModel.primaryActionLabel,
        onClick = onClick
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
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            QuickActionButton(
                icon = PandaWaveIcons.Queue,
                label = stringResource(R.string.pandawave_now_playing_library),
                enabled = true,
                onClick = onLibraryClick
            )
        }
        Box(
            modifier = Modifier.weight(2f),
            contentAlignment = Alignment.Center
        ) {
            VolumeControl(
                modifier = Modifier
                    .width(tokens.components.volumeControlMaxWidth)
                    .heightIn(min = tokens.components.volumeControlHeight),
                volume = volume,
                onVolumeChange = onVolumeChange
            )
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            BambooVoiceIndicator()
        }
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
private const val AMBIENT_TRANSITION_LABEL = "ambient-now-playing-transition"
