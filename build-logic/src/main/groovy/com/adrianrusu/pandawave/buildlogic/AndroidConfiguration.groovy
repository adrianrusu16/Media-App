package com.adrianrusu.pandawave.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project

class AndroidConfiguration {
    static void configureAndroid(android) {
        android.compileSdk {
            version = release(36) {
                minorApiLevel = 1
            }
        }

        android.defaultConfig {
            minSdk = 33
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        android.compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }

        android.testOptions {
            unitTests.all { test ->
                test.useJUnitPlatform()
                test.systemProperty("junit.jupiter.testinstance.lifecycle.default", "per_class")
            }
        }

        android.buildFeatures {
            aidl = true
        }
    }

    static String defaultNamespace(Project project) {
        def moduleNamespace = project.path
            .split(":")
            .findAll { !it.isBlank() }
            .collect { it.replace("-", ".") }
            .join(".")

        if (moduleNamespace.isBlank()) {
            return "com.adrianrusu.pandawave"
        }

        return "com.adrianrusu.pandawave.$moduleNamespace"
    }
}
