package com.adrianrusu.pandawave.feature.auth.presentation

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.common.log.PandaLog
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineAuthGateway
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineGateway
import com.adrianrusu.pandawave.feature.auth.domain.AuthFormMode
import com.adrianrusu.pandawave.feature.auth.domain.AuthFormPhase
import com.adrianrusu.pandawave.feature.auth.domain.AuthFormState
import com.adrianrusu.pandawave.feature.auth.domain.AuthUiEffect
import com.adrianrusu.pandawave.feature.auth.domain.AuthUiEvent
import com.adrianrusu.pandawave.feature.auth.domain.AuthUiReducer
import com.adrianrusu.pandawave.feature.auth.domain.AuthUiTransition
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthFlowController(
    mode: AuthFormMode,
    private val authGateway: EngineAuthGateway,
    engineGateway: EngineGateway,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val deviceLabel: String,
    private val onEffect: (AuthUiEffect) -> Unit
) : AutoCloseable {
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val mutableState = MutableStateFlow(
        when (mode) {
            AuthFormMode.LOGIN -> AuthFormState.login()
            AuthFormMode.REGISTER -> AuthFormState.register()
        }
    )
    private val mutableAvailable = MutableStateFlow(authGateway.isAuthAvailable)
    private var operation: Job? = null
    private val snapshotSubscription = engineGateway.observeSnapshots { snapshot ->
        dispatch(AuthUiEvent.SnapshotChanged(snapshot.authState))
    }
    private val availabilitySubscription = authGateway.observeAuthAvailability { available ->
        mutableAvailable.value = available
    }

    val state: StateFlow<AuthFormState> = mutableState.asStateFlow()
    val isAvailable: StateFlow<Boolean> = mutableAvailable.asStateFlow()

    fun submit(email: String, password: ByteArray) {
        if (!authGateway.isAuthAvailable) {
            password.fill(0)
            return
        }
        val transition = dispatch(AuthUiEvent.Submit)
        val expectedEffect = when (mutableState.value.mode) {
            AuthFormMode.LOGIN -> AuthUiEffect.SubmitLogin
            AuthFormMode.REGISTER -> AuthUiEffect.SubmitRegistration
        }
        if (expectedEffect !in transition.effects) {
            password.fill(0)
            return
        }

        val job = scope.launch(dispatcher, start = CoroutineStart.LAZY) {
            val result = try {
                when (mutableState.value.mode) {
                    AuthFormMode.LOGIN -> authGateway.loginPassword(email, password, deviceLabel)
                    AuthFormMode.REGISTER -> authGateway.registerPassword(email, password)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_TRANSPORT)
            }
            currentCoroutineContext().ensureActive()
            dispatch(AuthUiEvent.CommandCompleted(result))
        }
        synchronized(lock) { operation = job }
        job.invokeOnCompletion {
            password.fill(0)
            synchronized(lock) {
                if (operation === job) operation = null
            }
        }
        job.start()
    }

    fun resend(email: String) {
        if (!authGateway.isAuthAvailable) return
        val transition = dispatch(AuthUiEvent.Resend)
        if (AuthUiEffect.ResendVerification !in transition.effects) return

        val job = scope.launch(dispatcher, start = CoroutineStart.LAZY) {
            val result = try {
                authGateway.resendVerification(email)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_TRANSPORT)
            }
            currentCoroutineContext().ensureActive()
            dispatch(AuthUiEvent.ResendCompleted(result))
        }
        synchronized(lock) { operation = job }
        job.invokeOnCompletion {
            synchronized(lock) {
                if (operation === job) operation = null
            }
        }
        job.start()
    }

    fun cancelOrRestrict() {
        val job = synchronized(lock) { operation.also { operation = null } }
        job?.cancel()
        dispatch(AuthUiEvent.CancelOrRestricted)
    }

    private fun dispatch(event: AuthUiEvent): AuthUiTransition {
        val previousPhase = mutableState.value.phase
        val transition = synchronized(lock) {
            if (closed.get()) return@synchronized AuthUiTransition(mutableState.value)
            AuthUiReducer.reduce(mutableState.value, event).also { transition ->
                mutableState.value = transition.state
            }
        }
        logAuthPhase(event, previousPhase, transition)
        transition.effects.forEach(onEffect)
        return transition
    }

    private fun logAuthPhase(
        event: AuthUiEvent,
        previousPhase: AuthFormPhase,
        transition: AuthUiTransition
    ) {
        val nextPhase = transition.state.phase
        val finishing = previousPhase == AuthFormPhase.SUBMITTING ||
            previousPhase == AuthFormPhase.FINISHING_SIGN_IN ||
            nextPhase == AuthFormPhase.FINISHING_SIGN_IN
        val verification = previousPhase == AuthFormPhase.VERIFICATION_PENDING ||
            nextPhase == AuthFormPhase.VERIFICATION_PENDING
        if (!finishing && !verification && previousPhase == nextPhase) return
        val snapshotAuth = (event as? AuthUiEvent.SnapshotChanged)?.state?.state
        runCatching {
            PandaLog.i(PandaLog.Tag.AUTH) {
                "auth.ui event=${event::class.java.simpleName} phase=$previousPhase->$nextPhase" +
                    (snapshotAuth?.let { " snapshotAuth=$it" } ?: "")
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) { operation.also { operation = null } }?.cancel()
        snapshotSubscription.close()
        availabilitySubscription.close()
    }
}
