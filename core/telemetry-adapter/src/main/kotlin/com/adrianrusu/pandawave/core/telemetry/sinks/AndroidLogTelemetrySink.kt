package com.adrianrusu.pandawave.core.telemetry.sinks

import android.util.Log
import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetrySeverity
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink

class AndroidLogTelemetrySink : TelemetrySink {
    override fun record(event: TelemetryEvent) {
        val message = LogcatEventFormatter.format(event)
        val tag = event.module.logcatTag

        when (event.severity) {
            TelemetrySeverity.Debug -> Log.d(tag, message, event.throwable)
            TelemetrySeverity.Info -> Log.i(tag, message, event.throwable)
            TelemetrySeverity.Warning -> Log.w(tag, message, event.throwable)
            TelemetrySeverity.Error -> Log.e(tag, message, event.throwable)
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
