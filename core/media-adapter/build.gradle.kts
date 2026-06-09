plugins {
    id("mediaapp.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:playback"))
    implementation(project(":core:rust-bridge"))
    implementation(project(":core:telemetry-adapter"))

    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
