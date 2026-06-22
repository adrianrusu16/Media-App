package com.adrianrusu.pandawave.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import java.util.regex.Pattern

abstract class VerifyPandaWaveIdentityTask extends DefaultTask {
    private static final Set<String> TEXT_EXTENSIONS = [
        "aidl", "groovy", "kt", "kts", "properties", "rs", "toml", "xml"
    ] as Set

    private static final Map<String, Pattern> LEGACY_IDENTITIES = [
        javaPackage : literal("com.adrianrusu." + "mediaapp"),
        jvmPath     : literal("com/adrianrusu/" + "mediaapp"),
        jniExport   : literal("Java_com_adrianrusu_" + "mediaapp" + "_"),
        gradlePlugin: literal("mediaapp" + ".android."),
        rustPackage : literal("media_app" + "_core"),
        storageAlias: Pattern.compile(/\bmedia_app\.[a-z0-9_.-]+/)
    ].asImmutable()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getIdentitySources()

    @TaskAction
    void verifyIdentity() {
        List<String> violations = []

        identitySources.files
            .findAll { File file -> file.isFile() && TEXT_EXTENSIONS.contains(this.fileExtension(file)) }
            .sort()
            .each { File file -> scan(file, violations) }

        if (!violations.isEmpty()) {
            throw new GradleException(
                "Legacy PandaWave identity violations (${violations.size()}):\n" +
                    violations.collect { "  - ${it}" }.join("\n")
            )
        }
    }

    protected void scan(File file, List<String> violations) {
        String relativePath = relativePath(file)
        file.readLines("UTF-8").eachWithIndex { String line, int index ->
            LEGACY_IDENTITIES.each { String rule, Pattern pattern ->
                if (pattern.matcher(line).find()) {
                    violations.add("${relativePath}:${index + 1} [${rule}] ${line.trim()}")
                }
            }
        }
    }

    protected String relativePath(File file) {
        return project.rootDir.toPath().relativize(file.toPath()).toString().replace('\\', '/')
    }

    private static Pattern literal(String value) {
        return Pattern.compile(Pattern.quote(value))
    }

    protected String fileExtension(File file) {
        int separator = file.name.lastIndexOf('.')
        return separator >= 0 ? file.name.substring(separator + 1) : ""
    }
}
