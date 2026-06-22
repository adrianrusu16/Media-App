package com.adrianrusu.pandawave.core.automotive.ux

/**
 * Observes the current driver-distraction restriction state.
 */
interface AutomotiveUxRestrictionObserver : AutoCloseable {
    fun current(): AutomotiveUxRestrictions

    fun start(onChanged: (AutomotiveUxRestrictions) -> Unit)
}
