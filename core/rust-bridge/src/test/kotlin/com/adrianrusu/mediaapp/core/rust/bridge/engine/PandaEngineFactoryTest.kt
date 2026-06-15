package com.adrianrusu.mediaapp.core.rust.bridge.engine

import com.adrianrusu.mediaapp.core.rust.bridge.aidl.EngineCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PandaEngineFactoryTest {
    @Test
    fun `create fake returns test engine`() {
        val engine = PandaEngineFactory.createFake()

        assertNotNull(engine.snapshot())
    }

    @Test
    fun `fake engine applies seek and speed payloads`() {
        val engine = PandaEngineFactory.createFake(clock = { 42L })

        val seekResult = engine.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_SEEK,
                payload = "12345"
            )
        )
        val speedResult = engine.dispatch(
            EngineCommand(
                type = EngineCommand.TYPE_SET_SPEED,
                payload = "1.25"
            )
        )

        assertEquals(12_345L, seekResult.snapshot.positionMillis)
        assertEquals(1.25F, speedResult.snapshot.playbackSpeed)
    }
}
