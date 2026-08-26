package com.adrianrusu.pandawave.feature.profile.data

import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAccount
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthSession
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
import com.adrianrusu.pandawave.feature.profile.domain.AccountSessionsState
import com.adrianrusu.pandawave.feature.profile.domain.ProfileState
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PandaEngineProfileRepositoryTest {
    @Test
    fun `authenticated start fetches profile and preferences through engine commands`() {
        val engine = RecordingEngineGateway(authenticatedSnapshot())
        val repository = PandaEngineProfileRepository(engine, Executor { it.run() })

        repository.start()

        assertEquals(
            listOf(
                EngineCommand.TYPE_GET_PROFILE,
                EngineCommand.TYPE_LOAD_PROFILE_PREFERENCES,
                EngineCommand.TYPE_GET_ACCOUNT,
                EngineCommand.TYPE_LIST_DEVICE_SESSIONS
            ),
            engine.commands.map(EngineCommand::type)
        )
    }

    @Test
    fun `authenticated transition hydrates account and sessions once per exact identity`() {
        val engine = RecordingEngineGateway(EngineSnapshot.idle(1L))
        val repository = PandaEngineProfileRepository(engine, Executor { it.run() })
        repository.start()

        engine.emit(authenticatedSnapshot())
        engine.emit(authenticatedSnapshot())

        assertEquals(1, engine.commands.count { it.type == EngineCommand.TYPE_GET_ACCOUNT })
        assertEquals(1, engine.commands.count { it.type == EngineCommand.TYPE_LIST_DEVICE_SESSIONS })
    }

    @Test
    fun `protected account and session page project without credentials`() {
        val snapshot = authenticatedSnapshot().copy(
            protectedAccount = account(),
            deviceSessions = listOf(session()),
            deviceSessionsCount = 1,
            hasDeviceSessionsNextPage = true
        )
        val repository = PandaEngineProfileRepository(RecordingEngineGateway(snapshot), Executor { it.run() })

        repository.start()

        val ready = assertIs<AccountSessionsState.Ready>(repository.accountSessionsState.value)
        assertEquals("account-1", ready.account.id)
        assertEquals(listOf("session-1"), ready.sessions.map { it.id })
        assertTrue(ready.hasNextPage)
    }

    @Test
    fun `protected sessions clear and rehydrate for every exact identity transition`() {
        val initial = authenticatedSnapshot().copy(
            protectedAccount = account(),
            deviceSessions = listOf(session()),
            deviceSessionsCount = 1
        )
        val engine = RecordingEngineGateway(initial)
        val repository = PandaEngineProfileRepository(engine, Executor { it.run() })
        repository.start()
        engine.commands.clear()

        for (transition in listOf(
            authenticatedSnapshot("account-2", "session-1", true),
            authenticatedSnapshot("account-1", "session-2", true)
        )) {
            engine.emit(transition)
            assertIs<AccountSessionsState.Loading>(repository.accountSessionsState.value)
        }

        assertEquals(2, engine.commands.count { it.type == EngineCommand.TYPE_GET_ACCOUNT })
        assertEquals(2, engine.commands.count { it.type == EngineCommand.TYPE_LIST_DEVICE_SESSIONS })
    }

    @Test
    fun `current false and logout clear protected session state`() {
        val engine = RecordingEngineGateway(
            authenticatedSnapshot().copy(protectedAccount = account(), deviceSessions = listOf(session()))
        )
        val repository = PandaEngineProfileRepository(engine, Executor { it.run() })
        repository.start()

        engine.emit(authenticatedSnapshot(current = false))
        assertIs<AccountSessionsState.SignedOut>(repository.accountSessionsState.value)
        engine.emit(EngineSnapshot.idle(2L))
        assertIs<AccountSessionsState.SignedOut>(repository.accountSessionsState.value)
    }

    @Test
    fun `typed protected failure projects before account hydration`() {
        val engine = RecordingEngineGateway(
            authenticatedSnapshot().copy(
                protectedAccount = null,
                hasError = true,
                errorType = EngineSnapshot.ERROR_NETWORK
            )
        )
        val repository = PandaEngineProfileRepository(engine, Executor { it.run() })

        repository.start()

        val failure = assertIs<AccountSessionsState.Failure>(repository.accountSessionsState.value)
        assertEquals(EngineSnapshot.ERROR_NETWORK, failure.errorType)
        assertTrue(failure.retryable)
    }

    @Test
    fun `gateway unavailable protected actions clear pending state into typed failure`() {
        val engine = RecordingEngineGateway(
            authenticatedSnapshot().copy(protectedAccount = account(), deviceSessions = listOf(session())),
            dispatchEventType = EngineEvent.TYPE_GATEWAY_UNAVAILABLE
        )
        val repository = PandaEngineProfileRepository(engine, Executor { it.run() })
        repository.start()

        repository.revokeDeviceSession("session-other")
        assertIs<AccountSessionsState.Failure>(repository.accountSessionsState.value)
        repository.deleteAccount()
        val failure = assertIs<AccountSessionsState.Failure>(repository.accountSessionsState.value)
        assertEquals(EngineSnapshot.ERROR_NETWORK, failure.errorType)
        assertTrue(failure.retryable)
    }

    @Test
    fun `all profile mutations are reachable with typed credential free payloads`() {
        val engine = RecordingEngineGateway(authenticatedSnapshot())
        val repository = PandaEngineProfileRepository(engine, Executor { it.run() })
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
        val repository = PandaEngineProfileRepository(engine, Executor { it.run() })
        repository.start()

        assertNull(assertIs<ProfileState.Ready>(repository.state.value).profile.displayName)

        engine.emit(authenticatedSnapshot().copy(profile = profile(displayName = "")))

        assertEquals("", assertIs<ProfileState.Ready>(repository.state.value).profile.displayName)
    }

    @Test
    fun `authenticated snapshot without a profile is an actionable missing state`() {
        val repository = PandaEngineProfileRepository(
            RecordingEngineGateway(authenticatedSnapshot()),
            Executor { it.run() }
        )

        repository.start()

        assertIs<ProfileState.Missing>(repository.state.value)
    }

    @Test
    fun `authenticated profile not found is an actionable missing state`() {
        val snapshot = authenticatedSnapshot().copy(
            profile = null,
            hasError = true,
            errorType = EngineSnapshot.ERROR_NOT_FOUND
        )
        val repository = PandaEngineProfileRepository(RecordingEngineGateway(snapshot), Executor { it.run() })

        repository.start()

        assertIs<ProfileState.Missing>(repository.state.value)
    }

    @Test
    fun `unavailable non replayable mutation exposes retry classification`() {
        val engine = RecordingEngineGateway(
            initialSnapshot = authenticatedSnapshot().copy(profile = profile("Driver")),
            dispatchEventType = EngineEvent.TYPE_GATEWAY_UNAVAILABLE
        )
        val repository = PandaEngineProfileRepository(engine, Executor { it.run() })
        repository.start()

        repository.updateDisplayName("Passenger")

        val failure = assertIs<ProfileState.Failure>(repository.state.value)
        assertTrue(failure.retryable)
        assertEquals(EngineSnapshot.ERROR_NETWORK, failure.errorType)
    }

    private fun authenticatedSnapshot(
        accountId: String = "account-1",
        sessionId: String = "session-1",
        current: Boolean = true
    ): EngineSnapshot = EngineSnapshot.idle(1L).copy(
        authState = EngineAuthState(
            EngineAuthState.AUTHENTICATED,
            account(accountId),
            session(sessionId, current)
        ),
        themePreference = EngineThemePreference(
            themeId = EngineThemePreference.THEME_FOREST_TECH_DARK,
            source = EngineThemePreference.SOURCE_REMOTE_PROFILE,
            revision = 2,
            initialized = true
        )
    )

    private fun account(id: String = "account-1") = EngineAccount(id, "driver@example.com", "active", 10)
    private fun session(id: String = "session-1", current: Boolean = true) =
        EngineAuthSession(id, "car", 20, 30, 40, current)

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

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult = EngineDispatchResult(
        snapshot = current,
        event = EngineEvent(EngineEvent.TYPE_PLATFORM_EVENT_APPLIED, event.type)
    )

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        listeners += listener
        listener(current)
        return AutoCloseable { listeners -= listener }
    }

    override fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable = AutoCloseable { }

    fun emit(snapshot: EngineSnapshot) {
        current = snapshot
        listeners.toList().forEach { it(snapshot) }
    }
}
