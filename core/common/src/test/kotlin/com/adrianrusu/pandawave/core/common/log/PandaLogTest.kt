package com.adrianrusu.pandawave.core.common.log

import android.util.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PandaLogTest {
    @Test
    fun tagsStayWithinLogcatLimit() {
        val tags = listOf(
            PandaLog.Tag.HOME,
            PandaLog.Tag.NPS,
            PandaLog.Tag.LIBRARY,
            PandaLog.Tag.SEARCH,
            PandaLog.Tag.AUTH,
            PandaLog.Tag.ACCOUNT,
            PandaLog.Tag.MEDIA,
            PandaLog.Tag.PLAYER,
            PandaLog.Tag.APP_SHELL,
            PandaLog.Tag.HISTORY,
        )
        assertTrue(tags.all { it.length <= 23 })
        assertTrue(tags.all { it.startsWith("PandaWave:") })
    }

    @Test
    fun recordsStructuredEventThroughTestSink() {
        val sink = RecordingPandaLogSink()

        PandaLog.withSinkForTest(sink).use {
            PandaLog.i(PandaLog.Tag.HOME) {
                "play_requested section=recommendations trackId=track-1 title=Song"
            }
        }

        assertEquals(
            listOf("info:${PandaLog.Tag.HOME}:play_requested section=recommendations trackId=track-1 title=Song"),
            sink.events,
        )
    }

    @Test
    fun redactsStreamQueryTokensEmailsAndSecrets() {
        val sink = RecordingPandaLogSink()

        PandaLog.withSinkForTest(sink).use {
            PandaLog.i(PandaLog.Tag.PLAYER) {
                "source uri=https://cdn.example/stream.m3u8?token=abc123 email=user@example.com password=hunter2"
            }
        }

        assertEquals(
            listOf(
                "info:${PandaLog.Tag.PLAYER}:source uri=https://cdn.example/stream.m3u8?[REDACTED] " +
                    "email=[REDACTED] password=[REDACTED]",
            ),
            sink.events,
        )
    }

    @Test
    fun fieldCompactsWhitespaceAndTruncates() {
        assertEquals("Quiet Highway", PandaLog.field("Quiet\nHighway"))
        assertEquals("a".repeat(80), PandaLog.field("a".repeat(120)))
        assertEquals("", PandaLog.field(null))
    }
}

private class RecordingPandaLogSink : PandaLogSink {
    val events = mutableListOf<String>()

    override fun println(priority: Int, tag: String, message: String, throwable: Throwable?) {
        val level = when (priority) {
            Log.VERBOSE -> "verbose"
            Log.DEBUG -> "debug"
            Log.INFO -> "info"
            Log.WARN -> "warn"
            Log.ERROR -> "error"
            else -> priority.toString()
        }
        events += "$level:$tag:$message"
    }
}
