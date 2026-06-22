package com.adrianrusu.pandawave.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import java.util.regex.Pattern

abstract class VerifyPandaWaveUiContractTask extends DefaultTask {
    private static final Map<String, Pattern> FORBIDDEN_KOTLIN = [
        dpLiteral       : ~/(?<![A-Za-z0-9_])\d+(?:\.\d+)?\.dp\b/,
        spLiteral       : ~/(?<![A-Za-z0-9_])\d+(?:\.\d+)?\.sp\b/,
        packedColor     : ~/Color\(0x[0-9A-Fa-f]+\)/,
        alphaLiteral    : ~/(?:alpha|disabledAlpha)\s*=\s*\d+(?:\.\d+)?f?/,
        durationLiteral : ~/(?:durationMillis|delayMillis)\s*=\s*\d+/,
        directFontWeight: ~/FontWeight\.[A-Za-z]+/,
        staticText      : ~/(?:text|contentDescription|title|body|label)\s*=\s*"[^"\$]+"/
    ].asImmutable()

    private static final Pattern LEGACY_RESOURCE = ~/\bmediaapp_[a-z0-9_]+\b/
    private static final Set<String> ANIMATION_LABELS = ["bambooVoiceClock", "clock", "activeLevel"] as Set
    private static final List<String> TOKEN_PATH = [
        "core", "designsystem", "src", "main", "kotlin", "com", "adrianrusu", "pandawave",
        "core", "designsystem", "tokens"
    ]

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getKotlinSources()

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getResourceSources()

    @TaskAction
    void verifyContract() {
        List<String> violations = []

        kotlinSources.files.findAll { it.isFile() }.sort().each { File file ->
            this.scanKotlin(file, violations)
        }
        resourceSources.files.findAll { it.isFile() }.sort().each { File file ->
            this.scanLegacyResources(file, violations)
        }

        if (!violations.isEmpty()) {
            throw new org.gradle.api.GradleException(
                "PandaWave UI contract violations (${violations.size()}):\n" +
                    violations.collect { "  - ${it}" }.join("\n")
            )
        }
    }

    protected void scanKotlin(File file, List<String> violations) {
        String relativePath = relativePath(file)
        boolean tokenDefinition = hasPathSegments(file, TOKEN_PATH)
        boolean previewFile = file.name.contains("Preview")

        file.readLines("UTF-8").eachWithIndex { String line, int index ->
            int lineNumber = index + 1
            if (!tokenDefinition && !previewFile) {
                FORBIDDEN_KOTLIN.each { String rule, Pattern pattern ->
                    if (pattern.matcher(line).find() && !allowedKotlinLine(rule, line)) {
                        violations.add("${relativePath}:${lineNumber} [${rule}] ${line.trim()}")
                    }
                }
            }

            if (LEGACY_RESOURCE.matcher(line).find()) {
                violations.add("${relativePath}:${lineNumber} [legacyResource] ${line.trim()}")
            }

            if (relativePath.startsWith("feature/") && !previewFile) {
                if (line.contains("MaterialTheme.colorScheme")) {
                    violations.add("${relativePath}:${lineNumber} [directColorScheme] ${line.trim()}")
                }
                if (line.contains("MaterialTheme.typography")) {
                    violations.add("${relativePath}:${lineNumber} [directTypography] ${line.trim()}")
                }
            }
        }
    }

    protected void scanLegacyResources(File file, List<String> violations) {
        String relativePath = relativePath(file)
        file.readLines("UTF-8").eachWithIndex { String line, int index ->
            if (LEGACY_RESOURCE.matcher(line).find()) {
                violations.add("${relativePath}:${index + 1} [legacyResource] ${line.trim()}")
            }
        }
    }

    protected static boolean allowedKotlinLine(String rule, String line) {
        if (rule == "staticText") {
            String trimmed = line.trim()
            return trimmed.contains("testTag") ||
                trimmed.contains("debugLabel") ||
                trimmed.contains("animationLabel") ||
                trimmed.contains("transitionLabel") ||
                ANIMATION_LABELS.any { label -> trimmed.contains("\"${label}\"") }
        }
        return false
    }

    protected boolean hasPathSegments(File file, List<String> expectedSegments) {
        List<String> segments = project.rootDir.toPath().relativize(file.toPath()).collect { it.toString() }
        return segments.size() >= expectedSegments.size() &&
            segments.take(expectedSegments.size()) == expectedSegments
    }

    protected String relativePath(File file) {
        return project.rootDir.toPath().relativize(file.toPath()).toString().replace('\\', '/')
    }
}
