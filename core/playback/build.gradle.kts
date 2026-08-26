plugins {
    id("pandawave.android.library")
}

dependencies {
    implementation(project(":core:automotive"))
    implementation(project(":core:common"))
    api(project(":core:rust-bridge"))
    implementation(project(":core:telemetry-adapter"))
    implementation(project(":core:ui"))

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
