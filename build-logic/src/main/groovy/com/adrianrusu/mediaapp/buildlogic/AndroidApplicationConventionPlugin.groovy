package com.adrianrusu.mediaapp.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidApplicationConventionPlugin implements Plugin<Project> {
    @Override
    void apply(Project target) {
        target.pluginManager.apply("com.android.application")

        target.extensions.configure(ApplicationExtension) { android ->
            android.namespace = "com.adrianrusu.mediaapp"
            AndroidConfiguration.configureAndroid(android)

            android.defaultConfig {
                applicationId = "com.adrianrusu.mediaapp"
                targetSdk = 36
                versionCode = 1
                versionName = "1.0"
            }

            android.buildTypes {
                release {
                    optimization {
                        enable = false
                    }
                }
            }
        }
    }
}
