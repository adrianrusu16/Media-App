package com.adrianrusu.pandawave.core.common.trace

import android.os.Trace

interface TraceSink {
    fun beginSection(name: String)

    fun endSection()
}

object PandaTrace {
    @Volatile
    private var sink: TraceSink = AndroidTraceSink

    fun beginSection(name: String) {
        sink.beginSection(name.traceSectionName())
    }

    fun endSection() {
        sink.endSection()
    }

    fun <T> section(name: String, block: () -> T): T {
        beginSection(name)
        return try {
            block()
        } finally {
            endSection()
        }
    }

    fun withSinkForTest(testSink: TraceSink): AutoCloseable {
        val previous = sink
        sink = testSink
        return AutoCloseable { sink = previous }
    }
}

private object AndroidTraceSink : TraceSink {
    override fun beginSection(name: String) {
        runCatching { Trace.beginSection(name) }
    }

    override fun endSection() {
        runCatching { Trace.endSection() }
    }
}

private const val MAX_TRACE_SECTION_NAME_LENGTH = 127

private fun String.traceSectionName(): String =
    if (length <= MAX_TRACE_SECTION_NAME_LENGTH) this else take(MAX_TRACE_SECTION_NAME_LENGTH)
