package com.adrianrusu.mediaapp.core.rust.bridge.engine

import kotlin.test.Test
import kotlin.test.assertNotNull

class PandaEngineFactoryTest {
    @Test
    fun `create fake returns test engine`() {
        val engine = PandaEngineFactory.createFake()

        assertNotNull(engine.snapshot())
    }
}
