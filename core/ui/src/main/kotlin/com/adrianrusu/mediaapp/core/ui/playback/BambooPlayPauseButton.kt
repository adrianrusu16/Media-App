package com.adrianrusu.mediaapp.core.ui.playback

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.adrianrusu.mediaapp.core.designsystem.R
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.cardResting
import com.adrianrusu.mediaapp.core.designsystem.tokens.iconLarge
import com.adrianrusu.mediaapp.core.designsystem.tokens.iconMedium
import com.adrianrusu.mediaapp.core.designsystem.tokens.miniPlayerTransportButtonSize
import com.adrianrusu.mediaapp.core.designsystem.tokens.nowPlayingPrimaryButton
import com.adrianrusu.mediaapp.core.ui.focus.bambooBringIntoViewOnFocus
import com.adrianrusu.mediaapp.core.ui.icons.PandaWaveIcons

enum class BambooPlaybackControlSize { MiniPlayer, NowPlaying }

@Composable
fun BambooPlayPauseButton(
    playing: Boolean,
    enabled: Boolean,
    size: BambooPlaybackControlSize,
    playContentDescription: String,
    pauseContentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val buttonSize = when (size) {
        BambooPlaybackControlSize.MiniPlayer -> tokens.components.miniPlayerTransportButtonSize
        BambooPlaybackControlSize.NowPlaying -> tokens.layout.nowPlayingPrimaryButton
    }
    val iconSize = when (size) {
        BambooPlaybackControlSize.MiniPlayer -> tokens.components.iconMedium
        BambooPlaybackControlSize.NowPlaying -> tokens.components.iconLarge
    }

    Surface(
        modifier = modifier.size(buttonSize),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
        shadowElevation = tokens.elevation.cardResting
    ) {
        IconButton(
            modifier = Modifier.fillMaxSize().bambooBringIntoViewOnFocus(),
            enabled = enabled,
            onClick = onClick
        ) {
            if (playing) {
                Icon(
                    imageVector = PandaWaveIcons.Pause,
                    contentDescription = pauseContentDescription,
                    modifier = Modifier.size(iconSize)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.pandawave_ic_panda_paw),
                    contentDescription = playContentDescription,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
