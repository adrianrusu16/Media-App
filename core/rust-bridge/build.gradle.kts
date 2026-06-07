plugins {
    id("mediaapp.android.library")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:telemetry-adapter"))

    testImplementation(libs.junit)
}
