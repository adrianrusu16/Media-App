package com.adrianrusu.pandawave.core.rust.bridge.engine

import com.adrianrusu.pandawave.core.rust.bridge.engine.native.PandaEngine

object PandaEngineFactory {
    fun create(configJson: String): RustEngine = create(configJson, isDevelopment = false)

    internal fun create(configJson: String, isDevelopment: Boolean): RustEngine =
        PandaEngine.create().also { engine ->
            try {
                engine.configureBackend(configJson, isDevelopment)
            } catch (error: Throwable) {
                engine.close()
                throw error
            }
        }

    internal fun createFake(clock: () -> Long = System::currentTimeMillis): RustEngine = FakePandaEngine(clock = clock)
}
