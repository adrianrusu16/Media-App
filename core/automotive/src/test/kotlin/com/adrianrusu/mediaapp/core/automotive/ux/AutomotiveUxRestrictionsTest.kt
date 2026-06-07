package com.adrianrusu.mediaapp.core.automotive.ux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomotiveUxRestrictionsTest {
    @Test
    fun unrestrictedStateIsNotRestricted() {
        val restrictions = AutomotiveUxRestrictions.unrestricted(
            AutomotiveUxRestrictions.Source.NotAutomotive
        )

        assertFalse(restrictions.isRestricted)
        assertFalse(restrictions.hasRestriction(1))
    }

    @Test
    fun activeRestrictionFlagIsDetected() {
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
