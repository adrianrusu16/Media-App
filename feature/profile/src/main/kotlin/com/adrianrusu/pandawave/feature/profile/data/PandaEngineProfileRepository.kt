package com.adrianrusu.pandawave.feature.profile.data

import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.feature.profile.domain.ProfileDetails
import com.adrianrusu.pandawave.feature.profile.domain.ProfileRepository
import com.adrianrusu.pandawave.feature.profile.domain.ProfileState
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PandaEngineProfileRepository @Inject constructor(
    private val engineGateway: EngineGateway
) : ProfileRepository {
    private val mutableState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    override val state: StateFlow<ProfileState> = mutableState.asStateFlow()

    private val started = AtomicBoolean(false)
    private var subscription: AutoCloseable? = null

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        subscription = engineGateway.observeSnapshots(::project)
        if (engineGateway.snapshot().authState.state == EngineAuthState.AUTHENTICATED) {
            dispatch(EngineCommand(EngineCommand.TYPE_GET_PROFILE, null))
            dispatch(EngineCommand(EngineCommand.TYPE_LOAD_PROFILE_PREFERENCES, null))
        }
    }

    override fun refresh() {
        dispatch(EngineCommand(EngineCommand.TYPE_GET_PROFILE, null))
        dispatch(EngineCommand(EngineCommand.TYPE_LOAD_PROFILE_PREFERENCES, null))
    }

    override fun upsert(displayName: String?) {
        dispatch(
            EngineCommand(
                EngineCommand.TYPE_UPSERT_PROFILE,
                EngineCommandPayloads.upsertProfile(displayName)
            )
        )
    }

    override fun updateDisplayName(displayName: String?) {
        dispatch(
            EngineCommand(
                EngineCommand.TYPE_UPDATE_PROFILE,
                EngineCommandPayloads.updateProfileDisplayName(displayName)
            )
        )
    }

    override fun delete() {
        dispatch(EngineCommand(EngineCommand.TYPE_DELETE_PROFILE, null))
    }

    override fun updateTheme(preference: PandaWaveThemePreference) {
        dispatch(
            EngineCommand(
                EngineCommand.TYPE_UPDATE_PROFILE_PREFERENCES,
                EngineCommandPayloads.updateProfileTheme(preference.wireValue)
            )
        )
    }

    override fun close() {
        subscription?.close()
        subscription = null
        started.set(false)
    }

    private fun dispatch(command: EngineCommand) {
        val outcome = engineGateway.dispatch(command)
        if (outcome.event.type == EngineEvent.TYPE_GATEWAY_UNAVAILABLE) {
            mutableState.value = ProfileState.Failure(
                errorType = EngineSnapshot.ERROR_NETWORK,
                retryable = true
            )
        } else {
            project(outcome.snapshot)
        }
    }

    private fun project(snapshot: EngineSnapshot) {
        if (snapshot.authState.state != EngineAuthState.AUTHENTICATED) {
            mutableState.value = ProfileState.SignedOut
            return
        }
        if (snapshot.hasError) {
            mutableState.value = ProfileState.Failure(
                errorType = snapshot.errorType,
                retryable = snapshot.errorType == EngineSnapshot.ERROR_NETWORK
            )
            return
        }
        val profile = snapshot.profile ?: run {
            mutableState.value = ProfileState.Missing
            return
        }
        mutableState.value = ProfileState.Ready(
            profile = ProfileDetails(
                id = profile.id,
                externalUserId = profile.externalUserId,
                displayName = profile.displayName,
                createdAtEpochMillis = profile.createdAtEpochMillis,
                updatedAtEpochMillis = profile.updatedAtEpochMillis
            ),
            theme = PandaWaveThemePreference.fromWireOrNull(snapshot.themePreference.themeId)
                ?: PandaWaveThemePreference.SystemDefault
        )
    }
}
