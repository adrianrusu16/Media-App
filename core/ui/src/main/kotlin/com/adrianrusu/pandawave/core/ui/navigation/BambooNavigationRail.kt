package com.adrianrusu.pandawave.core.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.iconLarge
import com.adrianrusu.pandawave.core.designsystem.tokens.navigationLogoSize
import com.adrianrusu.pandawave.core.designsystem.tokens.navigationRailWidth
import com.adrianrusu.pandawave.core.designsystem.tokens.xs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BambooNavigationRail(
    items: List<BambooNavigationItemModel>,
    logo: Painter,
    logoContentDescription: String,
    onLogoClick: () -> Unit,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomItemId: String? = null
) {
    val tokens = LocalPandaWaveDesignTokens.current
    val primaryItems = items.filterNot { it.id == bottomItemId }
    val bottomItem = items.firstOrNull { it.id == bottomItemId }

    NavigationRail(
        modifier = modifier
            .width(tokens.layout.navigationRailWidth)
            .focusRestorer()
            .focusGroup()
            .testTag("navigation-rail-zone"),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        windowInsets = WindowInsets(0, 0, 0, 0),
        header = {
            Surface(
                modifier = Modifier
                    .padding(top = tokens.spacing.xs, bottom = tokens.spacing.xs)
                    .testTag("navigation-logo"),
                onClick = onLogoClick,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    modifier = Modifier.size(tokens.components.navigationLogoSize),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = logo,
                        contentDescription = logoContentDescription,
                        modifier = Modifier.size(tokens.components.iconLarge)
                    )
                }
            }
        }
    ) {
        primaryItems.forEach { item -> BambooNavigationItem(model = item, onClick = onItemClick) }
        if (bottomItem != null) {
            Spacer(modifier = Modifier.weight(1F))
            BambooNavigationItem(model = bottomItem, onClick = onItemClick)
        }
    }
}
