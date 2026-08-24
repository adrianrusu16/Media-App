# Preserve JNI entry points and parcel creators used across Binder boundaries.
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class com.adrianrusu.pandawave.core.rust.bridge.aidl.** implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

-keep class com.adrianrusu.pandawave.core.rust.bridge.engine.EngineDispatchResult {
    public static final android.os.Parcelable$Creator CREATOR;
}
