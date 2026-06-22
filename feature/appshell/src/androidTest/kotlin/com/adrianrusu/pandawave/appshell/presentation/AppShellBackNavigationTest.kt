package com.adrianrusu.pandawave.appshell.presentation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.adrianrusu.pandawave.appshell.domain.AppShellState
import com.adrianrusu.pandawave.core.designsystem.theme.PandaWaveTheme
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppShellBackNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `back at home requests task background exactly once`() {
        var backgroundRequests = 0
        composeRule.setContent {
            PandaWaveTheme(
                darkTheme = true,
                themePreference = PandaWaveThemePreference.ForestTechDark
            ) {
                AppShellScreen(
                    state = AppShellState(),
                    onIntent = {},
                    onMoveTaskToBack = { backgroundRequests += 1 }
                )
            }
        }

        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.runOnIdle {
            assertEquals(1, backgroundRequests)
        }
    }
}
