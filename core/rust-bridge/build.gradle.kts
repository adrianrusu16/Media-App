import com.adrianrusu.pandawave.buildlogic.BuildPandaEngineAndroidTask
import com.adrianrusu.pandawave.buildlogic.PandaEngineCargoMutex
import org.gradle.api.tasks.Sync

plugins {
    id("pandawave.android.library")
}

data class PandaEngineAndroidTarget(val abi: String, val rustTarget: String, val linkerPrefix: String)

val pandaEngineAndroidApi = 33
val pandaEngineLibraryName = "libpanda_engine_ffi.so"
val pandaEngineAndroidNdkVersion = providers.gradleProperty("pandaEngine.androidNdkVersion").get()
val pandaEngineGeneratedJniLibsDir = layout.buildDirectory.dir("generated/panda-engine/jniLibs")
val cargoExecutableProvider = providers.environmentVariable("CARGO").orElse("cargo")
val rustcExecutableProvider = providers.environmentVariable("RUSTC").orElse("rustc")
val pandaEngineBuildNative =
    providers.gradleProperty("pandaEngine.buildNative")
        .map(String::toBoolean)
        .orElse(true)

val pandaEngineAndroidTargets =
    mapOf(
        "Arm64V8a" to
            PandaEngineAndroidTarget(
                abi = "arm64-v8a",
                rustTarget = "aarch64-linux-android",
                linkerPrefix = "aarch64-linux-android"
            ),
        "ArmeabiV7a" to
            PandaEngineAndroidTarget(
                abi = "armeabi-v7a",
                rustTarget = "armv7-linux-androideabi",
                linkerPrefix = "armv7a-linux-androideabi"
            ),
        "X86" to
            PandaEngineAndroidTarget(
                abi = "x86",
                rustTarget = "i686-linux-android",
                linkerPrefix = "i686-linux-android"
            ),
        "X86_64" to
            PandaEngineAndroidTarget(
                abi = "x86_64",
                rustTarget = "x86_64-linux-android",
                linkerPrefix = "x86_64-linux-android"
            )
    )

android {
    ndkVersion = pandaEngineAndroidNdkVersion

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    if (pandaEngineBuildNative.get()) {
        sourceSets {
            getByName("main") {
                jniLibs.directories.add(pandaEngineGeneratedJniLibsDir.get().asFile.absolutePath)
            }
        }
    }
}

// Cargo serializes on the shared package cache and target dir; keep ABI tasks exclusive.
val pandaEngineCargoMutex =
    gradle.sharedServices.registerIfAbsent("pandaEngineCargoMutex", PandaEngineCargoMutex::class.java) {
        maxParallelUsages.set(1)
    }

val buildPandaEngineAndroidTargetTasks =
    pandaEngineAndroidTargets.map { (taskSuffix, target) ->
        tasks.register<BuildPandaEngineAndroidTask>("buildPandaEngineAndroid$taskSuffix") {
            group = "build"
            description = "Builds $pandaEngineLibraryName for ${target.abi}."
            usesService(pandaEngineCargoMutex)
            cargoExecutable.set(cargoExecutableProvider)
            rustcExecutable.set(rustcExecutableProvider)
            rustTarget.set(target.rustTarget)
            androidAbi.set(target.abi)
            androidNdkVersion.set(pandaEngineAndroidNdkVersion)
            androidApi.set(pandaEngineAndroidApi)
            linkerPrefix.set(target.linkerPrefix)
            androidNdkHome.set(providers.environmentVariable("ANDROID_NDK_HOME"))
            androidNdkRoot.set(providers.environmentVariable("ANDROID_NDK_ROOT"))
            androidSdkRoot.set(providers.environmentVariable("ANDROID_SDK_ROOT"))
            androidHome.set(providers.environmentVariable("ANDROID_HOME"))
            engineDirectory.set(rootProject.layout.projectDirectory.dir("rust/engine"))
            val localProperties = rootProject.layout.projectDirectory.file("local.properties")
            if (localProperties.asFile.isFile) {
                localPropertiesFile.set(localProperties)
            }
            rustSources.from(
                fileTree(rootProject.layout.projectDirectory.dir("rust/engine")) {
                    include("Cargo.toml")
                    include("Cargo.lock")
                    include("crates/**")
                }
            )
            outputLibrary.set(
                rootProject.layout.projectDirectory.file(
                    "rust/engine/target/${target.rustTarget}/release/$pandaEngineLibraryName"
                )
            )
        }
    }

tasks.register("buildPandaEngineAndroid") {
    group = "build"
    description = "Builds PandaEngine native libraries for all supported Android ABIs."
    dependsOn(buildPandaEngineAndroidTargetTasks)
}

val syncPandaEngineAndroidJniLibs =
    tasks.register<Sync>("syncPandaEngineAndroidJniLibs") {
        group = "build"
        description = "Copies built PandaEngine native libraries into generated Android jniLibs."
        dependsOn(buildPandaEngineAndroidTargetTasks)
        into(pandaEngineGeneratedJniLibsDir)

        pandaEngineAndroidTargets.values.forEach { target ->
            from(
                rootProject.layout.projectDirectory.file(
                    "rust/engine/target/${target.rustTarget}/release/$pandaEngineLibraryName"
                )
            ) {
                into(target.abi)
            }
        }
    }

if (pandaEngineBuildNative.get()) {
    tasks.named("preBuild") {
        dependsOn(syncPandaEngineAndroidJniLibs)
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:secure-storage-adapter"))
    implementation(project(":core:telemetry-adapter"))

    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(project(":core:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
