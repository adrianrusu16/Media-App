package com.adrianrusu.pandawave.feature.auth.presentation

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineAuthGateway
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.feature.auth.domain.LogoutPhase
import com.adrianrusu.pandawave.feature.auth.domain.ProfileAuthEffect
import com.adrianrusu.pandawave.feature.auth.domain.ProfileAuthEvent
import com.adrianrusu.pandawave.feature.auth.domain.ProfileAuthReducer
import com.adrianrusu.pandawave.feature.auth.domain.ProfileAuthTransition
import com.adrianrusu.pandawave.feature.auth.domain.ProfileAuthUiState
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileAuthController(
    private val authGateway: EngineAuthGateway,
    engineGateway: EngineGateway,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val snapshotTimeoutMillis: Long,
    private val onEffect: (ProfileAuthEffect) -> Unit
) : AutoCloseable {
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(
        ProfileAuthUiState.from(engineGateway.snapshot().authState)
    )
    private val mutableAvailable = MutableStateFlow(authGateway.isAuthAvailable)
    private var operation: Job? = null
    private val snapshotSubscription = engineGateway.observeSnapshots { snapshot ->
        dispatch(ProfileAuthEvent.SnapshotChanged(snapshot.authState))
    }
    private val availabilitySubscription = authGateway.observeAuthAvailability { available ->
        mutableAvailable.value = available
    }

    val state: StateFlow<ProfileAuthUiState> = mutableState.asStateFlow()
    val isAvailable: StateFlow<Boolean> = mutableAvailable.asStateFlow()

    fun logout() {
        if (!authGateway.isAuthAvailable) return
        val transition = dispatch(ProfileAuthEvent.Logout)
        if (ProfileAuthEffect.SubmitLogout !in transition.effects) return

        val job = scope.launch(dispatcher, start = CoroutineStart.LAZY) {
            val result = try {
                authGateway.logout()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_TRANSPORT)
            }
            currentCoroutineContext().ensureActive()
            dispatch(ProfileAuthEvent.LogoutCompleted(result))
            if (mutableState.value.logoutPhase == LogoutPhase.AWAITING_ANONYMOUS_SNAPSHOT) {
                delay(snapshotTimeoutMillis)
                dispatch(ProfileAuthEvent.SnapshotTimeout)
            }
        }
        synchronized(lock) { operation = job }
        job.invokeOnCompletion {
            synchronized(lock) {
                if (operation === job) operation = null
            }
        }
        job.start()
    }

    private fun dispatch(event: ProfileAuthEvent): ProfileAuthTransition {
        val transition = synchronized(lock) {
            if (closed.get()) return@synchronized ProfileAuthTransition(mutableState.value)
            ProfileAuthReducer.reduce(mutableState.value, event).also { transition ->
                mutableState.value = transition.state
            }
        }
        transition.effects.forEach(onEffect)
        return transition
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) { operation.also { operation = null } }?.cancel()
        snapshotSubscription.close()
        availabilitySubscription.close()
    }
}
