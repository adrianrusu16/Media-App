package com.adrianrusu.pandawave.feature.auth.presentation

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAccount
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthSession
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineAuthGateway
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.feature.auth.domain.AuthFormMode
import com.adrianrusu.pandawave.feature.auth.domain.AuthFormPhase
import com.adrianrusu.pandawave.feature.auth.domain.AuthUiEffect
import com.adrianrusu.pandawave.feature.auth.domain.LogoutPhase
import com.adrianrusu.pandawave.feature.auth.domain.ProfileAccountUi
import com.adrianrusu.pandawave.feature.auth.domain.ProfileAuthEffect
import com.adrianrusu.pandawave.feature.auth.domain.ProfileAuthNotice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthFlowControllerTest {
    @Test
    fun `login command runs once wipes password and closes only on authenticated snapshot`() = runTest {
        val gateway = RecordingAuthEngineGateway()
        val effects = mutableListOf<AuthUiEffect>()
        val password = "secret".encodeToByteArray()
        val controller = AuthFlowController(
            mode = AuthFormMode.LOGIN,
            authGateway = gateway,
            engineGateway = gateway,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            deviceLabel = "Panda Emulator",
            onEffect = effects::add
        )

        controller.submit("driver@example.com", password)
        controller.submit("driver@example.com", "duplicate".encodeToByteArray())
        runCurrent()

        assertEquals(1, gateway.loginCalls)
        assertEquals(AuthFormPhase.FINISHING_SIGN_IN, controller.state.value.phase)
        assertContentEquals(ByteArray(password.size), password)
        assertTrue(AuthUiEffect.Close !in effects)

        gateway.pushAuth(authenticatedState())
        runCurrent()

        assertEquals(AuthUiEffect.Close, effects.last())
    }

    @Test
    fun `restriction cancels an unstarted command wipes bytes and never replays`() = runTest {
        val gateway = RecordingAuthEngineGateway()
        val effects = mutableListOf<AuthUiEffect>()
        val password = "secret".encodeToByteArray()
        val controller = AuthFlowController(
            mode = AuthFormMode.LOGIN,
            authGateway = gateway,
            engineGateway = gateway,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            deviceLabel = "Panda Emulator",
            onEffect = effects::add
        )

        controller.submit("driver@example.com", password)
        controller.cancelOrRestrict()
        runCurrent()

        assertEquals(0, gateway.loginCalls)
        assertContentEquals(ByteArray(password.size), password)
        assertEquals(listOf(AuthUiEffect.Close), effects.filterIsInstance<AuthUiEffect.Close>())
    }

    @Test
    fun `unavailable auth rejects submission without leaving the form submitting`() = runTest {
        val gateway = RecordingAuthEngineGateway(authAvailable = false)
        val password = "secret".encodeToByteArray()
        val controller = AuthFlowController(
            mode = AuthFormMode.LOGIN,
            authGateway = gateway,
            engineGateway = gateway,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            deviceLabel = "Panda Emulator",
            onEffect = { }
        )

        controller.submit("driver@example.com", password)
        runCurrent()

        assertEquals(0, gateway.loginCalls)
        assertEquals(AuthFormPhase.IDLE, controller.state.value.phase)
        assertContentEquals(ByteArray(password.size), password)
    }

    @Test
    fun `registration and resend each submit once`() = runTest {
        val gateway = RecordingAuthEngineGateway(
            registerResult = EngineAuthOperationResult.accepted(),
            resendResult = EngineAuthOperationResult.accepted()
        )
        val controller = AuthFlowController(
            mode = AuthFormMode.REGISTER,
            authGateway = gateway,
            engineGateway = gateway,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            deviceLabel = "Panda Emulator",
            onEffect = { }
        )

        controller.submit("driver@example.com", "secret".encodeToByteArray())
        runCurrent()
        controller.resend("driver@example.com")
        controller.resend("driver@example.com")
        runCurrent()

        assertEquals(1, gateway.registerCalls)
        assertEquals(1, gateway.resendCalls)
        assertEquals(AuthFormPhase.VERIFICATION_PENDING, controller.state.value.phase)
        assertTrue(!controller.state.value.resendInFlight)
    }

    @Test
    fun `registration closes only on authenticated snapshot`() = runTest {
        val gateway = RecordingAuthEngineGateway(
            registerResult = EngineAuthOperationResult.accepted()
        )
        val effects = mutableListOf<AuthUiEffect>()
        val controller = AuthFlowController(
            mode = AuthFormMode.REGISTER,
            authGateway = gateway,
            engineGateway = gateway,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            deviceLabel = "Panda Emulator",
            onEffect = effects::add
        )

        controller.submit("driver@example.com", "secret".encodeToByteArray())
        runCurrent()

        assertEquals(AuthFormPhase.VERIFICATION_PENDING, controller.state.value.phase)
        assertTrue(AuthUiEffect.Close !in effects)

        gateway.pushAuth(authenticatedState())
        runCurrent()

        assertEquals(AuthUiEffect.Close, effects.last())
        assertEquals(AuthFormPhase.IDLE, controller.state.value.phase)
    }

    @Test
    fun `profile logout handles snapshot before remote ambiguity result`() = runTest {
        val gateway = RecordingAuthEngineGateway(initialAuth = authenticatedState())
        gateway.onLogout = {
            gateway.pushAuth(EngineAuthState.anonymous())
            EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_TRANSPORT)
        }
        val effects = mutableListOf<ProfileAuthEffect>()
        val controller = ProfileAuthController(
            authGateway = gateway,
            engineGateway = gateway,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            snapshotTimeoutMillis = 1_000,
            onEffect = effects::add
        )

        controller.logout()
        runCurrent()

        assertEquals(1, gateway.logoutCalls)
        assertEquals(ProfileAccountUi.Anonymous, controller.state.value.account)
        assertEquals(LogoutPhase.IDLE, controller.state.value.logoutPhase)
        assertEquals(ProfileAuthNotice.REMOTE_LOGOUT_UNCONFIRMED, effects.last())
    }

    @Test
    fun `profile normalizes after confirmed clear when snapshot delivery is interrupted`() = runTest {
        val gateway = RecordingAuthEngineGateway(initialAuth = authenticatedState())
        gateway.onLogout = { EngineAuthOperationResult.anonymous() }
        val effects = mutableListOf<ProfileAuthEffect>()
        val controller = ProfileAuthController(
            authGateway = gateway,
            engineGateway = gateway,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            snapshotTimeoutMillis = 1_000,
            onEffect = effects::add
        )

        controller.logout()
        runCurrent()
        assertEquals(LogoutPhase.AWAITING_ANONYMOUS_SNAPSHOT, controller.state.value.logoutPhase)

        advanceTimeBy(1_001)
        runCurrent()

        assertEquals(ProfileAccountUi.Anonymous, controller.state.value.account)
        assertEquals(ProfileAuthNotice.REMOTE_LOGOUT_UNCONFIRMED, effects.last())
    }

    @Test
    fun `unavailable bridge does not leave logout submitting`() = runTest {
        val gateway = RecordingAuthEngineGateway(
            initialAuth = authenticatedState(),
            authAvailable = false
        )
        val controller = ProfileAuthController(
            authGateway = gateway,
            engineGateway = gateway,
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            snapshotTimeoutMillis = 1_000,
            onEffect = { }
        )

        controller.logout()
        runCurrent()

        assertEquals(0, gateway.logoutCalls)
        assertEquals(LogoutPhase.IDLE, controller.state.value.logoutPhase)
    }
}

