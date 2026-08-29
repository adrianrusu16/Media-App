package com.adrianrusu.pandawave

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

class AppShellInsetsTest {
    @Suppress("DEPRECATION")
    private val compose = createAndroidComposeRule<MainActivity>()

    private val grantVisualizerPermission = TestRule { base, _ ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                instrumentation.uiAutomation.grantRuntimePermission(
                    instrumentation.targetContext.packageName,
                    Manifest.permission.RECORD_AUDIO
                )
                base.evaluate()
            }
        }
    }

    @get:Rule
    @Suppress("DEPRECATION")
    val rules: RuleChain = RuleChain
        .outerRule(grantVisualizerPermission)
        .around(compose)

    @Test
    fun shellChromeStaysInsideTheVisibleAaosBounds() {
        compose.waitUntil(timeoutMillis = 30_000) {
            runCatching {
                compose.onAllNodesWithTag("mini-player-zone")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }.getOrDefault(false)
        }

        val systemBars = ViewCompat
            .getRootWindowInsets(compose.activity.window.decorView)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
            ?: error("The AAOS test device did not report system bar insets")
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val rail = compose.onNodeWithTag("navigation-rail-zone")
            .fetchSemanticsNode()
            .boundsInRoot
        val logo = compose.onNodeWithTag("navigation-logo")
            .fetchSemanticsNode()
            .boundsInRoot
        val miniPlayer = compose.onNodeWithTag("mini-player-zone")
            .fetchSemanticsNode()
            .boundsInRoot
        val visibleBottom = root.bottom - systemBars.bottom
        val expectedLogoOffset = with(compose.density) { 14.dp.toPx() }
        val maximumChromeGap = with(compose.density) { 4.dp.toPx() }

        assertTrue("The test device must expose a top system bar", systemBars.top > 0)
        assertTrue("The test device must expose a bottom system bar", systemBars.bottom > 0)
        assertEquals(systemBars.top.toFloat(), rail.top, 0.5f)
        assertEquals(expectedLogoOffset, logo.top - rail.top, 0.5f)
        assertTrue(
            "The mini-player must not extend into the AAOS navigation bar",
            miniPlayer.bottom <= visibleBottom + 0.5f
        )
        assertTrue(
            "The mini-player must remain flush with the visible app bottom",
            visibleBottom - miniPlayer.bottom <= maximumChromeGap
        )
    }
}
