plugins {
    `groovy-gradle-plugin`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "pandawave.android.application"
            implementationClass = "com.adrianrusu.pandawave.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "pandawave.android.library"
            implementationClass = "com.adrianrusu.pandawave.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("pandaWaveUiContract") {
            id = "pandawave.ui-contract"
            implementationClass = "com.adrianrusu.pandawave.buildlogic.PandaWaveUiContractPlugin"
        }
    }
}
