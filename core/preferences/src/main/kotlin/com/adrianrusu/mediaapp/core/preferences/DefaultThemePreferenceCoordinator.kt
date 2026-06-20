package com.adrianrusu.mediaapp.core.preferences

import com.adrianrusu.mediaapp.core.model.theme.PandaWaveThemePreference
import com.adrianrusu.mediaapp.core.model.theme.ThemePreferenceRepository
import com.adrianrusu.mediaapp.core.model.theme.ThemePreferenceState
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommandPayloads
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineSnapshot
import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineThemePreference
import com.adrianrusu.mediaapp.core.rust.bridge.gateway.EngineGateway
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DefaultThemePreferenceCoordinator(
    private val repository: ThemePreferenceRepository,
    private val engineGateway: EngineGateway,
    private val scope: CoroutineScope
) : ThemePreferenceCoordinator {
    override val state: StateFlow<ThemePreferenceState> = repository.state

    private val started = AtomicBoolean(false)
    private var hydrationJob: Job? = null
    private var snapshotSubscription: AutoCloseable? = null
    private var lastAppliedRemoteRevision = Long.MIN_VALUE

    override fun start() {
        if (!started.compareAndSet(false, true)) return

        snapshotSubscription = engineGateway.observeSnapshots(::onEngineSnapshot)
        hydrationJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val preference = repository.state
                .filterIsInstance<ThemePreferenceState.Ready>()
                .first()
                .preference
            dispatchThemeCommand(EngineCommand.TYPE_HYDRATE_THEME_PREFERENCE, preference)
        }
    }

    override suspend fun select(preference: PandaWaveThemePreference) {
        repository.setPreference(preference)
        dispatchThemeCommand(EngineCommand.TYPE_SET_THEME_PREFERENCE, preference)
    }

    override fun close() {
        hydrationJob?.cancel()
        hydrationJob = null
        snapshotSubscription?.close()
        snapshotSubscription = null
        started.set(false)
    }

    private fun onEngineSnapshot(snapshot: EngineSnapshot) {
        val enginePreference = snapshot.themePreference
        if (!shouldApplyRemote(enginePreference)) return

        val preference = PandaWaveThemePreference.fromWireOrNull(enginePreference.themeId) ?: return
        val currentPreference = (repository.state.value as? ThemePreferenceState.Ready)?.preference
        if (currentPreference == preference) {
            lastAppliedRemoteRevision = enginePreference.revision
            return
        }

        lastAppliedRemoteRevision = enginePreference.revision
        scope.launch(start = CoroutineStart.UNDISPATCHED) { repository.setPreference(preference) }
    }

    private fun shouldApplyRemote(preference: EngineThemePreference): Boolean = preference.initialized &&
        preference.source == EngineThemePreference.SOURCE_REMOTE_PROFILE &&
        preference.revision > lastAppliedRemoteRevision

    private fun dispatchThemeCommand(type: String, preference: PandaWaveThemePreference) {
        engineGateway.dispatch(
            EngineCommand(
                type = type,
                payload = EngineCommandPayloads.themePreference(preference.wireValue)
            )
        )
    }
}
