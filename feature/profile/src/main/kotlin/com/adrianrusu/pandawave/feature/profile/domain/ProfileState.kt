package com.adrianrusu.pandawave.feature.profile.domain

import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference

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

    data class Ready(
        val profile: ProfileDetails,
        val theme: PandaWaveThemePreference
    ) : ProfileState

    data class Failure(
        val errorType: String,
        val retryable: Boolean
    ) : ProfileState
}
