package com.adrianrusu.mediaapp.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidLibraryConventionPlugin implements Plugin<Project> {
    @Override
    void apply(Project target) {
        target.pluginManager.apply("com.android.library")

        target.extensions.configure(LibraryExtension) { android ->
            android.namespace = AndroidConfiguration.defaultNamespace(target)
            AndroidConfiguration.configureAndroid(android)
        }
    }
}
