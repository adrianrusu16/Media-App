package com.adrianrusu.mediaapp.core.rust.bridge.engine.native

internal object NativeRustLibrary {
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

    private const val LIBRARY_NAME = "media_app_ffi"
}
