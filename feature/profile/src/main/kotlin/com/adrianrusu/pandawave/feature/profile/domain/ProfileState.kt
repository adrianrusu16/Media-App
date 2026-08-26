package com.adrianrusu.pandawave.feature.profile.domain

import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAccount
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthSession

data class ProfileDetails(
    val id: String,
    val externalUserId: String,
    val displayName: String?,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?
)

sealed interface ProfileState {
    data object Loading : ProfileState
    data object SignedOut : ProfileState
    data object Missing : ProfileState

    data class Ready(val profile: ProfileDetails, val theme: PandaWaveThemePreference) : ProfileState

    data class Failure(val errorType: String, val retryable: Boolean) : ProfileState
}

sealed interface AccountSessionsState {
    data object Loading : AccountSessionsState
    data object SignedOut : AccountSessionsState
    data class Ready(
        val account: EngineAccount,
        val sessions: List<EngineAuthSession>,
        val hasNextPage: Boolean,
        val pendingSessionId: String? = null,
        val deletingAccount: Boolean = false
    ) : AccountSessionsState
    data class Failure(val errorType: String, val retryable: Boolean) : AccountSessionsState
}
