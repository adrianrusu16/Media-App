package com.adrianrusu.pandawave.core.rust.bridge.engine.native

internal object PandaEngineLibrary {
    @Volatile
    private var loaded = false

    fun load() {
        if (loaded) return

        synchronized(this) {
            if (!loaded) {
                System.loadLibrary(LIBRARY_NAME)
                loaded = true
            }
        }
    }

    private const val LIBRARY_NAME = "panda_engine_ffi"
}
