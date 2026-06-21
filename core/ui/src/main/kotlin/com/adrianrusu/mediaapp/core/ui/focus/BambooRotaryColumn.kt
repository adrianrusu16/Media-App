package com.adrianrusu.mediaapp.core.ui.focus

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalFocusManager
import com.adrianrusu.mediaapp.core.designsystem.tokens.LocalPandaWaveDesignTokens

@Composable
fun BambooRotaryColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scrollEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val focusManager = LocalFocusManager.current
    val thresholdPixels = LocalPandaWaveDesignTokens.current.components.rotaryStepThresholdPx.toFloat()
    val accumulator = remember(thresholdPixels) {
        RotaryFocusStepAccumulator(thresholdPixels = thresholdPixels)
    }
    val scrollState = rememberScrollState()
    val scrollModifier = if (scrollEnabled) {
        Modifier.verticalScroll(scrollState)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .onRotaryScrollEvent { event ->
                when (accumulator.add(event.verticalScrollPixels)) {
                    RotaryFocusStep.Previous -> focusManager.moveFocus(FocusDirection.Previous)
                    RotaryFocusStep.Next -> focusManager.moveFocus(FocusDirection.Next)
                    null -> true
                }
            }
            .then(scrollModifier)
            .focusGroup()
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}
