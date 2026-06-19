package com.adrianrusu.mediaapp.core.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.cardResting
import com.adrianrusu.mediaapp.core.designsystem.tokens.lg
import com.adrianrusu.mediaapp.core.designsystem.tokens.md
import com.adrianrusu.mediaapp.core.designsystem.tokens.sm
import com.adrianrusu.mediaapp.core.designsystem.tokens.touchTargetLg
import com.adrianrusu.mediaapp.core.designsystem.tokens.xl
import com.adrianrusu.mediaapp.core.designsystem.tokens.xs

@Composable
fun BambooSectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val tokens = LocalPandaWaveDesignTokens.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
    ) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BambooMediaHeroCard(
    item: BambooMediaItem,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val resolvedAccent = accentColor.takeUnless { it == Color.Unspecified } ?: MaterialTheme.colorScheme.primary
    val enabled = item.action != BambooMediaAction.Unavailable

    Surface(
        modifier = modifier
            .widthIn(min = 280.dp, max = 360.dp)
            .heightIn(min = 208.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = tokens.elevation.cardResting,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.lg)
        ) {
            BambooArtworkPlate(
                icon = icon,
                accentColor = resolvedAccent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
                verticalAlignment = Alignment.Bottom
            ) {
                BambooMediaCopy(
                    item = item,
                    modifier = Modifier.weight(1f),
                    titleStyle = MaterialTheme.typography.titleMedium
                )
                if (enabled) {
                    Surface(
                        color = resolvedAccent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape
                    ) {
                        Icon(
                            modifier = Modifier
                                .padding(tokens.spacing.sm)
                                .size(tokens.sizing.touchTargetLg / 2),
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BambooMediaTile(
    item: BambooMediaItem,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val resolvedAccent = accentColor.takeUnless { it == Color.Unspecified } ?: MaterialTheme.colorScheme.secondary
    val enabled = item.action != BambooMediaAction.Unavailable

    Surface(
        modifier = modifier
            .widthIn(min = 168.dp, max = 220.dp)
            .heightIn(min = 184.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = tokens.elevation.cardResting,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.md)
        ) {
            BambooArtworkPlate(
                icon = icon,
                accentColor = resolvedAccent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
            )
            BambooMediaCopy(
                item = item,
                titleStyle = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@Composable
fun BambooMediaListRow(
    item: BambooMediaItem,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val resolvedAccent = accentColor.takeUnless { it == Color.Unspecified } ?: MaterialTheme.colorScheme.primary
    val enabled = item.action != BambooMediaAction.Unavailable

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = tokens.elevation.cardResting,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(tokens.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BambooArtworkPlate(
                icon = icon,
                accentColor = resolvedAccent,
                modifier = Modifier.size(64.dp)
            )
            BambooMediaCopy(
                item = item,
                modifier = Modifier.weight(1f),
                titleStyle = MaterialTheme.typography.titleSmall
            )
            if (enabled) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun BambooCategoryCard(
    category: BambooCategoryItem,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color.Unspecified
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val resolvedAccent = accentColor.takeUnless { it == Color.Unspecified } ?: MaterialTheme.colorScheme.primary
    val contentColor = if (category.enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .widthIn(min = 176.dp, max = 240.dp)
            .heightIn(min = 128.dp)
            .clickable(
                enabled = category.enabled,
                role = Role.Button,
                onClick = onClick
            ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = tokens.elevation.cardResting,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(tokens.spacing.md),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
        ) {
            Surface(
                color = resolvedAccent.copy(
                    alpha = if (category.enabled) ENABLED_ACCENT_ALPHA else DISABLED_ACCENT_ALPHA
                ),
                contentColor = resolvedAccent,
                shape = CircleShape
            ) {
                Icon(
                    modifier = Modifier
                        .padding(tokens.spacing.sm)
                        .size(tokens.sizing.touchTargetLg / 2),
                    imageVector = icon,
                    contentDescription = null
                )
            }
            Text(
                text = category.title,
                color = contentColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = category.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BambooFilterChipRow(
    options: List<BambooFilterOption>,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
        contentPadding = PaddingValues(horizontal = tokens.spacing.xs)
    ) {
        items(items = options, key = { it.id }) { option ->
            FilterChip(
                selected = option.selected,
                onClick = { onFilterSelected(option.id) },
                label = {
                    Text(
                        text = option.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
fun BambooSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onVoiceClick: (() -> Unit)? = null
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null
            )
        },
        trailingIcon = {
            if (onVoiceClick != null) {
                IconButton(onClick = onVoiceClick) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = VOICE_SEARCH_CONTENT_DESCRIPTION
                    )
                }
            }
        },
        placeholder = {
            Text(
                text = placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
fun BambooWaveform(modifier: Modifier = Modifier, active: Boolean = true) {
    val tokens = LocalPandaWaveDesignTokens.current
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val bars = listOf(0.38f, 0.82f, 0.56f, 1f, 0.48f, 0.72f, 0.32f)

    Row(
        modifier = modifier.height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(44.dp * fraction)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun BambooArtworkPlate(icon: ImageVector, accentColor: Color, modifier: Modifier = Modifier) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        modifier = modifier,
        color = accentColor.copy(alpha = ENABLED_ACCENT_ALPHA),
        contentColor = accentColor,
        shape = MaterialTheme.shapes.small
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(tokens.sizing.touchTargetLg),
                imageVector = icon,
                contentDescription = null
            )
        }
    }
}

@Composable
private fun BambooMediaCopy(
    item: BambooMediaItem,
    modifier: Modifier = Modifier,
    titleStyle: androidx.compose.ui.text.TextStyle
) {
    val tokens = LocalPandaWaveDesignTokens.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
    ) {
        Text(
            text = item.title,
            color = MaterialTheme.colorScheme.onSurface,
            style = titleStyle,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = item.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val ENABLED_ACCENT_ALPHA = 0.16f
private const val DISABLED_ACCENT_ALPHA = 0.08f
private const val VOICE_SEARCH_CONTENT_DESCRIPTION = "Voice search"
