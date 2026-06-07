package com.adrianrusu.mediaapp.appshell.data

import com.adrianrusu.mediaapp.appshell.domain.AppShellIntent
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictionObserver
import com.adrianrusu.mediaapp.core.automotive.ux.AutomotiveUxRestrictions
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.mediaapp.core.rust.bridge.engine.PandaEngineFactory
import com.adrianrusu.mediaapp.core.rust.bridge.engine.RustEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryAppShellRepositoryTest {
    @Test
    fun playbackIntentDispatchesThroughEngineSnapshot() {
        val repository = InMemoryAppShellRepository(
            uxRestrictionObserver = FakeAutomotiveUxRestrictionObserver(),
            engine = PandaEngineFactory.createFake()
        )

        repository.start()

        assertFalse(repository.state.value.miniPlayer.isPlaying)

        repository.dispatch(AppShellIntent.TogglePlayback)

        assertTrue(repository.state.value.miniPlayer.isPlaying)

        repository.dispatch(AppShellIntent.TogglePlayback)

        assertFalse(repository.state.value.miniPlayer.isPlaying)
    }

    @Test
    fun skipIntentsDispatchThroughEngineBoundary() {
        val engine = RecordingRustEngine()
        val repository = InMemoryAppShellRepository(
            uxRestrictionObserver = FakeAutomotiveUxRestrictionObserver(),
            engine = engine
        )

        repository.start()
        repository.dispatch(AppShellIntent.SkipPrevious)
        repository.dispatch(AppShellIntent.SkipNext)

        assertEquals(
            listOf(
                EngineCommand.TYPE_BOOTSTRAP,
                EngineCommand.TYPE_SKIP_PREVIOUS,
                EngineCommand.TYPE_SKIP_NEXT
            ),
            engine.commandTypes
        )
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
                maxRestrictedStringLength = 24
            )
        )
        val repository = InMemoryAppShellRepository(
            uxRestrictionObserver = observer,
            engine = PandaEngineFactory.createFake()
        )

        repository.start()

        assertTrue(repository.state.value.restriction.isRestricted)
        assertTrue(repository.state.value.miniPlayer.isRestricted)
    }
}

private data class FakeAutomotiveUxRestrictionObserver(
    val restrictions: AutomotiveUxRestrictions =
        AutomotiveUxRestrictions.unrestricted(
            AutomotiveUxRestrictions.Source.NotAutomotive
        )
) : AutomotiveUxRestrictionObserver {
    override fun current(): AutomotiveUxRestrictions = restrictions

    override fun start(onChanged: (AutomotiveUxRestrictions) -> Unit) {
        onChanged(restrictions)
    }

    override fun close() = Unit
}

private class RecordingRustEngine : RustEngine {
    val commandTypes = mutableListOf<String>()
    private var currentSnapshot = EngineSnapshot.idle(nowMillis = 100)

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commandTypes += command.type
        currentSnapshot = currentSnapshot.copy(updatedAtEpochMillis = currentSnapshot.updatedAtEpochMillis + 1)
        return EngineDispatchResult(
            snapshot = currentSnapshot,
            event = EngineEvent(
                type = EngineEvent.TYPE_COMMAND_APPLIED,
                message = command.type
            )
        )
    }
}
