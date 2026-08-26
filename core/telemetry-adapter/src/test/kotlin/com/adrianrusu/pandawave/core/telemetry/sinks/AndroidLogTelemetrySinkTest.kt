package com.adrianrusu.pandawave.core.telemetry.sinks

import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetryModule
import com.adrianrusu.pandawave.core.telemetry.TelemetrySeverity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidLogTelemetrySinkTest {
    @Test
    fun `every module exposes a stable logcat tag within the Android limit`() {
        assertTrue(TelemetryModule.entries.all { module -> module.logcatTag.length <= 23 })
        assertEquals("PandaWave:Playback", TelemetryModule.Playback.logcatTag)
        assertEquals("PandaWave:Home", TelemetryModule.Home.logcatTag)
        assertEquals("PandaWave:Nps", TelemetryModule.Nps.logcatTag)
        assertEquals("PandaWave:Player", TelemetryModule.Player.logcatTag)
    }

    @Test
    fun `logcat formatter emits deterministic single line fields`() {
        val event = TelemetryEvent(
            name = "playback.command",
            module = TelemetryModule.Playback,
            severity = TelemetrySeverity.Debug,
            attributes = mapOf(
                "z_value" to "last\nline",
                "a_value" to "first"
            ),
            timestampEpochMillis = 1L
        )

        assertEquals(
            "event=playback.command a_value=first z_value=last\\nline",
            LogcatEventFormatter.format(event)
        )
    }
}
