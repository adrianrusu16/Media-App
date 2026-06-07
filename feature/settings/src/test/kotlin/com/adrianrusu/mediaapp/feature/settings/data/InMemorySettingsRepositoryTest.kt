package com.adrianrusu.mediaapp.feature.settings.data

import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.feature.settings.domain.SettingsIntent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemorySettingsRepositoryTest {
    @Test
    fun appliesUxRestrictionsToSettingsState() {
        val observer = RecordingUxRestrictionObserver()
        val repository = InMemorySettingsRepository(uxRestrictionObserver = observer)

        repository.start()
        observer.emit(
            AutomotiveUxRestrictions(
                source = AutomotiveUxRestrictions.Source.AutomotivePlatform,
                requiresDistractionOptimization = true,
                activeRestrictions = AutomotiveUxRestrictions.NO_RESTRICTIONS,
                maxContentDepth = 1,
                maxCumulativeContentItems = 2,
                maxRestrictedStringLength = 24
            )
        )

        assertTrue(repository.state.value.restriction.isRestricted)
        assertFalse(repository.state.value.controlsEnabled)
    }

    @Test
    fun dispatchesSettingIntentsWhenUnrestricted() {
        val repository = InMemorySettingsRepository(
            uxRestrictionObserver = RecordingUxRestrictionObserver()
        )

        repository.dispatch(SettingsIntent.TogglePersonalization)

        assertTrue(repository.state.value.personalizationEnabled)
    }
}

private class RecordingUxRestrictionObserver : AutomotiveUxRestrictionObserver {
    private var listener: ((AutomotiveUxRestrictions) -> Unit)? = null

    override fun current(): AutomotiveUxRestrictions =
        AutomotiveUxRestrictions.unrestricted(AutomotiveUxRestrictions.Source.NotAutomotive)

    override fun start(onChanged: (AutomotiveUxRestrictions) -> Unit) {
        listener = onChanged
        onChanged(current())
    }

    fun emit(restrictions: AutomotiveUxRestrictions) {
        listener?.invoke(restrictions)
    }

    override fun close() {
        listener = null
    }
}
