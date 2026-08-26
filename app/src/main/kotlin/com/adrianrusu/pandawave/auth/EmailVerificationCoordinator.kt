package com.adrianrusu.pandawave.auth

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineAuthGateway
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Owns the one-shot, in-memory handoff from an Android link to PandaEngine. */
class EmailVerificationCoordinator(
    private val authGateway: EngineAuthGateway,
    private val parseLink: (String?) -> EmailVerificationLinkResult,
    private val deviceLabel: String,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val log: (String) -> Unit = {},
    private val onComplete: (EngineAuthOperationResult) -> Unit
) : AutoCloseable {
    private val lock = Any()
    private val consumed = AtomicBoolean(false)
    private var pendingToken: ByteArray? = null
    private var submitted = false
    private var closed = false
    private var availabilitySubscription: AutoCloseable? = null
    private val consumeAt = AtomicLong(0)

    init {
        availabilitySubscription = authGateway.observeAuthAvailability { available ->
            if (available) submitPendingToken()
        }
    }

    fun consume(link: String?) {
        if (!consumed.compareAndSet(false, true)) return
        consumeAt.set(clock())
        log("verify-email.consume available=${authGateway.isAuthAvailable}")

        when (val result = parseLink(link)) {
            EmailVerificationLinkResult.Invalid -> complete(
                EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_INVALID_INPUT)
            )

            is EmailVerificationLinkResult.Token -> {
                val accepted = synchronized(lock) {
                    if (closed) {
                        false
                    } else {
                        pendingToken = result.value
                        true
                    }
                }
                if (!accepted) result.value.fill(0)
                if (accepted && authGateway.isAuthAvailable) {
                    submitPendingToken()
                } else if (accepted) {
                    log("verify-email.wait_engine")
                }
            }
        }
    }

    private fun submitPendingToken() {
        val token = synchronized(lock) {
            if (closed || submitted) return
            pendingToken?.also {
                pendingToken = null
                submitted = true
            }
        } ?: return

        val job = scope.launch(dispatcher) {
            val submitAt = clock()
            log("verify-email.submit waitEngineMs=${submitAt - consumeAt.get()}")
            val result = try {
                authGateway.verifyEmail(token, deviceLabel)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Error) {
                throw error
            } catch (_: Exception) {
                EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_TRANSPORT)
            }
            log(
                "verify-email.engine_result status=${result.status} " +
                    "rpcMs=${clock() - submitAt} sinceConsumeMs=${clock() - consumeAt.get()}"
            )
            complete(result)
        }
        job.invokeOnCompletion { token.fill(0) }
    }

    private fun complete(result: EngineAuthOperationResult) {
        val shouldPublish = synchronized(lock) { !closed }
        if (shouldPublish) onComplete(result)
    }

    override fun close() {
        val token = synchronized(lock) {
            if (closed) return
            closed = true
            pendingToken.also { pendingToken = null }
        }
        token?.fill(0)
        availabilitySubscription?.close()
        availabilitySubscription = null
    }
}
