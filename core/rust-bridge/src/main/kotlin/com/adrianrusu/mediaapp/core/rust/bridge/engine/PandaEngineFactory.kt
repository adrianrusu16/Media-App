package com.adrianrusu.mediaapp.core.rust.bridge.engine

import com.adrianrusu.mediaapp.core.rust.bridge.engine.native.PandaEngine

object PandaEngineFactory {
    fun create(): RustEngine = PandaEngine.create()

    internal fun createFake(): RustEngine = FakePandaEngine()
}
