plugins {
    `groovy-gradle-plugin`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "mediaapp.android.application"
            implementationClass = "com.adrianrusu.mediaapp.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "mediaapp.android.library"
            implementationClass = "com.adrianrusu.mediaapp.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("pandaWaveUiContract") {
            id = "pandawave.ui-contract"
            implementationClass = "com.adrianrusu.mediaapp.buildlogic.PandaWaveUiContractPlugin"
        }
    }
}
