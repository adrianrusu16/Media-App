package com.adrianrusu.mediaapp.core.ui.focus

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.adrianrusu.mediaapp.core.designsystem.theme.PandaWaveTheme
import org.junit.Rule
import org.junit.Test

class BambooRotaryColumnTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dpadMovesFocusAndRevealsTheNextAction() {
        composeRule.setContent {
            PandaWaveTheme(darkTheme = true) {
                BambooRotaryColumn(modifier = Modifier.height(120.dp)) {
                    repeat(12) { index ->
                        Button(
                            modifier = Modifier
                                .testTag("action-$index")
                                .bambooBringIntoViewOnFocus(),
                            onClick = {}
                        ) {
                            Text(text = "Action $index")
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("action-0").requestFocus()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("action-0").assertIsFocused()
        composeRule.onNodeWithTag("action-0").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("action-1")
            .assertIsFocused()
            .assertIsDisplayed()
    }
}
