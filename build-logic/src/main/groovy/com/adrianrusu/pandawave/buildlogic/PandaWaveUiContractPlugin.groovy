package com.adrianrusu.pandawave.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class PandaWaveUiContractPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.tasks.register("verifyPandaWaveUiContract", VerifyPandaWaveUiContractTask) {
            group = "verification"
            description = "Verifies the production BambooUI and RRO resource contract."
            kotlinSources.from(project.fileTree(project.rootDir) {
                include "core/ui/src/main/**/*.kt"
                include "core/designsystem/src/main/**/*.kt"
                include "feature/*/src/main/**/*.kt"
            })
            resourceSources.from(project.fileTree(project.rootDir) {
                include "app/src/main/res/**/*"
                include "core/designsystem/src/main/res/**/*"
                include "core/ui/src/main/res/**/*"
                include "feature/*/src/main/res/**/*"
            })
        }

        project.tasks.register("verifyPandaWaveIdentity", VerifyPandaWaveIdentityTask) {
            group = "verification"
            description = "Rejects live prototype identities after the PandaWave package migration."

            List<File> moduleDirectories = [project.file("app")]
            ["core", "feature", "provider", "rro"].each { String group ->
                File groupDirectory = project.file(group)
                moduleDirectories.addAll(
                    groupDirectory.listFiles()?.findAll { File candidate -> candidate.isDirectory() } ?: []
                )
            }

            identitySources.from(project.files(
                project.file("build.gradle.kts"),
                project.file("settings.gradle.kts"),
                project.file("gradle.properties"),
                project.file("build-logic/build.gradle.kts"),
                project.file("rust/engine/Cargo.lock"),
                project.file("rust/engine/Cargo.toml"),
                *moduleDirectories.collect { File module -> new File(module, "build.gradle.kts") }
            ))

            [project.file("app/src"), project.file("build-logic/src")].each { File sourceRoot ->
                identitySources.from(project.fileTree(sourceRoot))
            }
            moduleDirectories.each { File module ->
                File sourceRoot = new File(module, "src")
                if (sourceRoot.isDirectory()) {
                    identitySources.from(project.fileTree(sourceRoot))
                }
            }
            identitySources.from(project.fileTree(project.file("rust/engine/crates")) {
                include "**/*.rs"
                include "**/*.toml"
            })
        }
    }
}
