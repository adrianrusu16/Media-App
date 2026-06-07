package com.adrianrusu.mediaapp.appshell.data

import com.adrianrusu.mediaapp.appshell.domain.AppShellIntent
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.rust.bridge.engine.FakeRustEngineFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryAppShellRepositoryTest {
    @Test
    fun playbackIntentDispatchesThroughEngineSnapshot() {
        val repository = InMemoryAppShellRepository(
            uxRestrictionObserver = FakeAutomotiveUxRestrictionObserver(),
            engine = FakeRustEngineFactory.create(),
        )

        repository.start()

        assertFalse(repository.state.value.miniPlayer.isPlaying)

        repository.dispatch(AppShellIntent.TogglePlayback)

        assertTrue(repository.state.value.miniPlayer.isPlaying)

        repository.dispatch(AppShellIntent.TogglePlayback)

        assertFalse(repository.state.value.miniPlayer.isPlaying)
    }

    @Test
    fun restrictionsAreProjectedIntoMiniPlayerState() {
        val observer = FakeAutomotiveUxRestrictionObserver().copy(
            restrictions = AutomotiveUxRestrictions(
                source = AutomotiveUxRestrictions.Source.AutomotivePlatform,
                requiresDistractionOptimization = true,
                activeRestrictions = 1,
                maxContentDepth = 1,
                maxCumulativeContentItems = 6,
                maxRestrictedStringLength = 24,
            ),
        )
        val repository = InMemoryAppShellRepository(
            uxRestrictionObserver = observer,
            engine = FakeRustEngineFactory.create(),
        )

        repository.start()

        assertTrue(repository.state.value.restriction.isRestricted)
        assertTrue(repository.state.value.miniPlayer.isRestricted)
    }
}

private data class FakeAutomotiveUxRestrictionObserver(
    val restrictions: AutomotiveUxRestrictions =
        AutomotiveUxRestrictions.unrestricted(
            AutomotiveUxRestrictions.Source.NotAutomotive,
        ),
) : AutomotiveUxRestrictionObserver {
    override fun current(): AutomotiveUxRestrictions = restrictions

    override fun start(onChanged: (AutomotiveUxRestrictions) -> Unit) {
        onChanged(restrictions)
    }

    override fun close() = Unit
}
