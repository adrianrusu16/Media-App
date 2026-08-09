package com.adrianrusu.pandawave.core.rust.bridge.engine

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCatalogItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineLibraryItem
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineCommand
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineEffect
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EnginePlatformEvent
import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineSnapshot

interface RustEngine {
    fun registerPassword(email: String, password: ByteArray): EngineAuthOperationResult =
        EngineAuthOperationResult.unavailable()

    fun resendVerification(email: String): EngineAuthOperationResult =
        EngineAuthOperationResult.unavailable()

    fun verifyEmail(
        verificationToken: ByteArray,
        deviceLabel: String
    ): EngineAuthOperationResult = EngineAuthOperationResult.unavailable()

    fun loginPassword(
        email: String,
        password: ByteArray,
        deviceLabel: String
    ): EngineAuthOperationResult = EngineAuthOperationResult.unavailable()

    fun logout(): EngineAuthOperationResult = EngineAuthOperationResult.unavailable()

    fun setAudioSourceResolver(resolver: AudioSourceResolver)

    fun snapshot(): EngineSnapshot

    fun browseResult(index: Int): EngineCatalogItem?

    fun searchResult(index: Int): EngineCatalogItem?

    fun savedTrack(index: Int): EngineLibraryItem? = null

    fun likedTrack(index: Int): EngineLibraryItem? = null

    fun pendingLibraryTrackId(index: Int): String? = null

    fun effectCount(): Int

    fun effect(index: Int): EngineEffect?

    fun dispatch(command: EngineCommand): EngineDispatchResult

    fun dispatchPlatformEvent(event: EnginePlatformEvent): EngineDispatchResult
}
