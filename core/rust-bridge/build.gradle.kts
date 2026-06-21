import java.util.Locale
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync

plugins {
    id("mediaapp.android.library")
}

data class PandaEngineAndroidTarget(val abi: String, val rustTarget: String, val linkerPrefix: String)

val pandaEngineAndroidApi = 33
val pandaEngineLibraryName = "libpanda_engine_ffi.so"
val pandaEngineAndroidNdkVersion = providers.gradleProperty("pandaEngine.androidNdkVersion").get()
val pandaEngineGeneratedJniLibsDir = layout.buildDirectory.dir("generated/panda-engine/jniLibs")
val cargoExecutable = providers.environmentVariable("CARGO").orElse("cargo")
val rustcExecutable = providers.environmentVariable("RUSTC").orElse("rustc")
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

    if (pandaEngineBuildNative.get()) {
        sourceSets {
            getByName("main") {
                jniLibs.directories.add(pandaEngineGeneratedJniLibsDir.get().asFile.absolutePath)
            }
        }
    }
}

fun findAndroidNdkDirectory(): File? {
    listOfNotNull(
        System.getenv("ANDROID_NDK_HOME"),
        System.getenv("ANDROID_NDK_ROOT")
    ).map(::File)
        .mapNotNull(::resolveAndroidNdkDirectory)
        .firstOrNull()
        ?.let { return it }

    listOfNotNull(
        System.getenv("ANDROID_SDK_ROOT"),
        System.getenv("ANDROID_HOME")
    ).map(::File)
        .map { it.resolve("ndk/$pandaEngineAndroidNdkVersion") }
        .firstOrNull { it.resolve("toolchains/llvm/prebuilt").isDirectory }
        ?.let { return it }

    val localPropertiesFile = rootProject.file("local.properties")
    if (!localPropertiesFile.isFile) return null

    val localProperties = Properties()
    localPropertiesFile.inputStream().use(localProperties::load)
    val sdkDirectory = localProperties.getProperty("sdk.dir")?.let(::File) ?: return null
    if (!sdkDirectory.isDirectory) return null

    val versionedNdkDirectory = sdkDirectory.resolve("ndk/$pandaEngineAndroidNdkVersion")
    if (versionedNdkDirectory.resolve("toolchains/llvm/prebuilt").isDirectory) {
        return versionedNdkDirectory
    }

    return null
}

fun resolveAndroidNdkDirectory(candidate: File): File? {
    if (!candidate.isDirectory) return null

    val toolchainDirectory = candidate.resolve("toolchains/llvm/prebuilt")
    if (toolchainDirectory.isDirectory) return candidate

    return candidate
        .listFiles(File::isDirectory)
        ?.filter { it.resolve("toolchains/llvm/prebuilt").isDirectory }
        ?.maxByOrNull(File::getName)
}

fun androidNdkHostTag(): String {
    val osName = System.getProperty("os.name").lowercase(Locale.US)
    val osArch = System.getProperty("os.arch").lowercase(Locale.US)

    return when {
        osName.contains("windows") -> "windows-x86_64"
        osName.contains("mac") && osArch.contains("aarch64") -> "darwin-arm64"
        osName.contains("mac") -> "darwin-x86_64"
        else -> "linux-x86_64"
    }
}

fun PandaEngineAndroidTarget.linkerExecutable(ndkDirectory: File): File {
    val executableSuffix = if (System.getProperty("os.name").contains("Windows", ignoreCase = true)) ".cmd" else ""
    return ndkDirectory.resolve(
        "toolchains/llvm/prebuilt/${androidNdkHostTag()}/bin/" +
            "$linkerPrefix$pandaEngineAndroidApi-clang$executableSuffix"
    )
}

fun rustTargetLibDirectory(rustTarget: String): File {
    val process =
        ProcessBuilder(
            rustcExecutable.get(),
            "--print",
            "target-libdir",
            "--target",
            rustTarget
        ).start()
    val output = process.inputStream.bufferedReader().readText().trim()
    val errors = process.errorStream.bufferedReader().readText().trim()
    val exitCode = process.waitFor()

    if (exitCode != 0) {
        throw GradleException(
            "Unable to inspect Rust target $rustTarget. " +
                "Install rustup/rustc or set RUSTC. $errors"
        )
    }

    return File(output)
}

val buildPandaEngineAndroidTargetTasks =
    pandaEngineAndroidTargets.map { (taskSuffix, target) ->
        tasks.register<Exec>("buildPandaEngineAndroid$taskSuffix") {
            group = "build"
            description = "Builds $pandaEngineLibraryName for ${target.abi}."
            workingDir = rootProject.layout.projectDirectory.dir("rust/engine").asFile
            commandLine(
                cargoExecutable.get(),
                "build",
                "-p",
                "panda_engine_ffi",
                "--release",
                "--target",
                target.rustTarget
            )
            inputs.files(
                fileTree(rootProject.layout.projectDirectory.dir("rust/engine")) {
                    include("Cargo.toml")
                    include("Cargo.lock")
                    include("crates/**")
                }
            )
            outputs.file(
                rootProject.layout.projectDirectory.file(
                    "rust/engine/target/${target.rustTarget}/release/$pandaEngineLibraryName"
                )
            )

            doFirst {
                val targetLibDirectory = rustTargetLibDirectory(target.rustTarget)
                if (!targetLibDirectory.isDirectory) {
                    throw GradleException(
                        "Rust target ${target.rustTarget} is required to build PandaEngine for ${target.abi}. " +
                            "Install it with: rustup target add ${target.rustTarget}"
                    )
                }

                val ndkDirectory =
                    findAndroidNdkDirectory()
                        ?: throw GradleException(
                            "Android NDK $pandaEngineAndroidNdkVersion is required to build " +
                                "PandaEngine native libraries. Install it through Android Studio " +
                                "or set ANDROID_NDK_HOME."
                        )
                val linker = target.linkerExecutable(ndkDirectory)
                if (!linker.isFile) {
                    throw GradleException("Android NDK linker was not found: ${linker.absolutePath}")
                }

                environment(
                    "CARGO_TARGET_${target.rustTarget.uppercase(Locale.US).replace('-', '_')}_LINKER",
                    linker.absolutePath
                )
            }
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
    implementation(project(":core:telemetry-adapter"))

    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
