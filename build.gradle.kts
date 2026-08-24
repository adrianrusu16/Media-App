import com.diffplug.spotless.LineEnding

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    id("pandawave.ui-contract")
}

val detektCli by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    detektCli(libs.detekt.cli)
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    basePath = rootDir
}

spotless {
    lineEndings = LineEnding.UNIX

    val generatedAndLocalCaches = listOf(
        "**/build/**",
        "**/.gradle/**",
        "**/.gradle-test-home/**",
        "**/.cargo-home/**",
        "**/.rustup-home/**",
        "**/.serena/**",
        "**/graphify-out/**",
        "**/target-codex*/**"
    )

    kotlin {
        target(
            "app/src/**/*.kt",
            "core/*/src/**/*.kt",
            "feature/*/src/**/*.kt",
            "provider/*/src/**/*.kt",
            "rro/*/src/**/*.kt"
        )
        targetExclude(generatedAndLocalCaches)
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(
            mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable")
        )
    }

    kotlinGradle {
        target(
            "*.gradle.kts",
            "app/*.gradle.kts",
            "build-logic/*.gradle.kts",
            "core/*/*.gradle.kts",
            "feature/*/*.gradle.kts",
            "provider/*/*.gradle.kts",
            "rro/*/*.gradle.kts"
        )
        targetExclude(generatedAndLocalCaches)
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

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs Kotlin formatting and static analysis checks."
    dependsOn("spotlessCheck", "detekt", "verifyPandaWaveIdentity", "verifyPandaWaveUiContract")
}
