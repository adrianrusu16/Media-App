package com.adrianrusu.mediaapp.core.telemetry.sinks

import android.util.Log
import com.adrianrusu.mediaapp.core.telemetry.TelemetryEvent
import com.adrianrusu.mediaapp.core.telemetry.TelemetrySeverity
import com.adrianrusu.mediaapp.core.telemetry.TelemetrySink

class AndroidLogTelemetrySink(
    private val tag: String = DefaultTag,
) : TelemetrySink {
    override fun record(event: TelemetryEvent) {
        val message = buildString {
            append(event.name)
            if (event.attributes.isNotEmpty()) {
                append(" ")
                append(event.attributes)
            }
        }

        when (event.severity) {
            TelemetrySeverity.Debug -> Log.d(tag, message, event.throwable)
            TelemetrySeverity.Info -> Log.i(tag, message, event.throwable)
            TelemetrySeverity.Warning -> Log.w(tag, message, event.throwable)
            TelemetrySeverity.Error -> Log.e(tag, message, event.throwable)
        }
    }

    private companion object {
        const val DefaultTag = "MediaApp"
    }
}
