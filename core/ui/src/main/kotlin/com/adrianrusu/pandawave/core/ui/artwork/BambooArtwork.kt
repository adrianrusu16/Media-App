package com.adrianrusu.pandawave.core.ui.artwork

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.designsystem.R as DesignSystemR
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.md
import com.adrianrusu.pandawave.core.designsystem.tokens.touchTargetLg
import java.net.URI

@Composable
fun BambooArtwork(
    artwork: BambooArtworkModel?,
    fallback: BambooArtworkFallback,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val shape = MaterialTheme.shapes.small
    val clipped = modifier.clip(shape)

    if (artwork == null) {
        // Incomplete identity already warned in toBambooArtworkModel(); bare null is expected.
        LaunchedEffect(fallback) {
            PandaLog.d(PandaLog.Tag.ARTWORK) {
                "artwork.ui.logo_fallback reason=missing_model kind=${fallback.wireValue()}"
            }
        }
        BambooArtworkLogoPlate(
            accentColor = MaterialTheme.colorScheme.primary,
            modifier = clipped
        )
        return
    }

    val context = LocalContext.current
    val cacheKey = artwork.cacheKey()
    val request = ImageRequest.Builder(context)
        .data(artwork.uri)
        .memoryCacheKey(cacheKey)
        .diskCacheKey(cacheKey)
        .listener(
            onStart = {
                PandaLog.d(PandaLog.Tag.ARTWORK) {
                    "artwork.coil.start id=${PandaLog.field(artwork.id)} " +
                        "version=${PandaLog.field(artwork.version)} " +
                        "uri=${artworkUriForLog(artwork.uri)}"
                }
            },
            onSuccess = { _, result: SuccessResult ->
                PandaLog.d(PandaLog.Tag.ARTWORK) {
                    "artwork.coil.success id=${PandaLog.field(artwork.id)} " +
                        "dataSource=${result.dataSource}"
                }
            },
            onError = { _, result: ErrorResult ->
                PandaLog.w(PandaLog.Tag.ARTWORK, result.throwable) {
                    "artwork.coil.error id=${PandaLog.field(artwork.id)} " +
                        "version=${PandaLog.field(artwork.version)} " +
                        "uri=${artworkUriForLog(artwork.uri)} " +
                        "kind=${fallback.wireValue()} " +
                        "message=${PandaLog.field(result.throwable.message)}"
                }
            },
            onCancel = {
                PandaLog.d(PandaLog.Tag.ARTWORK) {
                    "artwork.coil.cancel id=${PandaLog.field(artwork.id)}"
                }
            }
        )
        .build()

    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = clipped,
        contentScale = contentScale,
        loading = {
            BambooArtworkLogoPlate(
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize()
            )
        },
        error = {
            BambooArtworkLogoPlate(
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize()
            )
        },
        success = {
            SubcomposeAsyncImageContent()
        }
    )
}

@Composable
internal fun BambooArtworkLogoPlate(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        modifier = modifier,
        color = accentColor.copy(alpha = ARTWORK_FALLBACK_ACCENT_ALPHA),
        contentColor = accentColor,
        shape = MaterialTheme.shapes.small
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(DesignSystemR.drawable.pandawave_ic_logo),
                contentDescription = null,
                modifier = Modifier.size(tokens.sizing.touchTargetLg),
                contentScale = ContentScale.Fit
            )
        }
    }
}

internal fun artworkUriForLog(uri: String): String {
    val parsed = runCatching { URI(uri) }.getOrNull() ?: return PandaLog.field(uri)
    val host = parsed.host.orEmpty()
    val path = parsed.path.orEmpty()
    return "scheme=${parsed.scheme.orEmpty()} host=$host path=${PandaLog.field(path, maxChars = 96)}"
}

private fun BambooArtworkFallback.wireValue(): String = when (this) {
    BambooArtworkFallback.Track -> "track"
    BambooArtworkFallback.Album -> "album"
    BambooArtworkFallback.Artist -> "artist"
    BambooArtworkFallback.Playlist -> "playlist"
    BambooArtworkFallback.Radio -> "radio"
}

private const val ARTWORK_FALLBACK_ACCENT_ALPHA = 0.16f
