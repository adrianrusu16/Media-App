// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.spotless)
}

val detektCli by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    detektCli(libs.detekt.cli)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable")
        )
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(libs.versions.ktlint.get())
    }
}

val detektSource = files(
    subprojects.flatMap { project ->
        listOf(
            project.file("src/main/kotlin"),
            project.file("src/test/kotlin"),
            project.file("src/androidTest/kotlin")
        ).filter { it.exists() }
    }
)

tasks.register<JavaExec>("detektAll") {
    group = "verification"
    description = "Runs Detekt static analysis across Kotlin source sets."

    classpath = detektCli
    mainClass.set("dev.detekt.cli.Main")

    inputs.files(detektSource)
    inputs.file(layout.projectDirectory.file("config/detekt/detekt.yml"))
    outputs.dir(layout.buildDirectory.dir("reports/detekt"))

    doFirst {
        val reportDir = layout.buildDirectory.dir("reports/detekt").get().asFile
        reportDir.mkdirs()

        args(
            "--input",
            detektSource.files.joinToString(separator = java.io.File.pathSeparator) {
                it.absolutePath
            },
            "--config",
            layout.projectDirectory.file("config/detekt/detekt.yml").asFile.absolutePath,
            "--build-upon-default-config",
            "--base-path",
            rootDir.absolutePath,
            "--fail-on-severity",
            "Error",
            "--report",
            "html:${reportDir.resolve("detekt.html").absolutePath}",
            "--report",
            "checkstyle:${reportDir.resolve("detekt.xml").absolutePath}",
            "--report",
            "sarif:${reportDir.resolve("detekt.sarif").absolutePath}"
        )
    }
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs Kotlin formatting and static analysis checks."
    dependsOn("spotlessCheck", "detektAll")
}
