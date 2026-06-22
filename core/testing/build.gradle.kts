plugins {
    id("pandawave.android.library")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:rust-bridge"))
}
