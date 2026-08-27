package com.adrianrusu.pandawave.core.preferences

import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceRepository
import com.adrianrusu.pandawave.core.model.theme.ThemePreferenceState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineThemePreference
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
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
    fun `starting anonymous coordinator hydrates engine from datastore`() = runTest {
        val repository = RecordingThemePreferenceRepository(PandaWaveThemePreference.ForestTechDark)
        val engine = RecordingEngineGateway()
        val coordinator = DefaultThemePreferenceCoordinator(repository, engine, backgroundScope)

        coordinator.start()
        advanceUntilIdle()

        assertEquals(EngineCommand.TYPE_HYDRATE_THEME_PREFERENCE, engine.commands.single().type)
        assertTrue(engine.commands.single().payload.orEmpty().contains("forest_tech_dark"))
    }

    @Test
    fun `starting authenticated coordinator hydrates cache then loads remote preferences`() = runTest {
        val repository = RecordingThemePreferenceRepository(PandaWaveThemePreference.ForestTechDark)
        val engine = RecordingEngineGateway(authenticatedSnapshot())
        val coordinator = DefaultThemePreferenceCoordinator(repository, engine, backgroundScope)

        coordinator.start()
        advanceUntilIdle()

        assertEquals(
            listOf(
                EngineCommand.TYPE_HYDRATE_THEME_PREFERENCE,
                EngineCommand.TYPE_LOAD_PROFILE_PREFERENCES
            ),
            engine.commands.map(EngineCommand::type)
        )
    }

    @Test
    fun `authenticated selection is sent to remote profile before cache projection`() = runTest {
        val repository = RecordingThemePreferenceRepository(PandaWaveThemePreference.SystemDefault)
        val engine = RecordingEngineGateway(authenticatedSnapshot())
        val coordinator = DefaultThemePreferenceCoordinator(repository, engine, backgroundScope)
        coordinator.start()
        advanceUntilIdle()
        engine.commands.clear()

        coordinator.select(PandaWaveThemePreference.BambooGroveLight)

        assertEquals(PandaWaveThemePreference.SystemDefault, repository.currentPreference())
        assertEquals(0, repository.writeCount)
        assertEquals(EngineCommand.TYPE_UPDATE_PROFILE_PREFERENCES, engine.commands.single().type)
        assertEquals(
            """{"version":1,"values":{"theme":"bamboo_grove_light"}}""",
            engine.commands.single().payload
        )
    }

    @Test
    fun `anonymous selection stays local and durable`() = runTest {
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
        val engine = RecordingEngineGateway(authenticatedSnapshot())
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
        val engine = RecordingEngineGateway(authenticatedSnapshot())
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

    private fun authenticatedSnapshot(): EngineSnapshot = EngineSnapshot.idle(0L).copy(
        authState = EngineAuthState(EngineAuthState.AUTHENTICATED)
    )
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

private class RecordingEngineGateway(initialSnapshot: EngineSnapshot = EngineSnapshot.idle(0L)) : EngineGateway {
    val commands = mutableListOf<EngineCommand>()
    private val snapshotListeners = mutableListOf<(EngineSnapshot) -> Unit>()
    private var currentSnapshot = initialSnapshot

    override fun snapshot(): EngineSnapshot = currentSnapshot

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
