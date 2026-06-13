package com.adrianrusu.mediaapp.core.rust.bridge.engine

import org.junit.Assert.assertNotNull
import org.junit.Test

class PandaEngineFactoryTest {
    @Test
    fun createFakeReturnsTestEngine() {
        val engine = PandaEngineFactory.createFake()

        assertNotNull(engine.snapshot())
    }
}
