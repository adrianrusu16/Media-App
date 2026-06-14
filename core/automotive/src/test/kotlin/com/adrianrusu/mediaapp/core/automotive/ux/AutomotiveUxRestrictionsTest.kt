package com.adrianrusu.mediaapp.core.automotive.ux

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutomotiveUxRestrictionsTest {
    @Test
    fun `unrestricted state is not restricted`() {
        val restrictions = AutomotiveUxRestrictions.unrestricted(
            AutomotiveUxRestrictions.Source.NotAutomotive
        )

        assertFalse(restrictions.isRestricted)
        assertFalse(restrictions.hasRestriction(1))
    }

    @Test
    fun `active restriction flag is detected`() {
        val restrictions = AutomotiveUxRestrictions(
            source = AutomotiveUxRestrictions.Source.AutomotivePlatform,
            requiresDistractionOptimization = true,
            activeRestrictions = 0b101,
            maxContentDepth = 2,
            maxCumulativeContentItems = 20,
            maxRestrictedStringLength = 24
        )

        assertTrue(restrictions.isRestricted)
        assertTrue(restrictions.hasRestriction(0b001))
        assertTrue(restrictions.hasRestriction(0b100))
        assertFalse(restrictions.hasRestriction(0b010))
    }
}
