package com.adrianrusu.pandawave.core.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.adrianrusu.pandawave.core.designsystem.theme.PandaWaveTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BambooActionCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun completeCardIsTheActionTarget() {
        var clicks = 0
        compose.setContent {
            PandaWaveTheme(darkTheme = true) {
                BambooActionCard(
                    title = "Login",
                    body = "Use your Canopy account",
                    actionLabel = "Login",
                    actionEnabled = true,
                    onActionClick = { clicks += 1 },
                    modifier = Modifier.testTag("action-card")
                )
            }
        }

        compose.onNodeWithTag("action-card")
            .assertHasClickAction()
            .performClick()

        assertEquals(1, clicks)
    }
}