private class RecordingAuthEngineGateway(
    initialAuth: EngineAuthState = EngineAuthState.anonymous(),
    private val authAvailable: Boolean = true,
    private val loginResult: EngineAuthOperationResult = EngineAuthOperationResult.authenticated(),
    private val registerResult: EngineAuthOperationResult = EngineAuthOperationResult.accepted(),
    private val resendResult: EngineAuthOperationResult = EngineAuthOperationResult.accepted()
) : EngineAuthGateway, EngineGateway {
    private val authAvailabilityListeners = mutableSetOf<(Boolean) -> Unit>()
    private val snapshotListeners = mutableSetOf<(EngineSnapshot) -> Unit>()
    private var snapshot = EngineSnapshot.idle(nowMillis = 1L).copy(authState = initialAuth)

    var loginCalls = 0
        private set
    var registerCalls = 0
        private set
    var resendCalls = 0
        private set
    var logoutCalls = 0
        private set
    var onLogout: () -> EngineAuthOperationResult = { EngineAuthOperationResult.anonymous() }

    override val isAuthAvailable: Boolean = authAvailable

    override fun observeAuthAvailability(listener: (Boolean) -> Unit): AutoCloseable {
        authAvailabilityListeners += listener
        listener(authAvailable)
        return AutoCloseable { authAvailabilityListeners -= listener }
    }

    override fun loginPassword(email: String, password: ByteArray, deviceLabel: String): EngineAuthOperationResult {
        loginCalls += 1
        password.fill(0)
        return loginResult
    }

    override fun registerPassword(email: String, password: ByteArray): EngineAuthOperationResult {
        registerCalls += 1
        password.fill(0)
        return registerResult
    }

    override fun resendVerification(email: String): EngineAuthOperationResult {
        resendCalls += 1
        return resendResult
    }

    override fun verifyEmail(verificationToken: ByteArray, deviceLabel: String): EngineAuthOperationResult =
        EngineAuthOperationResult.unavailable()

    override fun logout(): EngineAuthOperationResult {
        logoutCalls += 1
        return onLogout()
    }

    fun pushAuth(authState: EngineAuthState) {
        snapshot = snapshot.copy(authState = authState)
        snapshotListeners.toList().forEach { it(snapshot) }
    }

    override fun snapshot(): EngineSnapshot = snapshot

    override fun browseResult(index: Int): EngineCatalogItem? = null

    override fun searchResult(index: Int): EngineCatalogItem? = null

    override fun dispatch(command: EngineCommand): EngineDispatchResult = error("Not used")

    override fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult = error("Not used")

    override fun observeSnapshots(listener: (EngineSnapshot) -> Unit): AutoCloseable {
        snapshotListeners += listener
        listener(snapshot)
        return AutoCloseable { snapshotListeners -= listener }
    }

    override fun observeEngineEvents(listener: (EngineEvent) -> Unit): AutoCloseable = AutoCloseable { }
}

private fun authenticatedState(): EngineAuthState = EngineAuthState(
    state = EngineAuthState.AUTHENTICATED,
    account = EngineAccount("account", "driver@example.com", "active", 1L),
    session = EngineAuthSession("session", "Panda Emulator", 2L, 3L, 4L, true)
)
