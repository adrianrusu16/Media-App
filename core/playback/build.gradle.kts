plugins {
    id("mediaapp.android.library")
}

dependencies {
    implementation(project(":core:automotive"))
    implementation(project(":core:rust-bridge"))
    implementation(project(":core:telemetry-adapter"))
    implementation(project(":core:ui"))

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
