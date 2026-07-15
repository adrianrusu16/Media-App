package com.adrianrusu.pandawave.core.rust.bridge.engine

import com.adrianrusu.pandawave.core.rust.bridge.engine.native.PandaEngine

object PandaEngineFactory {
    fun create(audioSourceResolver: AudioSourceResolver? = null): RustEngine =
        PandaEngine.create().apply {
            audioSourceResolver?.let(::setAudioSourceResolver)
        }

    internal fun createFake(clock: () -> Long = System::currentTimeMillis): RustEngine = FakePandaEngine(clock = clock)
}
