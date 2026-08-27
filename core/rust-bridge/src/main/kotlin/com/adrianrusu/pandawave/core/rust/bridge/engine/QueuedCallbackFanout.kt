package com.adrianrusu.pandawave.core.rust.bridge.engine

/**
 * Serializes fan-out so [android.os.RemoteCallbackList.beginBroadcast] is never
 * re-entered from a nested [emit] and is safe to call from multiple Binder threads.
 *
 * Queue mutations are lock-protected. [deliver] runs without holding the lock so
 * remote listener IPC cannot block other callers from enqueueing.
 */
internal class QueuedCallbackFanout<T> {
    private val lock = Any()
    private val pending = ArrayDeque<Pending<T>>()
    private var draining = false

    fun emit(value: T, deliver: (T) -> Unit) {
        synchronized(lock) {
            pending.addLast(Pending(value, deliver))
            if (draining) return
            draining = true
        }
        while (true) {
            val next = synchronized(lock) {
                pending.removeFirstOrNull() ?: run {
                    draining = false
                    null
                }
            } ?: return
            try {
                next.deliver(next.value)
            } catch (error: Throwable) {
                synchronized(lock) { draining = false }
                throw error
            }
        }
    }

    private class Pending<V>(
        val value: V,
        val deliver: (V) -> Unit
    )
}
