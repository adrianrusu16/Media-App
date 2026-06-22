plugins {
    id("pandawave.android.library")
}

android {
    useLibrary("android.car")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
