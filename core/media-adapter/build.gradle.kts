plugins {
    id("mediaapp.android.library")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:rust-bridge"))

    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
}
