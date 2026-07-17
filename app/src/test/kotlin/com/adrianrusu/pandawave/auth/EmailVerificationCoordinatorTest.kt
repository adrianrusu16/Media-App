package com.adrianrusu.pandawave.auth

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.gateway.EngineAuthGateway
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class EmailVerificationCoordinatorTest {
    @Test
    fun `cold start waits for availability then submits one token exactly once`() = runTest {
        val gateway = RecordingAuthGateway(available = false)
        val outcomes = mutableListOf<EngineAuthOperationResult>()
        val coordinator = EmailVerificationCoordinator(
            authGateway = gateway,
            parseLink = { EmailVerificationLinkResult.Token("one-shot".encodeToByteArray()) },
            deviceLabel = "PandaWave",
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            onComplete = outcomes::add
        )

        coordinator.consume("https://host/verify-email?token=first")
        coordinator.consume("https://host/verify-email?token=replay")
        runCurrent()
        assertEquals(0, gateway.verificationCalls)

        gateway.setAvailable(true)
        runCurrent()

        assertEquals(1, gateway.verificationCalls)
        assertContentEquals("one-shot".encodeToByteArray(), gateway.receivedToken)
        assertEquals(listOf(EngineAuthOperationResult.authenticated()), outcomes)
    }

    @Test
    fun `closing before service availability wipes the pending token`() = runTest {
        val pending = "one-shot".encodeToByteArray()
        val coordinator = EmailVerificationCoordinator(
            authGateway = RecordingAuthGateway(available = false),
            parseLink = { EmailVerificationLinkResult.Token(pending) },
            deviceLabel = "PandaWave",
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            onComplete = { }
        )

        coordinator.consume("https://host/verify-email?token=pending")
        coordinator.close()

        assertContentEquals(ByteArray(pending.size), pending)
    }

    @Test
    fun `ambiguous verification result is published once and never retried`() = runTest {
        val result = EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_TRANSPORT)
        val gateway = RecordingAuthGateway(available = true, verificationResult = result)
        val outcomes = mutableListOf<EngineAuthOperationResult>()
        val coordinator = EmailVerificationCoordinator(
            authGateway = gateway,
            parseLink = { EmailVerificationLinkResult.Token("one-shot".encodeToByteArray()) },
            deviceLabel = "PandaWave",
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
            onComplete = outcomes::add
        )

        coordinator.consume("https://host/verify-email?token=one-shot")
        runCurrent()
        gateway.setAvailable(false)
        gateway.setAvailable(true)
        runCurrent()

        assertEquals(1, gateway.verificationCalls)
        assertEquals(listOf(result), outcomes)
    }

    @Test
    fun `cancelled owner wipes a claimed token even when verification never starts`() = runTest {
        val token = "one-shot".encodeToByteArray()
        val owner = SupervisorJob().also { it.cancel() }
        val gateway = RecordingAuthGateway(available = true)
        val coordinator = EmailVerificationCoordinator(
            authGateway = gateway,
            parseLink = { EmailVerificationLinkResult.Token(token) },
            deviceLabel = "PandaWave",
            scope = CoroutineScope(coroutineContext + owner),
            dispatcher = StandardTestDispatcher(testScheduler),
            onComplete = { }
        )

        coordinator.consume("https://host/verify-email?token=one-shot")
        runCurrent()

        assertEquals(0, gateway.verificationCalls)
        assertContentEquals(ByteArray(token.size), token)
    }
}

private class RecordingAuthGateway(
    available: Boolean,
    private val verificationResult: EngineAuthOperationResult = EngineAuthOperationResult.authenticated()
) : EngineAuthGateway {
    private val listeners = mutableSetOf<(Boolean) -> Unit>()
    private var available = available

    var verificationCalls: Int = 0
        private set
    var receivedToken: ByteArray? = null
        private set

    override val isAuthAvailable: Boolean
        get() = available

    override fun observeAuthAvailability(listener: (Boolean) -> Unit): AutoCloseable {
        listeners += listener
        listener(available)
        return AutoCloseable { listeners -= listener }
    }

    fun setAvailable(value: Boolean) {
        available = value
        listeners.toList().forEach { listener -> listener(value) }
    }

    override fun verifyEmail(verificationToken: ByteArray, deviceLabel: String): EngineAuthOperationResult {
        verificationCalls += 1
        receivedToken = verificationToken.copyOf()
        verificationToken.fill(0)
        return verificationResult
    }

    override fun registerPassword(email: String, password: ByteArray): EngineAuthOperationResult =
        EngineAuthOperationResult.unavailable()

    override fun resendVerification(email: String): EngineAuthOperationResult = EngineAuthOperationResult.unavailable()

    override fun loginPassword(email: String, password: ByteArray, deviceLabel: String): EngineAuthOperationResult =
        EngineAuthOperationResult.unavailable()

    override fun logout(): EngineAuthOperationResult = EngineAuthOperationResult.unavailable()
}
