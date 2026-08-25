import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.RegularFileProperty

plugins {
    id("pandawave.android.application")
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
}

abstract class VerifyVerificationAppLinkHostTask : DefaultTask() {
    @get:Input
    abstract val appLinkHost: Property<String>

    @TaskAction
    fun verify() {
        val host = appLinkHost.orNull
        check(
            !host.isNullOrBlank() &&
                !host.endsWith(".invalid", ignoreCase = true) &&
                !host.endsWith(".test", ignoreCase = true) &&
                !host.endsWith(".example", ignoreCase = true) &&
                !host.endsWith(".localhost", ignoreCase = true)
        ) {
            "Release builds require -Ppandawave.verificationAppLinkHost=<public-host>."
        }
    }
}

abstract class VerifyReleaseNetworkSecurityTask : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile.readText()
        check("android:usesCleartextTraffic=\"false\"" in manifest) {
            "Release manifest must disable cleartext traffic."
        }
        check("android:networkSecurityConfig=\"@xml/network_security_config\"" in manifest) {
            "Release manifest must use the production network security configuration."
        }
    }
}

val configuredVerificationAppLinkHost = providers
    .gradleProperty("pandawave.verificationAppLinkHost")
    .orNull

android {
    val verificationAppLinkHost = configuredVerificationAppLinkHost ?: "verification.invalid"
    require(
        verificationAppLinkHost.matches(
            Regex(
                "(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+" +
                    "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
            )
        )
    ) { "pandawave.verificationAppLinkHost must be a DNS hostname without a scheme or path." }

    defaultConfig {
        manifestPlaceholders["verificationAppLinkHost"] = verificationAppLinkHost
        buildConfigField("String", "VERIFICATION_APP_LINK_HOST", "\"$verificationAppLinkHost\"")
        buildConfigField("String", "VERIFICATION_ACTION_PATH", "\"verify-email\"")
        buildConfigField("String", "VERIFICATION_TOKEN_PARAMETER", "\"token\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "VERIFICATION_DEBUG_SCHEME", "\"pandawave-dev\"")
        }
        release {
            buildConfigField("String", "VERIFICATION_DEBUG_SCHEME", "\"\"")
            optimization {
                enable = true
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

composeCompiler {
    // R8 still minifies; this only skips Compose group-key stack trace mapping.
    includeComposeMappingFile.set(false)
}

val verifyReleaseVerificationConfig by tasks.registering(VerifyVerificationAppLinkHostTask::class) {
    group = "verification"
    description = "Rejects release builds without a deployment-supplied verification App Link host."
    appLinkHost.set(configuredVerificationAppLinkHost ?: "")
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseVerificationConfig)
}

val verifyReleaseNetworkSecurityConfig by tasks.registering(VerifyReleaseNetworkSecurityTask::class) {
    group = "verification"
    description = "Verifies the merged release manifest rejects cleartext traffic."
    dependsOn("processReleaseMainManifest")
    mergedManifest.set(
        layout.buildDirectory.file(
            "intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml"
        )
    )
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn(verifyReleaseNetworkSecurityConfig)
}

dependencies {
    baselineProfile(project(":benchmark"))
    implementation(project(":core:audio-visualizer"))
    implementation(project(":core:automotive"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:media-adapter"))
    implementation(project(":core:model"))
    implementation(project(":core:playback"))
    implementation(project(":core:preferences"))
    implementation(project(":core:rust-bridge"))
    implementation(project(":core:telemetry-adapter"))
    implementation(project(":core:ui"))
    implementation(project(":feature:appshell"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)
    implementation(libs.androidx.profileinstaller)
    ksp(libs.hilt.compiler)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
