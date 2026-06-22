package com.adrianrusu.pandawave.core.ui.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.adrianrusu.pandawave.core.designsystem.theme.PandaWaveTheme
import com.adrianrusu.pandawave.core.ui.components.BambooSelectableRow
import org.junit.Rule
import org.junit.Test

class BambooFocusIndicatorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disabledSelectableRowIsSkipped() {
        composeRule.setContent {
            PandaWaveTheme(darkTheme = true) {
                Column {
                    Button(
                        modifier = Modifier.testTag("start"),
                        onClick = {}
                    ) {
                        Text(text = "Start")
                    }
                    BambooSelectableRow(
                        title = "Disabled",
                        body = "Unavailable",
                        selected = false,
                        enabled = false,
                        onClick = {},
                        modifier = Modifier.testTag("disabled")
                    )
                    BambooSelectableRow(
                        title = "Enabled",
                        body = "Available",
                        selected = false,
                        enabled = true,
                        onClick = {},
                        modifier = Modifier.testTag("enabled")
                    )
                }
            }
        }

        composeRule.onNodeWithTag("start").requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("start").assertIsFocused()
        composeRule.onNodeWithTag("start").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("enabled").assertIsFocused()
    }
}
