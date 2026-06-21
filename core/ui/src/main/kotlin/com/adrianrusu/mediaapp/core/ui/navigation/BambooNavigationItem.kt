package com.adrianrusu.mediaapp.core.ui.navigation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.mediaapp.core.designsystem.tokens.navigationItemHeight
import com.adrianrusu.mediaapp.core.designsystem.tokens.navigationSelectedIndicatorHeight
import com.adrianrusu.mediaapp.core.designsystem.tokens.navigationSelectedIndicatorInset
import com.adrianrusu.mediaapp.core.designsystem.tokens.navigationSelectedIndicatorWidth
import com.adrianrusu.mediaapp.core.designsystem.tokens.smallCorner
import com.adrianrusu.mediaapp.core.designsystem.tokens.xs
import com.adrianrusu.mediaapp.core.ui.focus.bambooBringIntoViewOnFocus
import com.adrianrusu.mediaapp.core.ui.focus.bambooFocusIndicator

data class BambooNavigationItemModel(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val showLabel: Boolean,
    val enabled: Boolean = true
)

@Composable
internal fun BambooNavigationItem(
    model: BambooNavigationItemModel,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (model.selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tokens.components.navigationItemHeight),
        contentAlignment = Alignment.Center
    ) {
        if (model.selected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = tokens.components.navigationSelectedIndicatorInset)
                    .width(tokens.layout.navigationSelectedIndicatorWidth)
                    .height(tokens.layout.navigationSelectedIndicatorHeight),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(tokens.shape.smallCorner)
            ) { }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .bambooFocusIndicator()
                .bambooBringIntoViewOnFocus()
                .selectable(
                    selected = model.selected,
                    enabled = model.enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Tab,
                    onClick = { onClick(model.id) }
                )
                .testTag("navigation-${model.id}"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = model.icon, contentDescription = null, tint = contentColor)
            if (model.showLabel) {
                Spacer(modifier = Modifier.height(tokens.spacing.xs))
                Text(
                    text = model.label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
