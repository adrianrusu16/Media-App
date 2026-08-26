package com.adrianrusu.pandawave.core.common.log

import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

fun interface PandaLogSink {
    fun println(priority: Int, tag: String, message: String, throwable: Throwable?)
}

/**
 * Diagnostic logcat helper for PandaWave.
 *
 * VERBOSE/DEBUG are gated by [Log.isLoggable] unless [setDebuggable] is true.
 * Formatting and logcat I/O run off the main thread.
 */
object PandaLog {
    object Tag {
        const val HOME = "PandaWave:Home"
        const val NPS = "PandaWave:Nps"
        const val LIBRARY = "PandaWave:Library"
        const val SEARCH = "PandaWave:Search"
        const val AUTH = "PandaWave:Auth"
        const val ACCOUNT = "PandaWave:Account"
        const val MEDIA = "PandaWave:Media"
        const val PLAYER = "PandaWave:Player"
        const val APP_SHELL = "PandaWave:AppShell"
        const val HISTORY = "PandaWave:History"
    }

    @Volatile
    private var sink: PandaLogSink = AndroidPandaLogSink

    @Volatile
    private var runInline: Boolean = false

    @Volatile
    private var debuggable: Boolean = false

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pw-log").apply { isDaemon = true }
    }

    fun setDebuggable(enabled: Boolean) {
        debuggable = enabled
    }

    fun v(tag: String, message: () -> String) = log(Log.VERBOSE, tag, message)

    fun d(tag: String, message: () -> String) = log(Log.DEBUG, tag, message)

    fun i(tag: String, message: () -> String) = log(Log.INFO, tag, message)

    fun w(tag: String, throwable: Throwable? = null, message: () -> String) =
        log(Log.WARN, tag, message, throwable)

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) =
        log(Log.ERROR, tag, message, throwable)

    fun field(value: String?, maxChars: Int = MAX_FIELD_CHARS): String {
        if (value.isNullOrEmpty()) return ""
        return WHITESPACE_REGEX.replace(value, " ").trim().take(maxChars)
    }

    fun withSinkForTest(testSink: PandaLogSink): AutoCloseable {
        val previousSink = sink
        val previousInline = runInline
        val previousDebuggable = debuggable
        sink = testSink
        runInline = true
        debuggable = true
        return AutoCloseable {
            sink = previousSink
            runInline = previousInline
            debuggable = previousDebuggable
        }
    }

    private fun log(
        priority: Int,
        tag: String,
        message: () -> String,
        throwable: Throwable? = null,
    ) {
        if (!shouldEmit(priority, tag)) return
        enqueue {
            val formatted = runCatching { PandaLogRedactor.redact(message()) }
                .getOrElse { "log_format_failed" }
            sink.println(priority, tag, formatted, throwable)
        }
    }

    private fun shouldEmit(priority: Int, tag: String): Boolean {
        if (priority >= Log.INFO) return true
        if (debuggable) return true
        return runCatching { Log.isLoggable(tag, priority) }.getOrDefault(false)
    }

    private fun enqueue(work: () -> Unit) {
        if (runInline) {
            work()
            return
        }
        val onMain = runCatching {
            Looper.myLooper() != null && Looper.myLooper() == Looper.getMainLooper()
        }.getOrDefault(false)
        if (onMain) {
            executor.execute(work)
        } else {
            work()
        }
    }
}

internal object PandaLogRedactor {
    fun redact(message: String): String {
        val withoutQueries = QUERY_URL_REGEX.replace(message) { match ->
            val url = match.value
            val queryIndex = url.indexOf('?')
            url.substring(0, queryIndex) + "?[REDACTED]"
        }
        val withoutEmails = EMAIL_REGEX.replace(withoutQueries, "[REDACTED]")
        return SENSITIVE_ASSIGNMENT_REGEX.replace(withoutEmails, "$1[REDACTED]")
    }
}

private object AndroidPandaLogSink : PandaLogSink {
    override fun println(priority: Int, tag: String, message: String, throwable: Throwable?) {
        runCatching {
            if (throwable == null) {
                Log.println(priority, tag, message)
            } else {
                Log.println(priority, tag, message + '\n' + Log.getStackTraceString(throwable))
            }
        }
    }
}

private val WHITESPACE_REGEX = Regex("\\s+")
private val QUERY_URL_REGEX = Regex("https?://[^\\s]+\\?[^\\s]*", RegexOption.IGNORE_CASE)
private val EMAIL_REGEX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
private val SENSITIVE_ASSIGNMENT_REGEX = Regex(
    "(?i)\\b(password|passwd|token|authorization|apikey|api_key|secret)\\s*[=:]\\s*[^\\s]+",
)
private const val MAX_FIELD_CHARS = 80
