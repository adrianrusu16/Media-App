package com.adrianrusu.pandawave.core.ui.interaction

import kotlin.test.Test
import kotlin.test.assertEquals

class UserInteractionTrackerTest {
    @Test
    fun `records monotonically increasing interaction revisions`() {
        val tracker = UserInteractionTracker()

        assertEquals(0L, tracker.revision.value)

        tracker.recordInteraction()
        tracker.recordInteraction()

        assertEquals(2L, tracker.revision.value)
    }
}
