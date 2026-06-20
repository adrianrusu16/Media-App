package com.adrianrusu.mediaapp.core.preferences

import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.mediaapp.core.model.theme.ThemePreferenceRepository
import com.adrianrusu.mediaapp.core.model.theme.ThemePreferenceState
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineThemePreference
import com.adrianrusu.mediaapp.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.EngineGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultThemePreferenceCoordinatorTest {
    @Test
    fun `starting coordinator hydrates engine from datastore`() = runTest {
        val repository = RecordingThemePreferenceRepository(PandaWaveThemePreference.ForestTechDark)
        val engine = RecordingEngineGateway()
        val coordinator = DefaultThemePreferenceCoordinator(repository, engine, backgroundScope)

        coordinator.start()
        advanceUntilIdle()

        assertEquals(EngineCommand.TYPE_HYDRATE_THEME_PREFERENCE, engine.commands.single().type)
        assertTrue(engine.commands.single().payload.orEmpty().contains("forest_tech_dark"))
    }

    @Test
    fun `local selection is durable before engine dispatch`() = runTest {
        val repository = RecordingThemePreferenceRepository(PandaWaveThemePreference.SystemDefault)
        val engine = RecordingEngineGateway()
        val coordinator = DefaultThemePreferenceCoordinator(repository, engine, backgroundScope)
        coordinator.start()
        advanceUntilIdle()
        engine.commands.clear()

        coordinator.select(PandaWaveThemePreference.BambooGroveLight)

        assertEquals(PandaWaveThemePreference.BambooGroveLight, repository.currentPreference())
        assertEquals(EngineCommand.TYPE_SET_THEME_PREFERENCE, engine.commands.single().type)
    }

    @Test
    fun `accepted remote theme is persisted locally once`() = runTest {
        val repository = RecordingThemePreferenceRepository(PandaWaveThemePreference.BambooGroveLight)
        val engine = RecordingEngineGateway()
        val coordinator = DefaultThemePreferenceCoordinator(repository, engine, backgroundScope)
        coordinator.start()
        advanceUntilIdle()

        val remote = engine.snapshot().copy(
            themePreference = EngineThemePreference(
                themeId = EngineThemePreference.THEME_FOREST_TECH_DARK,
                source = EngineThemePreference.SOURCE_REMOTE_PROFILE,
                revision = 3,
                initialized = true
            )
        )
        engine.emit(remote)
        engine.emit(remote)
        advanceUntilIdle()

        assertEquals(PandaWaveThemePreference.ForestTechDark, repository.currentPreference())
        assertEquals(1, repository.writeCount)
    }

    @Test
    fun `invalid remote theme is ignored`() = runTest {
        val repository = RecordingThemePreferenceRepository(PandaWaveThemePreference.BambooGroveLight)
        val engine = RecordingEngineGateway()
        val coordinator = DefaultThemePreferenceCoordinator(repository, engine, backgroundScope)
        coordinator.start()
        advanceUntilIdle()

        engine.emit(
            engine.snapshot().copy(
                themePreference = EngineThemePreference(
                    themeId = "not_a_theme",
                    source = EngineThemePreference.SOURCE_REMOTE_PROFILE,
                    revision = 4,
                    initialized = true
                )
            )
        )
        advanceUntilIdle()

        assertEquals(PandaWaveThemePreference.BambooGroveLight, repository.currentPreference())
        assertEquals(0, repository.writeCount)
    }
}

private class RecordingThemePreferenceRepository(initial: PandaWaveThemePreference) : ThemePreferenceRepository {
    private val mutableState = MutableStateFlow<ThemePreferenceState>(ThemePreferenceState.Ready(initial))
    override val state: StateFlow<ThemePreferenceState> = mutableState.asStateFlow()
    var writeCount: Int = 0
        private set

    override suspend fun setPreference(preference: PandaWaveThemePreference) {
        writeCount += 1
        mutableState.value = ThemePreferenceState.Ready(preference)
    }

    fun currentPreference(): PandaWaveThemePreference = (state.value as ThemePreferenceState.Ready).preference
}

private class RecordingEngineGateway : EngineGateway {
    val commands = mutableListOf<EngineCommand>()
    private val snapshotListeners = mutableListOf<(EngineSnapshot) -> Unit>()
    private var currentSnapshot = EngineSnapshot.idle(0L)

    override fun snapshot(): EngineSnapshot = currentSnapshot

    override fun browseResult(index: Int): EngineCatalogItem? = null

    override fun searchResult(index: Int): EngineCatalogItem? = null

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commands += command
        return EngineDispatchResult(
            snapshot = currentSnapshot,
            event = EngineEvent(EngineEvent.TYPE_COMMAND_APPLIED, command.type),
            effects = emptyList<EngineEffect>()
        )
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult = EngineDispatchResult(
        snapshot = currentSnapshot,
        event = EngineEvent(EngineEvent.TYPE_PLATFORM_EVENT_APPLIED, event.type)
    )

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        snapshotListeners += listener
        listener(currentSnapshot)
        return AutoCloseable { snapshotListeners -= listener }
    }

    override fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable = AutoCloseable { }

    fun emit(snapshot: EngineSnapshot) {
        currentSnapshot = snapshot
        snapshotListeners.toList().forEach { listener -> listener(snapshot) }
    }
}
