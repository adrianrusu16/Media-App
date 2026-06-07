package com.adrianrusu.mediaapp.core.rust.bridge.engine

object PandaEngineFactory {
    fun createFake(): RustEngine = FakePandaEngine()
}
