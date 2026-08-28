import com.adrianrusu.pandawave.buildlogic.BuildPandaEngineAndroidTask
import com.adrianrusu.pandawave.buildlogic.PandaEngineCargoMutex
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider

plugins {
    id("pandawave.android.library")
}

data class PandaEngineAndroidTarget(val abi: String, val rustTarget: String, val linkerPrefix: String)

val pandaEngineAndroidApi = 33
val pandaEngineLibraryName = "libpanda_engine_ffi.so"
val pandaEngineAndroidNdkVersion = providers.gradleProperty("pandaEngine.androidNdkVersion").get()
val pandaEngineGeneratedDebugJniLibsDir =
    layout.buildDirectory.dir("generated/panda-engine/debug/jniLibs")
val pandaEngineGeneratedReleaseJniLibsDir =
    layout.buildDirectory.dir("generated/panda-engine/release/jniLibs")
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
val pandaEngineTargetsByAbi = pandaEngineAndroidTargets.values.associateBy { it.abi }
val pandaEngineTaskSuffixByAbi =
    pandaEngineAndroidTargets.entries.associate { (suffix, target) -> target.abi to suffix }

fun parseAbiList(propertyName: String, defaultValue: String): List<String> {
    val raw = providers.gradleProperty(propertyName).orElse(defaultValue).get()
    val abis = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    require(abis.isNotEmpty()) { "$propertyName must list at least one ABI." }
    abis.forEach { abi ->
        require(pandaEngineTargetsByAbi.containsKey(abi)) {
            "Unknown ABI '$abi' in $propertyName. Supported: " +
                pandaEngineTargetsByAbi.keys.sorted().joinToString()
        }
    }
    return abis
}

val pandaEngineDebugAbis = parseAbiList("pandaEngine.debugAbis", "x86_64")
val pandaEngineReleaseAbis =
    parseAbiList("pandaEngine.releaseAbis", "arm64-v8a,armeabi-v7a,x86,x86_64")

android {
    if (pandaEngineBuildNative.get()) {
        ndkVersion = pandaEngineAndroidNdkVersion
    }

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        aidl = true
    }

    if (pandaEngineBuildNative.get()) {
        sourceSets {
            getByName("debug") {
                jniLibs.directories.add(
                    pandaEngineGeneratedDebugJniLibsDir.get().asFile.absolutePath
                )
            }
            getByName("release") {
                jniLibs.directories.add(
                    pandaEngineGeneratedReleaseJniLibsDir.get().asFile.absolutePath
                )
            }
        }
    }
}

// Cargo serializes on the shared package cache and target dir; keep ABI tasks exclusive.
val pandaEngineCargoMutex =
    gradle.sharedServices.registerIfAbsent("pandaEngineCargoMutex", PandaEngineCargoMutex::class.java) {
        maxParallelUsages.set(1)
    }

fun pandaEngineRustSources() =
    fileTree(rootProject.layout.projectDirectory.dir("rust/engine")) {
        include("Cargo.toml")
        include("Cargo.lock")
        include("crates/**")
    }

fun registerPandaEngineNativeBuildTasks(
    variantLabel: String,
    requestedCargoProfile: String,
    abis: List<String>
): List<TaskProvider<BuildPandaEngineAndroidTask>> =
    abis.map { abi ->
        val target = pandaEngineTargetsByAbi.getValue(abi)
        val taskSuffix = pandaEngineTaskSuffixByAbi.getValue(abi)
        tasks.register<BuildPandaEngineAndroidTask>(
            "buildPandaEngineAndroid$variantLabel$taskSuffix"
        ) {
            group = "build"
            description =
                "Builds $pandaEngineLibraryName for ${target.abi} using Cargo profile $requestedCargoProfile."
            usesService(pandaEngineCargoMutex)
            cargoExecutable.set(cargoExecutableProvider)
            rustcExecutable.set(rustcExecutableProvider)
            cargoProfile.set(requestedCargoProfile)
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
            rustSources.from(pandaEngineRustSources())
            outputLibrary.set(
                rootProject.layout.projectDirectory.file(
                    "rust/engine/target/${target.rustTarget}/$requestedCargoProfile/$pandaEngineLibraryName"
                )
            )
        }
    }

val buildPandaEngineAndroidDebugTargetTasks =
    registerPandaEngineNativeBuildTasks("Debug", "android-dev", pandaEngineDebugAbis)
val buildPandaEngineAndroidReleaseTargetTasks =
    registerPandaEngineNativeBuildTasks("Release", "release", pandaEngineReleaseAbis)

tasks.register("buildPandaEngineAndroid") {
    group = "build"
    description = "Builds PandaEngine native libraries for all configured release Android ABIs."
    dependsOn(buildPandaEngineAndroidReleaseTargetTasks)
}

fun registerPandaEngineJniSync(
    taskName: String,
    taskDescription: String,
    nativeTasks: List<TaskProvider<BuildPandaEngineAndroidTask>>,
    abis: List<String>,
    destination: Provider<Directory>
): TaskProvider<Sync> =
    tasks.register<Sync>(taskName) {
        group = "build"
        description = taskDescription
        dependsOn(nativeTasks)
        into(destination)
        nativeTasks.forEachIndexed { index, nativeTask ->
            from(nativeTask.flatMap { it.outputLibrary }) {
                into(abis[index])
            }
        }
    }

val syncPandaEngineDebugJniLibs =
    registerPandaEngineJniSync(
        taskName = "syncPandaEngineDebugJniLibs",
        taskDescription =
            "Copies debug PandaEngine native libraries into generated Android jniLibs.",
        nativeTasks = buildPandaEngineAndroidDebugTargetTasks,
        abis = pandaEngineDebugAbis,
        destination = pandaEngineGeneratedDebugJniLibsDir
    )

val syncPandaEngineReleaseJniLibs =
    registerPandaEngineJniSync(
        taskName = "syncPandaEngineReleaseJniLibs",
        taskDescription =
            "Copies release PandaEngine native libraries into generated Android jniLibs.",
        nativeTasks = buildPandaEngineAndroidReleaseTargetTasks,
        abis = pandaEngineReleaseAbis,
        destination = pandaEngineGeneratedReleaseJniLibsDir
    )

if (pandaEngineBuildNative.get()) {
    tasks.matching { it.name == "preDebugBuild" }.configureEach {
        dependsOn(syncPandaEngineDebugJniLibs)
    }
    tasks.matching { it.name == "preReleaseBuild" }.configureEach {
        dependsOn(syncPandaEngineReleaseJniLibs)
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
