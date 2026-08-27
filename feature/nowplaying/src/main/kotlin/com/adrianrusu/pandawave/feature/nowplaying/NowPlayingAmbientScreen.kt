package com.adrianrusu.pandawave.feature.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.ambientArtworkMaxSize
import com.adrianrusu.pandawave.core.designsystem.tokens.ambientArtworkMinSize
import com.adrianrusu.pandawave.core.designsystem.tokens.ambientVisualizerHeight
import com.adrianrusu.pandawave.core.designsystem.tokens.md
import com.adrianrusu.pandawave.core.designsystem.tokens.xl
import com.adrianrusu.pandawave.core.ui.artwork.BambooArtwork
import com.adrianrusu.pandawave.core.ui.artwork.BambooArtworkFallback
import com.adrianrusu.pandawave.core.ui.artwork.BambooArtworkModel
import com.adrianrusu.pandawave.core.ui.audio.visualizer.BambooAmbientVisualizer

@Composable
fun NowPlayingAmbientScreen(
    modifier: Modifier = Modifier,
    amplitudes: FloatArray,
    artwork: BambooArtworkModel?,
    title: String,
    artist: String,
    onShowPlaybackControls: () -> Unit
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val actionLabel = stringResource(R.string.pandawave_now_playing_show_playback_controls)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(tokens.colors.surface))
            .clickable(
                onClickLabel = actionLabel,
                onClick = onShowPlaybackControls
            )
            .testTag("ambient-now-playing")
    ) {
        val availableArtworkSize = minOf(
            maxWidth * ARTWORK_WIDTH_FRACTION,
            maxHeight - (tokens.spacing.xl * 2)
        )
        val artworkSize = availableArtworkSize.coerceIn(
            minimumValue = minOf(tokens.components.ambientArtworkMinSize, availableArtworkSize),
            maximumValue = tokens.components.ambientArtworkMaxSize
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(tokens.spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BambooArtwork(
                artwork = artwork,
                fallback = BambooArtworkFallback.Track,
                contentDescription = null,
                modifier = Modifier.size(artworkSize)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
            ) {
                Text(
                    text = title,
                    color = Color(tokens.colors.onSurface),
                    style = tokens.typography.display,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    color = Color(tokens.colors.onSurfaceVariant),
                    style = tokens.typography.sectionTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BambooAmbientVisualizer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tokens.components.ambientVisualizerHeight)
                        .clearAndSetSemantics { },
                    amplitudes = amplitudes
                )
            }
        }
    }
}

private const val ARTWORK_WIDTH_FRACTION = 0.42f
