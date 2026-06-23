package com.adrianrusu.pandawave.core.automotive.driving

interface AutomotiveDrivingStateObserver : AutoCloseable {
    fun current(): AutomotiveDrivingState

    fun start(onChanged: (AutomotiveDrivingState) -> Unit)

    companion object {
        val Unavailable: AutomotiveDrivingStateObserver = object : AutomotiveDrivingStateObserver {
            override fun current(): AutomotiveDrivingState = AutomotiveDrivingState.Unknown

            override fun start(onChanged: (AutomotiveDrivingState) -> Unit) {
                onChanged(AutomotiveDrivingState.Unknown)
            }

            override fun close() = Unit
        }
    }
}
