package com.adrianrusu.pandawave.core.ui.focus

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import com.adrianrusu.pandawave.core.designsystem.tokens.LocalPandaWaveDesignTokens
import com.adrianrusu.pandawave.core.designsystem.tokens.outlinePadding
import com.adrianrusu.pandawave.core.designsystem.tokens.outlineWidth

fun Modifier.bambooFocusIndicator(enabled: Boolean = true): Modifier = composed {
    val tokens = LocalPandaWaveDesignTokens.current
    var focused by remember { mutableStateOf(false) }
    val outlineColor = if (focused && enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    onFocusChanged { focusState -> focused = focusState.isFocused }
        .border(
            width = tokens.focus.outlineWidth,
            color = outlineColor,
            shape = MaterialTheme.shapes.small
        )
        .padding(tokens.focus.outlinePadding)
}
