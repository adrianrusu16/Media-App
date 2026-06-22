package com.adrianrusu.pandawave.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidApplicationConventionPlugin implements Plugin<Project> {
    @Override
    void apply(Project target) {
        target.pluginManager.apply("com.android.application")

        target.extensions.configure(ApplicationExtension) { android ->
            android.namespace = "com.adrianrusu.pandawave"
            AndroidConfiguration.configureAndroid(android)

            android.defaultConfig {
                applicationId = "com.adrianrusu.pandawave"
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
