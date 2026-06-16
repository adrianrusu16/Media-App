package com.adrianrusu.mediaapp.core.rust.bridge.engine

import com.adrianrusu.mediaapp.core.rust.bridge.engine.native.PandaEngine

object PandaEngineFactory {
    fun create(audioSourceResolver: AudioSourceResolver = AudioSourceResolvers.unavailable()): RustEngine =
        PandaEngine.create().apply {
            setAudioSourceResolver(audioSourceResolver)
        }

    internal fun createFake(clock: () -> Long = System::currentTimeMillis): RustEngine = FakePandaEngine(clock = clock)
}
