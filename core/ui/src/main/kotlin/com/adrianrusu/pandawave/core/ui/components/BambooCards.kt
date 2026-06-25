package com.adrianrusu.pandawave.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.actionableCardMinHeight
import com.adrianrusu.pandawave.core.designsystem.tokens.cardPadding
import com.adrianrusu.pandawave.core.designsystem.tokens.cardResting
import com.adrianrusu.pandawave.core.designsystem.tokens.lg
import com.adrianrusu.pandawave.core.designsystem.tokens.md
import com.adrianrusu.pandawave.core.designsystem.tokens.preferenceContentPadding
import com.adrianrusu.pandawave.core.designsystem.tokens.preferenceControlWidth
import com.adrianrusu.pandawave.core.designsystem.tokens.preferenceRowMinHeight
import com.adrianrusu.pandawave.core.designsystem.tokens.sm
import com.adrianrusu.pandawave.core.designsystem.tokens.xs
import com.adrianrusu.pandawave.core.ui.focus.bambooBringIntoViewOnFocus
import com.adrianrusu.pandawave.core.ui.focus.bambooFocusIndicator

@Composable
fun BambooCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    contentPadding: Dp? = null,
    content: @Composable () -> Unit
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val resolvedContentPadding = contentPadding ?: tokens.components.cardPadding

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = tokens.elevation.cardResting,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(resolvedContentPadding),
            verticalArrangement = Arrangement.spacedBy(tokens.spacing.sm)
        ) {
            content()
        }
    }
}

@Composable
fun BambooStatusCard(title: String, body: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    BambooCard(
        modifier = modifier,
        highlighted = highlighted
    ) {
        BambooTitleBody(
            title = title,
            body = body
        )
    }
}

@Composable
fun BambooActionCard(
    title: String,
    body: String,
    actionLabel: String,
    actionEnabled: Boolean,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current

    BambooCard(modifier = modifier.heightIn(min = tokens.components.actionableCardMinHeight)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BambooTitleBody(
                title = title,
                body = body,
                modifier = Modifier.weight(1f)
            )
            Button(
                modifier = Modifier.bambooBringIntoViewOnFocus(),
                enabled = actionEnabled,
                onClick = onActionClick
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
fun BambooSwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current

    BambooCard(
        modifier = modifier
            .heightIn(min = tokens.components.preferenceRowMinHeight)
            .bambooFocusIndicator(enabled = enabled)
            .bambooBringIntoViewOnFocus()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        contentPadding = tokens.components.preferenceContentPadding
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BambooTitleBody(
                title = title,
                body = body,
                modifier = Modifier.weight(1f)
            )
            Switch(
                modifier = Modifier.width(tokens.components.preferenceControlWidth),
                checked = checked,
                enabled = enabled,
                onCheckedChange = null
            )
        }
    }
}

@Composable
fun BambooSelectableRow(
    title: String,
    body: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = tokens.components.preferenceRowMinHeight)
            .bambooFocusIndicator(enabled = enabled)
            .bambooBringIntoViewOnFocus()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            ),
        color = if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(tokens.components.preferenceContentPadding),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                modifier = Modifier.width(tokens.components.preferenceControlWidth),
                selected = selected,
                enabled = enabled,
                onClick = null
            )
            BambooTitleBody(
                title = title,
                body = body,
                titleStyle = BambooTitleStyle.Body
            )
        }
    }
}

@Composable
fun BambooLoadingState(label: String, modifier: Modifier = Modifier) {
    val tokens = LocalPandaWaveDesignTokens.current

    BambooCard(modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator()
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun BambooEmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    BambooCard(modifier = modifier) {
        BambooTitleBody(
            title = title,
            body = body
        )
    }
}

@Composable
fun BambooTitleBody(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    titleStyle: BambooTitleStyle = BambooTitleStyle.Title
) {
    val tokens = LocalPandaWaveDesignTokens.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
    ) {
        Text(
            modifier = Modifier.semantics {
                if (titleStyle == BambooTitleStyle.Title) {
                    heading()
                }
            },
            text = title,
            style = if (titleStyle == BambooTitleStyle.Title) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

enum class BambooTitleStyle {
    Title,
    Body
}
