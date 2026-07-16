package com.adrianrusu.pandawave.core.rust.bridge.gateway

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult

/** Dedicated, non-queueing boundary for ephemeral authentication inputs. */
interface EngineAuthGateway {
    fun registerPassword(email: String, password: ByteArray): EngineAuthOperationResult

    fun resendVerification(email: String): EngineAuthOperationResult

    fun verifyEmail(verificationToken: ByteArray, deviceLabel: String): EngineAuthOperationResult

    fun loginPassword(
        email: String,
        password: ByteArray,
        deviceLabel: String
    ): EngineAuthOperationResult

    fun logout(): EngineAuthOperationResult
}
