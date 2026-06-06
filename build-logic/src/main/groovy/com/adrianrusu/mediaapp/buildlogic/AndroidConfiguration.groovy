package com.adrianrusu.mediaapp.buildlogic

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
            return "com.adrianrusu.mediaapp"
        }

        return "com.adrianrusu.mediaapp.$moduleNamespace"
    }
}
