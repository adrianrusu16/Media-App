package com.adrianrusu.pandawave.feature.auth.domain

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState

enum class AuthFormMode {
    LOGIN,
    REGISTER
}

enum class AuthFormPhase {
    IDLE,
    SUBMITTING,
    FINISHING_SIGN_IN,
    VERIFICATION_PENDING
}

enum class AuthNotice {
    LOGIN_REJECTED,
    POLICY_MISMATCH,
    RATE_LIMITED,
    CANOPY_UNREACHABLE,
    SERVER_CONFIGURATION_FAILED,
    CANOPY_SERVER_FAILED,
    APP_BACKEND_MISMATCH,
    TRY_AGAIN_LATER,
    SESSION_STORAGE_FAILED,
    REQUEST_UNCONFIRMED,
    EMAIL_SENT
}

data class AuthFormState(
    val mode: AuthFormMode,
    val phase: AuthFormPhase = AuthFormPhase.IDLE,
    val notice: AuthNotice? = null,
    val resendInFlight: Boolean = false
) {
    companion object {
        fun login(): AuthFormState = AuthFormState(AuthFormMode.LOGIN)

        fun register(): AuthFormState = AuthFormState(AuthFormMode.REGISTER)
    }
}

sealed interface AuthUiEvent {
    data object Submit : AuthUiEvent

    data class CommandCompleted(val result: EngineAuthOperationResult) : AuthUiEvent

    data class SnapshotChanged(val state: EngineAuthState) : AuthUiEvent

    data object Resend : AuthUiEvent

    data class ResendCompleted(val result: EngineAuthOperationResult) : AuthUiEvent

    data object CancelOrRestricted : AuthUiEvent
}

sealed interface AuthUiEffect {
    data object SubmitLogin : AuthUiEffect

    data object SubmitRegistration : AuthUiEffect

    data object ResendVerification : AuthUiEffect

    data object Close : AuthUiEffect
}

data class AuthUiTransition(
    val state: AuthFormState,
    val effects: List<AuthUiEffect> = emptyList()
)

object AuthUiReducer {
    fun reduce(state: AuthFormState, event: AuthUiEvent): AuthUiTransition = when (event) {
        AuthUiEvent.Submit -> submit(state)
        is AuthUiEvent.CommandCompleted -> commandCompleted(state, event.result)
        is AuthUiEvent.SnapshotChanged -> snapshotChanged(state, event.state)
        AuthUiEvent.Resend -> resend(state)
        is AuthUiEvent.ResendCompleted -> resendCompleted(state, event.result)
        AuthUiEvent.CancelOrRestricted -> AuthUiTransition(
            state.copy(
                phase = AuthFormPhase.IDLE,
                notice = null,
                resendInFlight = false
            ),
            listOf(AuthUiEffect.Close)
        )
    }

    private fun submit(state: AuthFormState): AuthUiTransition {
        if (state.phase != AuthFormPhase.IDLE) return AuthUiTransition(state)
        val effect = when (state.mode) {
            AuthFormMode.LOGIN -> AuthUiEffect.SubmitLogin
            AuthFormMode.REGISTER -> AuthUiEffect.SubmitRegistration
        }
        return AuthUiTransition(
            state.copy(phase = AuthFormPhase.SUBMITTING, notice = null),
            listOf(effect)
        )
    }

    private fun commandCompleted(
        state: AuthFormState,
        result: EngineAuthOperationResult
    ): AuthUiTransition {
        if (state.phase != AuthFormPhase.SUBMITTING) return AuthUiTransition(state)
        return when (state.mode) {
            AuthFormMode.LOGIN -> loginCompleted(state, result)
            AuthFormMode.REGISTER -> registrationCompleted(state, result)
        }
    }

    private fun loginCompleted(
        state: AuthFormState,
        result: EngineAuthOperationResult
    ): AuthUiTransition = when (result.status) {
        EngineAuthOperationResult.STATUS_AUTHENTICATED,
        EngineAuthOperationResult.STATUS_ACCEPTED -> AuthUiTransition(
            state.copy(phase = AuthFormPhase.FINISHING_SIGN_IN, notice = null)
        )
        EngineAuthOperationResult.STATUS_REJECTED -> AuthUiTransition(
            state.copy(phase = AuthFormPhase.IDLE, notice = AuthNotice.LOGIN_REJECTED)
        )
        else -> AuthUiTransition(
            state.copy(
                phase = AuthFormPhase.IDLE,
                notice = noticeFor(result, login = true)
            )
        )
    }

