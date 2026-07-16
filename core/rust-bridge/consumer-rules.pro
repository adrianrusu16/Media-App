# Called by PandaEngine's Rust JNI adapter. Names and byte-array signatures are ABI.
-keepclassmembers class com.adrianrusu.pandawave.core.rust.bridge.engine.native.PandaEngineSessionCryptor {
    public byte[][] seal(byte[], byte[]);
    public byte[] open(byte[], byte[], byte[], byte[]);
}
