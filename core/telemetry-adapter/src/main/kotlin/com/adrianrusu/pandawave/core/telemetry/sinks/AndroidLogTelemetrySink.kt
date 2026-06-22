package com.adrianrusu.pandawave.core.telemetry.sinks

import android.util.Log
import com.adrianrusu.pandawave.core.telemetry.TelemetryEvent
import com.adrianrusu.pandawave.core.telemetry.TelemetrySeverity
import com.adrianrusu.pandawave.core.telemetry.TelemetrySink

class AndroidLogTelemetrySink(private val tag: String = DEFAULT_TAG) : TelemetrySink {
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
        const val DEFAULT_TAG = "PandaWave"
    }
}
