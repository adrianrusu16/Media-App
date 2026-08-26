package com.adrianrusu.pandawave.feature.auth.domain

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAccount
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthSession
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthUiReducerTest {
    @Test
    fun `login submits once and waits for authenticated snapshot before closing`() {
        val initial = AuthFormState.login()
        val submitting = AuthUiReducer.reduce(initial, AuthUiEvent.Submit)
        val duplicate = AuthUiReducer.reduce(submitting.state, AuthUiEvent.Submit)
        val commandDone = AuthUiReducer.reduce(
            duplicate.state,
            AuthUiEvent.CommandCompleted(EngineAuthOperationResult.authenticated())
        )

        assertEquals(listOf(AuthUiEffect.SubmitLogin), submitting.effects)
        assertTrue(duplicate.effects.isEmpty())
        assertEquals(AuthFormPhase.FINISHING_SIGN_IN, commandDone.state.phase)
        assertFalse(AuthUiEffect.Close in commandDone.effects)

        val authenticated = AuthUiReducer.reduce(
            commandDone.state,
            AuthUiEvent.SnapshotChanged(authenticatedState())
        )
        assertEquals(listOf(AuthUiEffect.Close), authenticated.effects)
        assertNull(authenticated.state.notice)
    }

    @Test
    fun `registration ambiguity enters generic pending state without account disclosure`() {
        val submitting = AuthUiReducer.reduce(AuthFormState.register(), AuthUiEvent.Submit).state
        val transition = AuthUiReducer.reduce(
            submitting,
            AuthUiEvent.CommandCompleted(
                EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_TRANSPORT)
            )
        )

        assertEquals(AuthFormPhase.VERIFICATION_PENDING, transition.state.phase)
        assertEquals(AuthNotice.REQUEST_UNCONFIRMED, transition.state.notice)
        assertTrue(transition.effects.isEmpty())
    }

    @Test
    fun `invalid input maps to a sanitized policy mismatch for login and registration`() {
        val invalidInput = AuthUiEvent.CommandCompleted(
            EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_INVALID_INPUT)
        )
        val login = AuthUiReducer.reduce(
            AuthUiReducer.reduce(AuthFormState.login(), AuthUiEvent.Submit).state,
            invalidInput
        )
        val registration = AuthUiReducer.reduce(
            AuthUiReducer.reduce(AuthFormState.register(), AuthUiEvent.Submit).state,
            invalidInput
        )

        assertEquals(AuthNotice.POLICY_MISMATCH, login.state.notice)
        assertEquals(AuthNotice.POLICY_MISMATCH, registration.state.notice)
    }

    @Test
    fun `login makes a Canopy connection failure explicit`() {
        val submitting = AuthUiReducer.reduce(AuthFormState.login(), AuthUiEvent.Submit).state

        val transition = AuthUiReducer.reduce(
            submitting,
            AuthUiEvent.CommandCompleted(
                EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_SERVICE_UNAVAILABLE)
            )
        )

        assertEquals(AuthFormPhase.IDLE, transition.state.phase)
        assertEquals(AuthNotice.CANOPY_UNREACHABLE, transition.state.notice)
    }

    @Test
    fun `registration accepted enables one resend at a time`() {
        val pending = AuthUiReducer.reduce(
            AuthUiReducer.reduce(AuthFormState.register(), AuthUiEvent.Submit).state,
            AuthUiEvent.CommandCompleted(EngineAuthOperationResult.accepted())
        ).state
        val resend = AuthUiReducer.reduce(pending, AuthUiEvent.Resend)
        val duplicate = AuthUiReducer.reduce(resend.state, AuthUiEvent.Resend)

        assertEquals(AuthFormPhase.VERIFICATION_PENDING, pending.phase)
        assertEquals(listOf(AuthUiEffect.ResendVerification), resend.effects)
        assertTrue(resend.state.resendInFlight)
        assertTrue(duplicate.effects.isEmpty())

        val verified = AuthUiReducer.reduce(pending, AuthUiEvent.SnapshotChanged(authenticatedState()))
        assertEquals(listOf(AuthUiEffect.Close), verified.effects)
        assertEquals(AuthFormPhase.IDLE, verified.state.phase)
    }

    @Test
    fun `cancellation closes without manufacturing success or replay`() {
        val submitting = AuthUiReducer.reduce(AuthFormState.login(), AuthUiEvent.Submit).state
        val cancelled = AuthUiReducer.reduce(submitting, AuthUiEvent.CancelOrRestricted)

        assertEquals(listOf(AuthUiEffect.Close), cancelled.effects)
        assertEquals(AuthFormPhase.IDLE, cancelled.state.phase)

        val lateSnapshot = AuthUiReducer.reduce(
            cancelled.state,
            AuthUiEvent.SnapshotChanged(authenticatedState())
        )
        assertEquals(listOf(AuthUiEffect.Close), lateSnapshot.effects)
        assertTrue(lateSnapshot.effects.none { it is AuthUiEffect.SubmitLogin })
    }

    @Test
    fun `observable form state has no password or credential fields`() {
        val names = AuthFormState::class.java.declaredFields.map { it.name.lowercase() }

        assertTrue(
            names.none { name ->
                name.contains("password") || name.contains("credential") || name.contains("token")
            }
        )
    }

    @Test
    fun `logout waits for anonymous snapshot and ignores stale authenticated snapshots`() {
        val initial = ProfileAuthUiState.from(authenticatedState())
        val submitting = ProfileAuthReducer.reduce(initial, ProfileAuthEvent.Logout)
        val stale = ProfileAuthReducer.reduce(
            submitting.state,
            ProfileAuthEvent.SnapshotChanged(authenticatedState())
        )
        val result = ProfileAuthReducer.reduce(
            stale.state,
            ProfileAuthEvent.LogoutCompleted(EngineAuthOperationResult.anonymous())
        )

        assertEquals(listOf(ProfileAuthEffect.SubmitLogout), submitting.effects)
        assertEquals(LogoutPhase.SUBMITTING, stale.state.logoutPhase)
        assertTrue(stale.state.account is ProfileAccountUi.Authenticated)
        assertEquals(LogoutPhase.AWAITING_ANONYMOUS_SNAPSHOT, result.state.logoutPhase)
        assertTrue(result.state.localClearConfirmed)

        val anonymous = ProfileAuthReducer.reduce(
            result.state,
            ProfileAuthEvent.SnapshotChanged(EngineAuthState.anonymous())
        )
        assertEquals(LogoutPhase.IDLE, anonymous.state.logoutPhase)
        assertEquals(ProfileAccountUi.Anonymous, anonymous.state.account)
    }

    @Test
    fun `confirmed local clear normalizes to anonymous if snapshot observation is interrupted`() {
        val initial = ProfileAuthUiState.from(authenticatedState())
        val submitting = ProfileAuthReducer.reduce(initial, ProfileAuthEvent.Logout).state
        val confirmedClear = ProfileAuthReducer.reduce(
            submitting,
            ProfileAuthEvent.LogoutCompleted(EngineAuthOperationResult.anonymous())
        ).state
        val timeout = ProfileAuthReducer.reduce(confirmedClear, ProfileAuthEvent.SnapshotTimeout)

        assertTrue(confirmedClear.localClearConfirmed)
        assertEquals(ProfileAccountUi.Anonymous, timeout.state.account)
        assertEquals(LogoutPhase.IDLE, timeout.state.logoutPhase)
        assertEquals(ProfileAuthNotice.REMOTE_LOGOUT_UNCONFIRMED, timeout.effects.single())
    }

    @Test
    fun `transport error without anonymous snapshot does not claim local clear`() {
        val submitting = ProfileAuthReducer.reduce(
            ProfileAuthUiState.from(authenticatedState()),
            ProfileAuthEvent.Logout
        ).state

        val result = ProfileAuthReducer.reduce(
            submitting,
            ProfileAuthEvent.LogoutCompleted(
                EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_TRANSPORT)
            )
        )

        assertFalse(result.state.localClearConfirmed)
        assertTrue(result.state.account is ProfileAccountUi.Authenticated)
        assertEquals(LogoutPhase.IDLE, result.state.logoutPhase)
        assertEquals(ProfileAuthNotice.LOGOUT_FAILED, result.effects.single())
    }

    @Test
    fun `anonymous snapshot arriving before logout result preserves remote warning`() {
        val submitting = ProfileAuthReducer.reduce(
            ProfileAuthUiState.from(authenticatedState()),
            ProfileAuthEvent.Logout
        ).state
        val snapshotFirst = ProfileAuthReducer.reduce(
            submitting,
            ProfileAuthEvent.SnapshotChanged(EngineAuthState.anonymous())
        )

        assertEquals(ProfileAccountUi.Anonymous, snapshotFirst.state.account)
        assertEquals(LogoutPhase.SUBMITTING, snapshotFirst.state.logoutPhase)

        val resultLater = ProfileAuthReducer.reduce(
            snapshotFirst.state,
            ProfileAuthEvent.LogoutCompleted(
                EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_TRANSPORT)
            )
        )
        assertEquals(LogoutPhase.IDLE, resultLater.state.logoutPhase)
        assertEquals(ProfileAuthNotice.REMOTE_LOGOUT_UNCONFIRMED, resultLater.effects.single())
    }

    @Test
    fun `storage failure cannot manufacture anonymous state`() {
        val initial = ProfileAuthUiState.from(authenticatedState())
        val submitting = ProfileAuthReducer.reduce(initial, ProfileAuthEvent.Logout).state
        val failed = ProfileAuthReducer.reduce(
            submitting,
            ProfileAuthEvent.LogoutCompleted(
                EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_SESSION_STORAGE)
            )
        )

        assertFalse(failed.state.localClearConfirmed)
        assertTrue(failed.state.account is ProfileAccountUi.Authenticated)
        assertEquals(LogoutPhase.IDLE, failed.state.logoutPhase)
        assertEquals(ProfileAuthNotice.LOGOUT_FAILED, failed.effects.single())
    }

    @Test
    fun `authenticated projection excludes internal account and session identifiers`() {
        val projection = ProfileAuthUiState.from(authenticatedState()).account
            as ProfileAccountUi.Authenticated

        assertEquals("driver@example.com", projection.email)
        assertEquals("active", projection.accountStatus)
        assertEquals("Panda Emulator", projection.deviceLabel)
        assertEquals(10L, projection.sessionCreatedAtEpochMillis)
        assertEquals(20L, projection.sessionLastActiveAtEpochMillis)
        val names = ProfileAccountUi.Authenticated::class.java.declaredFields.map { it.name.lowercase() }
        assertTrue(names.none { it == "accountid" || it == "sessionid" || it.contains("token") })
    }

    private fun authenticatedState(): EngineAuthState = EngineAuthState(
        state = EngineAuthState.AUTHENTICATED,
        account = EngineAccount(
            id = "internal-account",
            primaryEmail = "driver@example.com",
            status = "active",
            createdAtEpochMillis = 1L
        ),
        session = EngineAuthSession(
            id = "internal-session",
            deviceLabel = "Panda Emulator",
            createdAtEpochMillis = 10L,
            lastUsedAtEpochMillis = 20L,
            expiresAtEpochMillis = 30L,
            current = true
        )
    )
}
