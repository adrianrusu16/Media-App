plugins {
    id("mediaapp.android.library")
}

dependencies {
    implementation(project(":core:automotive"))
    implementation(project(":core:model"))
    implementation(project(":core:rust-bridge"))
    implementation(project(":core:ui"))
}
