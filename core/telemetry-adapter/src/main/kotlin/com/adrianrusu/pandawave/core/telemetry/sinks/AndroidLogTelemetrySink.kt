package com.adrianrusu.pandawave.core.telemetry.sinks

import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetrySeverity
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink

class AndroidLogTelemetrySink : TelemetrySink {
    override fun record(event: TelemetryEvent) {
        val tag = event.module.logcatTag
        val throwable = event.throwable
        when (event.severity) {
            TelemetrySeverity.Debug -> PandaLog.d(tag) { LogcatEventFormatter.format(event) }
            TelemetrySeverity.Info -> PandaLog.i(tag) { LogcatEventFormatter.format(event) }
            TelemetrySeverity.Warning -> PandaLog.w(tag, throwable) { LogcatEventFormatter.format(event) }
            TelemetrySeverity.Error -> PandaLog.e(tag, throwable) { LogcatEventFormatter.format(event) }
        }
    }
}

internal object LogcatEventFormatter {
    fun format(event: TelemetryEvent): String = buildString {
        append("event=")
        append(event.name.escapeLogcat())
        event.attributes.toSortedMap().forEach { (key, value) ->
            append(' ')
            append(key.escapeLogcat())
            append('=')
            append(value.escapeLogcat())
        }
    }

    private fun String.escapeLogcat(): String = buildString {
        this@escapeLogcat.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }
}
