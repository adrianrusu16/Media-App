package com.adrianrusu.mediaapp.core.rust.bridge.engine

object FakeRustEngineFactory {
    fun create(): RustEngine = FakeRustEngine()
}
