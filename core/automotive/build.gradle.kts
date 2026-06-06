plugins {
    id("mediaapp.android.library")
}

android {
    useLibrary("android.car")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))

    testImplementation(libs.junit)
}
