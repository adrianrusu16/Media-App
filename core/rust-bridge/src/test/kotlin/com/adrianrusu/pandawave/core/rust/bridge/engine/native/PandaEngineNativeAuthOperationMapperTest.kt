package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.rust.bridge.aidl.EngineAuthOperationResult
import kotlin.test.Test
import kotlin.test.assertEquals

class PandaEngineNativeAuthOperationMapperTest {
    @Test
    fun `maps typed error and retry hint without a message field`() {
        assertEquals(
            EngineAuthOperationResult.error(
                EngineAuthOperationResult.ERROR_RATE_LIMITED,
                retryAfterMillis = 2_500L
            ),
            PandaEngineNativeAuthOperationMapper.toDomain(
                arrayOf("error", "rate_limited", "2500")
            )
        )
    }

    @Test
    fun `malformed native values fail to mapping defect`() {
        assertEquals(
            EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_MAPPING_DEFECT),
            PandaEngineNativeAuthOperationMapper.toDomain(arrayOf("authenticated"))
        )
    }

    @Test
    fun `unknown native error text cannot cross the Kotlin boundary`() {
        assertEquals(
            EngineAuthOperationResult.error(EngineAuthOperationResult.ERROR_MAPPING_DEFECT),
            PandaEngineNativeAuthOperationMapper.toDomain(
                arrayOf("error", "token=secret backend message", "")
            )
        )
    }
}
