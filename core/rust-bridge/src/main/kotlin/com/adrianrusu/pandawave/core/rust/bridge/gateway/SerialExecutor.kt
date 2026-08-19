package com.adrianrusu.pandawave.core.rust.bridge.gateway

import java.util.concurrent.Executor

/** Serializes tasks without prescribing which thread executes them. */
internal class SerialExecutor(
    private val delegate: Executor,
) : Executor {
    private val lock = Any()
    private val tasks = ArrayDeque<Runnable>()
    private var running = false

    override fun execute(command: Runnable) {
        val shouldSchedule = synchronized(lock) {
            tasks.addLast(command)
            if (running) {
                false
            } else {
                running = true
                true
            }
        }
        if (shouldSchedule) scheduleNext()
    }

    private fun scheduleNext() {
        val next = synchronized(lock) {
            tasks.removeFirstOrNull().also { task ->
                if (task == null) running = false
            }
        } ?: return

        try {
            delegate.execute {
                try {
                    next.run()
                } finally {
                    scheduleNext()
                }
            }
        } catch (error: RuntimeException) {
            synchronized(lock) { running = false }
            throw error
        }
    }
}
