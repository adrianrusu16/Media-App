package com.adrianrusu.pandawave.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.process.ExecSpec
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject

@DisableCachingByDefault(because = "Cargo manages its own incremental build cache")
abstract class BuildPandaEngineAndroidTask extends DefaultTask {
    @Input
    abstract Property<String> getCargoExecutable()

    @Input
    abstract Property<String> getRustcExecutable()

    @Input
    abstract Property<String> getCargoProfile()

    @Input
    abstract Property<String> getRustTarget()

    @Input
    abstract Property<String> getAndroidAbi()

    @Input
    abstract Property<String> getAndroidNdkVersion()

    @Input
    abstract Property<Integer> getAndroidApi()

    @Input
    abstract Property<String> getLinkerPrefix()

    @Input
    @Optional
    abstract Property<String> getAndroidNdkHome()

    @Input
    @Optional
    abstract Property<String> getAndroidNdkRoot()

    @Input
    @Optional
    abstract Property<String> getAndroidSdkRoot()

    @Input
    @Optional
    abstract Property<String> getAndroidHome()

    @Internal
    abstract DirectoryProperty getEngineDirectory()

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.ABSOLUTE)
    abstract RegularFileProperty getLocalPropertiesFile()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getRustSources()

    @OutputFile
    abstract RegularFileProperty getOutputLibrary()

    private final ExecOperations execOperations

    @Inject
    BuildPandaEngineAndroidTask(ExecOperations execOperations) {
        this.execOperations = execOperations
        cargoProfile.convention("release")
    }

    @TaskAction
    void buildNativeLibrary() {
        verifyRustTargetInstalled()

        File ndkDirectory = findAndroidNdkDirectory()
        if (ndkDirectory == null) {
            throw new GradleException(
                "Android NDK ${androidNdkVersion.get()} is required to build PandaEngine native " +
                    "libraries. Install it through Android Studio or set ANDROID_NDK_HOME."
            )
        }

        File linker = linkerExecutable(ndkDirectory)
        if (!linker.isFile()) {
            throw new GradleException("Android NDK linker was not found: ${linker.absolutePath}")
        }

        String normalizedRustTarget = rustTarget.get().replace('-', '_')
        String linkerEnvironmentVariable =
            "CARGO_TARGET_${normalizedRustTarget.toUpperCase(Locale.US)}_LINKER"
        String ccEnvironmentVariable = "CC_${rustTarget.get()}"
        String normalizedCcEnvironmentVariable = "CC_${normalizedRustTarget}"
        File toolchainBin = linker.parentFile
        String inheritedPath = System.getenv("PATH") ?: ""
        String cargoPath = toolchainBin.absolutePath + File.pathSeparator + inheritedPath

        File cargoTargetDirectory = new File(engineDirectory.get().asFile, "target")

        execOperations.exec { ExecSpec spec ->
            spec.workingDir(engineDirectory.get().asFile)
            spec.environment("PATH", cargoPath)
            spec.environment("CARGO_TARGET_DIR", cargoTargetDirectory.absolutePath)
            spec.environment(ccEnvironmentVariable, linker.absolutePath)
            spec.environment(normalizedCcEnvironmentVariable, linker.absolutePath)
            spec.commandLine(
                cargoExecutable.get(),
                "build",
                "-p",
                "panda_engine_ffi",
                "--profile",
                cargoProfile.get(),
                "--target",
                rustTarget.get()
            )
            spec.environment(linkerEnvironmentVariable, linker.absolutePath)
        }
    }

    private File findAndroidNdkDirectory() {
        File explicitNdk =
            [androidNdkHome.orNull, androidNdkRoot.orNull]
                .findAll { String path -> path != null }
                .collect { String path ->
                    BuildPandaEngineAndroidTask.resolveAndroidNdkDirectory(new File(path))
                }
                .find { File directory -> directory != null }
        if (explicitNdk != null) {
            return explicitNdk
        }

        File sdkNdk =
            [androidSdkRoot.orNull, androidHome.orNull]
                .findAll { String path -> path != null }
                .collect { String path -> new File(path, "ndk/${androidNdkVersion.get()}") }
                .find { File directory -> BuildPandaEngineAndroidTask.hasLlvmToolchain(directory) }
        if (sdkNdk != null) {
            return sdkNdk
        }

        if (!localPropertiesFile.isPresent()) {
            return null
        }

        Properties localProperties = new Properties()
        localPropertiesFile.get().asFile.withInputStream { stream -> localProperties.load(stream) }
        String sdkDirectory = localProperties.getProperty("sdk.dir")
        if (sdkDirectory == null) {
            return null
        }

        File versionedNdk = new File(sdkDirectory, "ndk/${androidNdkVersion.get()}")
        return hasLlvmToolchain(versionedNdk) ? versionedNdk : null
    }

    private static File resolveAndroidNdkDirectory(File candidate) {
        if (!candidate.isDirectory()) {
            return null
        }
        if (hasLlvmToolchain(candidate)) {
            return candidate
        }

        return candidate
            .listFiles({ File file -> file.isDirectory() } as FileFilter)
            ?.findAll { File directory -> BuildPandaEngineAndroidTask.hasLlvmToolchain(directory) }
            ?.max { File left, File right -> left.name <=> right.name }
    }

    private static boolean hasLlvmToolchain(File directory) {
        return new File(directory, "toolchains/llvm/prebuilt").isDirectory()
    }

    private File linkerExecutable(File ndkDirectory) {
        String executableSuffix =
            System.getProperty("os.name").toLowerCase(Locale.US).contains("windows") ? ".cmd" : ""
        return new File(
            ndkDirectory,
            "toolchains/llvm/prebuilt/${androidNdkHostTag()}/bin/" +
                "${linkerPrefix.get()}${androidApi.get()}-clang${executableSuffix}"
        )
    }

    private static String androidNdkHostTag() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.US)
        String osArch = System.getProperty("os.arch").toLowerCase(Locale.US)

        if (osName.contains("windows")) {
            return "windows-x86_64"
        }
        if (osName.contains("mac") && osArch.contains("aarch64")) {
            return "darwin-arm64"
        }
        if (osName.contains("mac")) {
            return "darwin-x86_64"
        }
        return "linux-x86_64"
    }

    private void verifyRustTargetInstalled() {
        ByteArrayOutputStream standardOutput = new ByteArrayOutputStream()
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream()

        def result = execOperations.exec { ExecSpec spec ->
            spec.commandLine(
                rustcExecutable.get(),
                "--print",
                "target-libdir",
                "--target",
                rustTarget.get()
            )
            spec.standardOutput = standardOutput
            spec.errorOutput = errorOutput
            spec.ignoreExitValue = true
        }

        File targetLibraryDirectory = new File(standardOutput.toString("UTF-8").trim())
        if (result.exitValue != 0 || !targetLibraryDirectory.isDirectory()) {
            String details = errorOutput.toString("UTF-8").trim()
            throw new GradleException(
                "Rust target ${rustTarget.get()} is required to build PandaEngine for " +
                    "${androidAbi.get()}. Install it with: rustup target add ${rustTarget.get()}. " +
                    details
            )
        }
    }
}
