package com.adrianrusu.pandawave.appshell.navigation

import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation3.runtime.rememberNavBackStack
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PandaWaveNavigationRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `preferences stack survives saved state restoration`() {
        val restorationTester = StateRestorationTester(composeRule)
        lateinit var navigator: PandaWaveNavigator

        restorationTester.setContent {
            val backStack = rememberNavBackStack(HomeDestination)
            navigator = remember(backStack) { PandaWaveNavigator(backStack) }
        }
        composeRule.runOnIdle {
            navigator.openPreferences()
        }

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle {
            assertEquals(PreferencesDestination, navigator.currentDestination)
        }
    }
}
