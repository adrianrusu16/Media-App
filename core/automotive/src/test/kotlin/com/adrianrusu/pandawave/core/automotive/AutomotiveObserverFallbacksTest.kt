package com.adrianrusu.pandawave.core.automotive

import com.adrianrusu.pandawave.core.automotive.driving.AutomotiveDrivingState
import com.adrianrusu.pandawave.core.automotive.driving.AutomotiveDrivingStateObserver
import com.adrianrusu.pandawave.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.pandawave.core.automotive.ux.AutomotiveUxRestrictions
import kotlin.test.Test
import kotlin.test.assertEquals

class AutomotiveObserverFallbacksTest {
    @Test
    fun `unavailable observers publish conservative platform-independent state`() {
        var drivingUpdate: AutomotiveDrivingState? = null
        var restrictionUpdate: AutomotiveUxRestrictions? = null

        AutomotiveDrivingStateObserver.Unavailable.start { drivingUpdate = it }
        AutomotiveUxRestrictionObserver.Unavailable.start { restrictionUpdate = it }

        assertEquals(AutomotiveDrivingState.Unknown, drivingUpdate)
        assertEquals(
            AutomotiveUxRestrictions.unrestricted(AutomotiveUxRestrictions.Source.NotAutomotive),
            restrictionUpdate
        )
        assertEquals(
            AutomotiveUxRestrictions.unrestricted(AutomotiveUxRestrictions.Source.NotAutomotive),
            AutomotiveUxRestrictionObserver.Unavailable.current()
        )
    }
}
