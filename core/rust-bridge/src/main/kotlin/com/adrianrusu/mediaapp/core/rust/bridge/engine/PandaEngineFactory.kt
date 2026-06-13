package com.adrianrusu.mediaapp.core.rust.bridge.engine

import com.adrianrusu.mediaapp.core.rust.bridge.engine.native.PandaEngine

object PandaEngineFactory {
    fun create(): RustEngine = try {
        PandaEngine.create()
    } catch (_: UnsatisfiedLinkError) {
        createFake()
    } catch (_: IllegalStateException) {
        createFake()
    }

    fun createFake(): RustEngine = FakePandaEngine()
}