    private fun registrationCompleted(
        state: AuthFormState,
        result: EngineAuthOperationResult
    ): AuthUiTransition = when {
        result.status == EngineAuthOperationResult.STATUS_ACCEPTED -> AuthUiTransition(
            state.copy(phase = AuthFormPhase.VERIFICATION_PENDING, notice = null)
        )
        result.errorType == EngineAuthOperationResult.ERROR_INVALID_INPUT -> AuthUiTransition(
            state.copy(phase = AuthFormPhase.IDLE, notice = AuthNotice.POLICY_MISMATCH)
        )
        result.errorType == EngineAuthOperationResult.ERROR_RATE_LIMITED -> AuthUiTransition(
            state.copy(phase = AuthFormPhase.IDLE, notice = AuthNotice.RATE_LIMITED)
        )
        else -> AuthUiTransition(
            state.copy(
                phase = AuthFormPhase.VERIFICATION_PENDING,
                notice = AuthNotice.REQUEST_UNCONFIRMED
            )
        )
    }

    private fun snapshotChanged(
        state: AuthFormState,
        authState: EngineAuthState
    ): AuthUiTransition = if (authState.state == EngineAuthState.AUTHENTICATED) {
        AuthUiTransition(
            state.copy(
                phase = AuthFormPhase.IDLE,
                notice = null,
                resendInFlight = false
            ),
            listOf(AuthUiEffect.Close)
        )
    } else {
        AuthUiTransition(state)
    }

    private fun resend(state: AuthFormState): AuthUiTransition = if (
        state.phase == AuthFormPhase.VERIFICATION_PENDING && !state.resendInFlight
    ) {
        AuthUiTransition(
            state.copy(resendInFlight = true, notice = null),
            listOf(AuthUiEffect.ResendVerification)
        )
    } else {
        AuthUiTransition(state)
    }

    private fun resendCompleted(
        state: AuthFormState,
        result: EngineAuthOperationResult
    ): AuthUiTransition {
        if (!state.resendInFlight) return AuthUiTransition(state)
        val notice = when {
            result.status == EngineAuthOperationResult.STATUS_ACCEPTED -> AuthNotice.EMAIL_SENT
            result.errorType == EngineAuthOperationResult.ERROR_RATE_LIMITED -> AuthNotice.RATE_LIMITED
            else -> AuthNotice.REQUEST_UNCONFIRMED
        }
        return AuthUiTransition(state.copy(resendInFlight = false, notice = notice))
    }

    private fun noticeFor(result: EngineAuthOperationResult, login: Boolean): AuthNotice = when (result.errorType) {
        EngineAuthOperationResult.ERROR_INVALID_INPUT -> AuthNotice.POLICY_MISMATCH
        EngineAuthOperationResult.ERROR_RATE_LIMITED -> AuthNotice.RATE_LIMITED
        EngineAuthOperationResult.ERROR_SESSION_STORAGE -> AuthNotice.SESSION_STORAGE_FAILED
        EngineAuthOperationResult.ERROR_SERVICE_UNAVAILABLE,
        EngineAuthOperationResult.ERROR_TRANSPORT,
        EngineAuthOperationResult.ERROR_NETWORK -> AuthNotice.CANOPY_UNREACHABLE
        EngineAuthOperationResult.ERROR_UNSAFE_TRANSPORT -> AuthNotice.SERVER_CONFIGURATION_FAILED
        EngineAuthOperationResult.ERROR_BACKEND_FAULT -> AuthNotice.CANOPY_SERVER_FAILED
        EngineAuthOperationResult.ERROR_MAPPING_DEFECT -> AuthNotice.APP_BACKEND_MISMATCH
        EngineAuthOperationResult.ERROR_AUTHENTICATION,
        EngineAuthOperationResult.ERROR_FORBIDDEN,
        EngineAuthOperationResult.ERROR_LOGIN_REQUIRED -> if (login) {
            AuthNotice.LOGIN_REJECTED
        } else {
            AuthNotice.REQUEST_UNCONFIRMED
        }
        else -> AuthNotice.TRY_AGAIN_LATER
    }
}
