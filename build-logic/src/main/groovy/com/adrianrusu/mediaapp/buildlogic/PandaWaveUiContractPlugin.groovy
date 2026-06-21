package com.adrianrusu.mediaapp.buildlogic

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
    }
}
