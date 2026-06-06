plugins {
    id("mediaapp.android.application")
}

dependencies {
    implementation(project(":core:automotive"))
    implementation(project(":core:media-adapter"))
    implementation(project(":core:rust-bridge"))
    implementation(project(":core:telemetry-adapter"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:library"))
    implementation(project(":feature:nowplaying"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:search"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
