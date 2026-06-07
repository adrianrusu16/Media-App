pluginManagement {
    includeBuild("build-logic")

    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PandaWave"
include(":app")

include(
    ":core:common",
    ":core:model",
    ":core:designsystem",
    ":core:ui",
    ":core:automotive",
    ":core:vehicle",
    ":core:carui",
    ":core:media-adapter",
    ":core:rust-bridge",
    ":core:secure-storage-adapter",
    ":core:telemetry-adapter",
    ":core:testing",
    ":feature:home",
    ":feature:appshell",
    ":feature:library",
    ":feature:search",
    ":feature:nowplaying",
    ":feature:settings",
    ":feature:profile",
    ":feature:auth",
    ":provider:jamendo"
)
