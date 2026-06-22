plugins {
    id("pandawave.android.library")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:rust-bridge"))
    implementation(project(":core:telemetry-adapter"))

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
