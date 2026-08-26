package com.adrianrusu.pandawave.feature.auth.domain

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState

sealed interface ProfileAccountUi {
    data object Anonymous : ProfileAccountUi

    data class Authenticated(
        val email: String,
        val accountStatus: String,
        val deviceLabel: String,
        val sessionCreatedAtEpochMillis: Long,
        val sessionLastActiveAtEpochMillis: Long
    ) : ProfileAccountUi
}

enum class LogoutPhase {
    IDLE,
    SUBMITTING,
    AWAITING_ANONYMOUS_SNAPSHOT
}

data class ProfileAuthUiState(
    val account: ProfileAccountUi,
    val logoutPhase: LogoutPhase = LogoutPhase.IDLE,
    val localClearConfirmed: Boolean = false,
    val remoteWarningPending: Boolean = false
) {
    companion object {
        fun from(authState: EngineAuthState): ProfileAuthUiState = ProfileAuthUiState(
            account = authState.toProfileAccount()
        )
    }
}

sealed interface ProfileAuthEvent {
    data object Logout : ProfileAuthEvent

    data class LogoutCompleted(val result: EngineAuthOperationResult) : ProfileAuthEvent

    data class SnapshotChanged(val state: EngineAuthState) : ProfileAuthEvent

    data object SnapshotTimeout : ProfileAuthEvent
}

sealed interface ProfileAuthEffect {
    data object SubmitLogout : ProfileAuthEffect
}

enum class ProfileAuthNotice : ProfileAuthEffect {
    REMOTE_LOGOUT_UNCONFIRMED,
    LOGOUT_FAILED
}

data class ProfileAuthTransition(val state: ProfileAuthUiState, val effects: List<ProfileAuthEffect> = emptyList())

object ProfileAuthReducer {
    fun reduce(state: ProfileAuthUiState, event: ProfileAuthEvent): ProfileAuthTransition = when (event) {
        ProfileAuthEvent.Logout -> logout(state)
        is ProfileAuthEvent.LogoutCompleted -> logoutCompleted(state, event.result)
        is ProfileAuthEvent.SnapshotChanged -> snapshotChanged(state, event.state)
        ProfileAuthEvent.SnapshotTimeout -> snapshotTimeout(state)
    }

    private fun logout(state: ProfileAuthUiState): ProfileAuthTransition = if (
        state.account is ProfileAccountUi.Authenticated && state.logoutPhase == LogoutPhase.IDLE
    ) {
        ProfileAuthTransition(
            state.copy(logoutPhase = LogoutPhase.SUBMITTING),
            listOf(ProfileAuthEffect.SubmitLogout)
        )
    } else {
        ProfileAuthTransition(state)
    }

    private fun logoutCompleted(state: ProfileAuthUiState, result: EngineAuthOperationResult): ProfileAuthTransition {
        if (state.logoutPhase != LogoutPhase.SUBMITTING) return ProfileAuthTransition(state)
        if (state.account == ProfileAccountUi.Anonymous) {
            val warning = result.status == EngineAuthOperationResult.STATUS_ERROR
            return ProfileAuthTransition(
                ProfileAuthUiState(account = ProfileAccountUi.Anonymous),
                if (warning) {
                    listOf(ProfileAuthNotice.REMOTE_LOGOUT_UNCONFIRMED)
                } else {
                    emptyList()
                }
            )
        }
        if (result.status != EngineAuthOperationResult.STATUS_ANONYMOUS) {
            return ProfileAuthTransition(
                state.copy(
                    logoutPhase = LogoutPhase.IDLE,
                    localClearConfirmed = false,
                    remoteWarningPending = false
                ),
                listOf(ProfileAuthNotice.LOGOUT_FAILED)
            )
        }

        return ProfileAuthTransition(
            state.copy(
                logoutPhase = LogoutPhase.AWAITING_ANONYMOUS_SNAPSHOT,
                localClearConfirmed = true,
                remoteWarningPending = false
            )
        )
    }

    private fun snapshotChanged(state: ProfileAuthUiState, authState: EngineAuthState): ProfileAuthTransition {
        val account = authState.toProfileAccount()
        if (account == ProfileAccountUi.Anonymous) {
            if (state.logoutPhase == LogoutPhase.SUBMITTING) {
                return ProfileAuthTransition(
                    state.copy(
                        account = ProfileAccountUi.Anonymous,
                        localClearConfirmed = true
                    )
                )
            }
            return ProfileAuthTransition(
                ProfileAuthUiState(account = ProfileAccountUi.Anonymous),
                warningEffect(state)
            )
        }
        if (state.logoutPhase != LogoutPhase.IDLE) return ProfileAuthTransition(state)
        return ProfileAuthTransition(state.copy(account = account))
    }

    private fun snapshotTimeout(state: ProfileAuthUiState): ProfileAuthTransition = if (
        state.localClearConfirmed && state.logoutPhase == LogoutPhase.AWAITING_ANONYMOUS_SNAPSHOT
    ) {
        ProfileAuthTransition(
            ProfileAuthUiState(account = ProfileAccountUi.Anonymous),
            warningEffect(state, fallback = true)
        )
    } else {
        ProfileAuthTransition(state)
    }

    private fun warningEffect(state: ProfileAuthUiState, fallback: Boolean = false): List<ProfileAuthEffect> =
        if (state.remoteWarningPending || fallback) {
            listOf(ProfileAuthNotice.REMOTE_LOGOUT_UNCONFIRMED)
        } else {
            emptyList()
        }
}

private fun EngineAuthState.toProfileAccount(): ProfileAccountUi {
    val account = account
    val session = session
    return if (
        state == EngineAuthState.AUTHENTICATED && account != null && session != null
    ) {
        ProfileAccountUi.Authenticated(
            email = account.primaryEmail,
            accountStatus = account.status,
            deviceLabel = session.deviceLabel,
            sessionCreatedAtEpochMillis = session.createdAtEpochMillis,
            sessionLastActiveAtEpochMillis = session.lastUsedAtEpochMillis
        )
    } else {
        ProfileAccountUi.Anonymous
    }
}
