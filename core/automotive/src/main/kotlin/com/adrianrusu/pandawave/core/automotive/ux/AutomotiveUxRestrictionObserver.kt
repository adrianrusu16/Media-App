package com.adrianrusu.pandawave.core.automotive.ux

/**
 * Observes the current driver-distraction restriction state.
 */
interface AutomotiveUxRestrictionObserver : AutoCloseable {
    fun current(): AutomotiveUxRestrictions

    fun start(onChanged: (AutomotiveUxRestrictions) -> Unit)

    companion object {
        val Unavailable: AutomotiveUxRestrictionObserver = object : AutomotiveUxRestrictionObserver {
            private val unrestricted = AutomotiveUxRestrictions.unrestricted(
                AutomotiveUxRestrictions.Source.NotAutomotive
            )

            override fun current(): AutomotiveUxRestrictions = unrestricted

            override fun start(onChanged: (AutomotiveUxRestrictions) -> Unit) {
                onChanged(unrestricted)
            }

            override fun close() = Unit
        }
    }
}
