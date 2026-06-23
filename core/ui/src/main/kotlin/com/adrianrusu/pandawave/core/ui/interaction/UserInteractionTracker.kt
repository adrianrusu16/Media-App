package com.adrianrusu.pandawave.core.ui.interaction

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

fun interface MonotonicClock {
    fun nowMillis(): Long
}

class SystemMonotonicClock : MonotonicClock {
    override fun nowMillis(): Long = SystemClock.elapsedRealtime()
}

class UserInteractionTracker {
    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()

    fun recordInteraction() {
        mutableRevision.update { current -> current + 1L }
    }
}
