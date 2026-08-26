package com.adrianrusu.pandawave.feature.profile.data

import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthState
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.feature.profile.domain.ProfileDetails
import com.adrianrusu.pandawave.feature.profile.domain.AccountSessionsState
import com.adrianrusu.pandawave.feature.profile.domain.ProfileRepository
import com.adrianrusu.pandawave.feature.profile.domain.ProfileState
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PandaEngineProfileRepository(
    private val engineGateway: EngineGateway,
    private val hydrateExecutor: Executor,
) : ProfileRepository {
    @Inject
    constructor(engineGateway: EngineGateway) : this(
        engineGateway,
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "pw-account-hydrate").apply { isDaemon = true }
        }
    )
    private val mutableState = MutableStateFlow<ProfileState>(ProfileState.Loading)
    override val state: StateFlow<ProfileState> = mutableState.asStateFlow()
    private val mutableAccountSessionsState = MutableStateFlow<AccountSessionsState>(AccountSessionsState.Loading)
    override val accountSessionsState: StateFlow<AccountSessionsState> = mutableAccountSessionsState.asStateFlow()

    private val started = AtomicBoolean(false)
    private var subscription: AutoCloseable? = null
    private var hydratedIdentity: String? = null
    private var accountProjectionIdentity: String? = null

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        subscription = engineGateway.observeSnapshots(::onSnapshot)
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

    override fun refreshAccountSessions() {
        dispatch(EngineCommand(EngineCommand.TYPE_GET_ACCOUNT, null))
        dispatch(EngineCommand(EngineCommand.TYPE_LIST_DEVICE_SESSIONS, EngineCommandPayloads.deviceSessionsPage(50)))
    }

    override fun loadNextDeviceSessionsPage() = dispatch(EngineCommand(EngineCommand.TYPE_LOAD_NEXT_DEVICE_SESSIONS_PAGE, null))

    override fun revokeDeviceSession(sessionId: String) {
        val ready = mutableAccountSessionsState.value as? AccountSessionsState.Ready
        if (ready != null) mutableAccountSessionsState.value = ready.copy(pendingSessionId = sessionId)
        dispatch(EngineCommand(EngineCommand.TYPE_REVOKE_DEVICE_SESSION, EngineCommandPayloads.revokeDeviceSession(sessionId)))
    }

    override fun deleteAccount() {
        val ready = mutableAccountSessionsState.value as? AccountSessionsState.Ready
        if (ready != null) mutableAccountSessionsState.value = ready.copy(deletingAccount = true)
        dispatch(EngineCommand(EngineCommand.TYPE_DELETE_ACCOUNT, null))
    }

    override fun close() {
        subscription?.close()
        subscription = null
        started.set(false)
        hydratedIdentity = null
        accountProjectionIdentity = null
    }

    private fun dispatch(command: EngineCommand) {
        val outcome = engineGateway.dispatch(command)
        if (outcome.event.type == EngineEvent.TYPE_GATEWAY_UNAVAILABLE) {
            mutableState.value = ProfileState.Failure(
                errorType = EngineSnapshot.ERROR_NETWORK,
                retryable = true
            )
            if (command.isAccountSessionsCommand()) {
                mutableAccountSessionsState.value = AccountSessionsState.Failure(
                    errorType = EngineSnapshot.ERROR_NETWORK,
                    retryable = true
                )
            }
        } else {
            project(outcome.snapshot, command)
        }
    }

    private fun project(snapshot: EngineSnapshot, command: EngineCommand? = null) {
        val identity = snapshot.currentIdentity()
        if (identity == null) {
            mutableState.value = ProfileState.SignedOut
            mutableAccountSessionsState.value = AccountSessionsState.SignedOut
            accountProjectionIdentity = null
            return
        }
        projectAccountSessions(snapshot, identity, command)
        if (
            snapshot.profile == null &&
            snapshot.hasError &&
            snapshot.errorType == EngineSnapshot.ERROR_NOT_FOUND
        ) {
            mutableState.value = ProfileState.Missing
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

    private fun onSnapshot(snapshot: EngineSnapshot) {
        val identity = snapshot.currentIdentity()
        if (identity == null) {
            hydratedIdentity = null
            accountProjectionIdentity = null
            project(snapshot)
            return
        }
        val identityChanged = hydratedIdentity != identity
        if (identityChanged) {
            hydratedIdentity = identity
            accountProjectionIdentity = null
            mutableAccountSessionsState.value = AccountSessionsState.Loading
        }
        project(snapshot)
        if (!identityChanged) return
        hydrateExecutor.execute {
            if (!started.get()) return@execute
            val startedAt = System.currentTimeMillis()
            dispatch(EngineCommand(EngineCommand.TYPE_GET_PROFILE, null))
            dispatch(EngineCommand(EngineCommand.TYPE_LOAD_PROFILE_PREFERENCES, null))
            refreshAccountSessions()
            PandaLog.i(PandaLog.Tag.ACCOUNT) {
                "account.hydrate elapsedMs=${System.currentTimeMillis() - startedAt}"
            }
        }
    }

    private fun projectAccountSessions(
        snapshot: EngineSnapshot,
        identity: String,
        command: EngineCommand?
    ) {
        if (snapshot.hasError) {
            mutableAccountSessionsState.value = AccountSessionsState.Failure(snapshot.errorType, snapshot.errorType == EngineSnapshot.ERROR_NETWORK)
            return
        }
        val account = snapshot.protectedAccount ?: run {
            mutableAccountSessionsState.value = AccountSessionsState.Loading
            return
        }
        val authAccountId = snapshot.authState.account?.id
        if (account.id != authAccountId) {
            mutableAccountSessionsState.value = AccountSessionsState.Loading
            accountProjectionIdentity = null
            return
        }
        if (command?.type == EngineCommand.TYPE_GET_ACCOUNT) {
            accountProjectionIdentity = identity
        }
        if (accountProjectionIdentity != identity) {
            mutableAccountSessionsState.value = AccountSessionsState.Loading
            return
        }
        mutableAccountSessionsState.value = AccountSessionsState.Ready(account, snapshot.deviceSessions, snapshot.hasDeviceSessionsNextPage)
    }

    private fun EngineSnapshot.currentIdentity(): String? {
        val auth = authState
        val account = auth.account
        val session = auth.session
        return if (
            auth.state == EngineAuthState.AUTHENTICATED &&
            account != null && session != null && session.current
        ) {
            "${account.id}\u001f${session.id}"
        } else {
            null
        }
    }

    private fun EngineCommand.isAccountSessionsCommand(): Boolean = type in setOf(
        EngineCommand.TYPE_GET_ACCOUNT,
        EngineCommand.TYPE_DELETE_ACCOUNT,
        EngineCommand.TYPE_LIST_DEVICE_SESSIONS,
        EngineCommand.TYPE_LOAD_NEXT_DEVICE_SESSIONS_PAGE,
        EngineCommand.TYPE_REVOKE_DEVICE_SESSION
    )
}
