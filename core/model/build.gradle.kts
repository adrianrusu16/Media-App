plugins {
    id("mediaapp.android.library")
}

dependencies {
    implementation(project(":core:common"))

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
