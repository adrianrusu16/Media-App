package com.adrianrusu.pandawave.feature.profile.data

import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineProfile
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineThemePreference
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.feature.profile.domain.ProfileState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PandaEngineProfileRepositoryTest {
    @Test
    fun `authenticated start fetches profile and preferences through engine commands`() {
        val engine = RecordingEngineGateway(authenticatedSnapshot())
        val repository = PandaEngineProfileRepository(engine)

        repository.start()

        assertEquals(
            listOf(
                EngineCommand.TYPE_GET_PROFILE,
                EngineCommand.TYPE_LOAD_PROFILE_PREFERENCES
            ),
            engine.commands.map(EngineCommand::type)
        )
    }

    @Test
    fun `all profile mutations are reachable with typed credential free payloads`() {
        val engine = RecordingEngineGateway(authenticatedSnapshot())
        val repository = PandaEngineProfileRepository(engine)
        repository.start()
        engine.commands.clear()

        repository.upsert(displayName = null)
        repository.updateDisplayName(displayName = "")
        repository.delete()
        repository.updateTheme(PandaWaveThemePreference.ForestTechDark)

        assertEquals(
            listOf(
                EngineCommand.TYPE_UPSERT_PROFILE,
                EngineCommand.TYPE_UPDATE_PROFILE,
                EngineCommand.TYPE_DELETE_PROFILE,
                EngineCommand.TYPE_UPDATE_PROFILE_PREFERENCES
            ),
            engine.commands.map(EngineCommand::type)
        )
        assertEquals("""{"version":1,"display_name":null}""", engine.commands[0].payload)
        assertEquals(
            """{"version":1,"update_display_name":true,"display_name":""}""",
            engine.commands[1].payload
        )
        assertEquals(
            """{"version":1,"values":{"theme":"forest_tech_dark"}}""",
            engine.commands[3].payload
        )
    }

    @Test
    fun `snapshot projection preserves absent display name distinctly from empty text`() {
        val engine = RecordingEngineGateway(
            authenticatedSnapshot().copy(profile = profile(displayName = null))
        )
        val repository = PandaEngineProfileRepository(engine)
        repository.start()

        assertNull(assertIs<ProfileState.Ready>(repository.state.value).profile.displayName)

        engine.emit(authenticatedSnapshot().copy(profile = profile(displayName = "")))

        assertEquals("", assertIs<ProfileState.Ready>(repository.state.value).profile.displayName)
    }

    @Test
    fun `authenticated snapshot without a profile is an actionable missing state`() {
        val repository = PandaEngineProfileRepository(RecordingEngineGateway(authenticatedSnapshot()))

        repository.start()

        assertIs<ProfileState.Missing>(repository.state.value)
    }
    @Test
    fun `unavailable non replayable mutation exposes retry classification`() {
        val engine = RecordingEngineGateway(
            initialSnapshot = authenticatedSnapshot().copy(profile = profile("Driver")),
            dispatchEventType = EngineEvent.TYPE_GATEWAY_UNAVAILABLE
        )
        val repository = PandaEngineProfileRepository(engine)
        repository.start()

        repository.updateDisplayName("Passenger")

        val failure = assertIs<ProfileState.Failure>(repository.state.value)
        assertTrue(failure.retryable)
        assertEquals(EngineSnapshot.ERROR_NETWORK, failure.errorType)
    }

    private fun authenticatedSnapshot(): EngineSnapshot = EngineSnapshot.idle(1L).copy(
        authState = EngineAuthState(EngineAuthState.AUTHENTICATED),
        themePreference = EngineThemePreference(
            themeId = EngineThemePreference.THEME_FOREST_TECH_DARK,
            source = EngineThemePreference.SOURCE_REMOTE_PROFILE,
            revision = 2,
            initialized = true
        )
    )

    private fun profile(displayName: String?): EngineProfile = EngineProfile(
        id = "profile-1",
        externalUserId = "account-1",
        displayName = displayName,
        createdAtEpochMillis = 100,
        updatedAtEpochMillis = 200
    )
}

private class RecordingEngineGateway(
    initialSnapshot: EngineSnapshot,
    private val dispatchEventType: String = EngineEvent.TYPE_COMMAND_APPLIED
) : EngineGateway {
    private var current = initialSnapshot
    private val listeners = mutableListOf<(EngineSnapshot) -> Unit>()
    val commands = mutableListOf<EngineCommand>()

    override fun snapshot(): EngineSnapshot = current
    override fun browseResult(index: Int): EngineCatalogItem? = null
    override fun searchResult(index: Int): EngineCatalogItem? = null

    override fun dispatch(command: EngineCommand): EngineDispatchResult {
        commands += command
        return EngineDispatchResult(
            snapshot = current,
            event = EngineEvent(dispatchEventType, command.type),
            effects = emptyList<EngineEffect>()
        )
    }

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult =
        EngineDispatchResult(
            snapshot = current,
            event = EngineEvent(EngineEvent.TYPE_PLATFORM_EVENT_APPLIED, event.type)
        )

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        listeners += listener
        listener(current)
        return AutoCloseable { listeners -= listener }
    }

    override fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable =
        AutoCloseable { }

    fun emit(snapshot: EngineSnapshot) {
        current = snapshot
        listeners.toList().forEach { it(snapshot) }
    }
}
