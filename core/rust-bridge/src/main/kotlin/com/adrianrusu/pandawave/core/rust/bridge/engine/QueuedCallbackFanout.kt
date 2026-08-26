package com.adrianrusu.pandawave.core.rust.bridge.engine

/**
 * Serializes nested fan-out so [android.os.RemoteCallbackList.beginBroadcast]
 * is never re-entered from a listener callback.
 */
internal class QueuedCallbackFanout<T> {
    private var broadcasting = false
    private val pending = ArrayDeque<T>()

    fun emit(value: T, deliver: (T) -> Unit) {
        pending.addLast(value)
        if (broadcasting) return
        broadcasting = true
        try {
            while (true) {
                val next = pending.removeFirstOrNull() ?: break
                deliver(next)
            }
        } finally {
            broadcasting = false
        }
    }
}
