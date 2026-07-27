/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.embedcode.gradle.report

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Generates the root dependency POM used as repository documentation.
 *
 * The POM lists first-level external dependencies declared by the plugin module across all
 * configurations. It describes the build rather than a Maven publication and is not suitable
 * for Maven build tasks.
 */
@CacheableTask
public abstract class GenerateDependencyPom : DefaultTask() {

    /** Group identifier written to the report. */
    @get:Input
    public abstract val groupId: Property<String>

    /** Artifact identifier written to the report. */
    @get:Input
    public abstract val artifactId: Property<String>

    /** Project version written to the report. */
    @get:Input
    public abstract val projectVersion: Property<String>

    /** Encoded direct external dependency declarations. */
    @get:Input
    public abstract val dependencyDeclarations: ListProperty<String>

    /** Encoded direct dependency versions from resolvable configurations. */
    @get:Input
    public abstract val resolvedDependencyVersions: ListProperty<String>

    /** Generated aggregate dependency POM. */
    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    /**
     * Selects the configurations whose direct external dependencies enter the report.
     *
     * Captures declarations when the task is configured and wires resolved versions through
     * providers, so task execution does not retain or query the Gradle configuration model.
     */
    public fun dependenciesFrom(configurations: ConfigurationContainer) {
        dependencyDeclarations.set(
            collectDeclarations(configurations).map(DeclaredDependency::toTaskInput),
        )
        resolvedDependencyVersions.set(emptyList())
        configurations
            .filter { configuration -> configuration.isCanBeResolved }
            .sortedBy { configuration -> configuration.name }
            .forEach { configuration ->
                val configurationName = configuration.name
                val resolvedVersions =
                    configuration.incoming.resolutionResult.rootComponent.map { root ->
                        directResolvedVersions(root)
                            .sortedWith(resolvedVersionComparator)
                    }
                dependencyDeclarations.addAll(
                    resolvedVersions.map { versions ->
                        versions.map { version ->
                            DeclaredDependency(
                                group = version.group,
                                artifact = version.artifact,
                                configuredVersion = version.version,
                                configurationName = configurationName,
                            ).toTaskInput()
                        }
                    },
                )
                resolvedDependencyVersions.addAll(
                    resolvedVersions.map { versions ->
                        versions.map(ResolvedVersion::toTaskInput)
                    },
                )
            }
    }

    /** Writes the aggregate report from the dependency snapshot. */
    @TaskAction
    public fun generate() {
        val declarations =
            dependencyDeclarations
                .get()
                .map(String::toDeclaredDependency)
        val resolvedVersions =
            resolvedDependencyVersions
                .get()
                .map(String::toResolvedVersion)
                .groupBy(ResolvedVersion::moduleId, ResolvedVersion::version)
        val dependencies = selectDependencies(declarations, resolvedVersions)
        val pom =
            renderDependencyPom(
                coordinates =
                    PomCoordinates(
                        group = groupId.get(),
                        artifact = artifactId.get(),
                        version = projectVersion.get(),
                    ),
                dependencies = dependencies,
            )
        val destination = outputFile.get().asFile.toPath()
        Files.createDirectories(destination.parent)
        Files.writeString(destination, pom, UTF_8)
    }
}

/** Coordinates that identify the project described by the aggregate POM. */
internal data class PomCoordinates(
    val group: String,
    val artifact: String,
    val version: String,
)

/** A first-level external dependency and its representative Maven scope. */
internal data class PomDependency(
    val group: String,
    val artifact: String,
    val version: String,
    val scope: MavenScope,
)

/** Maven scope used to group dependencies in the generated report. */
internal enum class MavenScope(
    val xmlValue: String,
    val outputOrder: Int,
    val selectionOrder: Int,
) {
    COMPILE("compile", 0, 0),
    RUNTIME("runtime", 1, 1),
    TEST("test", 2, 3),
    PROVIDED("provided", 3, 2),
}

/** A dependency declaration and the Gradle configuration that owns it. */
private data class DeclaredDependency(
    val group: String,
    val artifact: String,
    val configuredVersion: String?,
    val configurationName: String,
)

/** Group and artifact identity used while resolving configured versions. */
private data class ModuleId(
    val group: String,
    val artifact: String,
)

/** A resolved first-level dependency version. */
private data class ResolvedVersion(
    val group: String,
    val artifact: String,
    val version: String,
) {
    val moduleId: ModuleId
        get() = ModuleId(group, artifact)
}

internal fun collectDependencies(
    configurations: ConfigurationContainer,
): List<PomDependency> {
    val resolvedVersions = resolveDirectVersions(configurations)
    val declarations = collectDeclarations(configurations)
    return selectDependencies(declarations, resolvedVersions)
}

private fun collectDeclarations(
    configurations: ConfigurationContainer,
): List<DeclaredDependency> =
    configurations
        .sortedBy { configuration -> configuration.name }
        .flatMap { configuration ->
            configuration.dependencies
                .withType(ExternalModuleDependency::class.java)
                .map { dependency ->
                    DeclaredDependency(
                        group = checkNotNull(dependency.group),
                        artifact = dependency.name,
                        configuredVersion =
                            dependency.version
                                ?: dependency.versionConstraint.requiredVersion
                                    .takeIf { it.isNotEmpty() },
                        configurationName = configuration.name,
                    )
                }
        }

private fun resolveDirectVersions(
    configurations: ConfigurationContainer,
): Map<ModuleId, List<String>> {
    val versions =
        configurations
            .filter { it.isCanBeResolved }
            .flatMap { configuration ->
                directResolvedVersions(
                    configuration.incoming.resolutionResult.rootComponent.get(),
                )
            }
    return versions
        .distinct()
        .groupBy(ResolvedVersion::moduleId, ResolvedVersion::version)
}

private fun directResolvedVersions(
    root: ResolvedComponentResult,
): List<ResolvedVersion> =
    root.dependencies
        .filterIsInstance<ResolvedDependencyResult>()
        .mapNotNull { dependency -> dependency.selected.moduleVersion }
        .map { module ->
            ResolvedVersion(
                group = module.group,
                artifact = module.name,
                version = module.version,
            )
        }

private fun selectDependencies(
    declarations: List<DeclaredDependency>,
    resolvedVersions: Map<ModuleId, List<String>>,
): List<PomDependency> =
    declarations
        .map { declaration ->
            val id = ModuleId(declaration.group, declaration.artifact)
            val version =
                resolvedVersions[id]?.maxWithOrNull(dependencyVersionComparator)
                    ?: declaration.configuredVersion
                    ?: error(
                        "Cannot determine the version of " +
                            "`${declaration.group}:${declaration.artifact}` declared in " +
                            "`${declaration.configurationName}`.",
                    )
            PomDependency(
                group = declaration.group,
                artifact = declaration.artifact,
                version = version,
                scope = mavenScope(declaration.configurationName),
            )
        }
        .groupBy { dependency -> dependency.group to dependency.artifact }
        .map { (_, versions) ->
            val selectedVersion =
                checkNotNull(
                    versions
                        .map(PomDependency::version)
                        .maxWithOrNull(dependencyVersionComparator),
                )
            versions
                .filter { dependency ->
                    dependency.version == selectedVersion
                }
                .minBy { dependency -> dependency.scope.selectionOrder }
        }
        .sortedWith(
            compareBy<PomDependency> { dependency -> dependency.scope.outputOrder }
                .thenBy { dependency -> dependency.group }
                .thenBy { dependency -> dependency.artifact }
        )

internal fun mavenScope(configurationName: String): MavenScope =
    when (configurationName) {
        "implementation",
        "api",
        ->
            MavenScope.COMPILE
        "runtimeOnly",
        "runtimeClasspath",
        "default",
        ->
            MavenScope.RUNTIME
        "compileOnly",
        "compileOnlyApi",
        "annotationProcessor",
        ->
            MavenScope.PROVIDED
        else -> {
            if (
                configurationName.startsWith("test", ignoreCase = true) ||
                    configurationName.startsWith("functionalTest", ignoreCase = true)
            ) {
                MavenScope.TEST
            } else {
                MavenScope.PROVIDED
            }
        }
    }

internal fun renderDependencyPom(
    coordinates: PomCoordinates,
    dependencies: List<PomDependency>,
): String =
    buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<project xmlns="http://maven.apache.org/POM/4.0.0"""")
        appendLine("""    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"""")
        appendLine(
            """    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 """ +
                """https://maven.apache.org/xsd/maven-4.0.0.xsd">""",
        )
        appendLine("  <modelVersion>4.0.0</modelVersion>")
        appendLine("  <!--")
        appendLine("    This file is generated by the `generateDependencyReports` Gradle task.")
        appendLine("    It documents first-level dependencies from all plugin-module")
        appendLine("    configurations and is not suitable for Maven build tasks.")
        appendLine("  -->")
        appendLine("  <groupId>${coordinates.group.escapeXml()}</groupId>")
        appendLine("  <artifactId>${coordinates.artifact.escapeXml()}</artifactId>")
        appendLine("  <version>${coordinates.version.escapeXml()}</version>")
        appendLine("  <licenses>")
        appendLine("    <license>")
        appendLine("      <name>The Apache License, Version 2.0</name>")
        appendLine("      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>")
        appendLine("      <distribution>repo</distribution>")
        appendLine("    </license>")
        appendLine("  </licenses>")
        appendLine("  <dependencies>")
        dependencies.forEach { dependency ->
            appendLine("    <dependency>")
            appendLine("      <groupId>${dependency.group.escapeXml()}</groupId>")
            appendLine("      <artifactId>${dependency.artifact.escapeXml()}</artifactId>")
            appendLine("      <version>${dependency.version.escapeXml()}</version>")
            appendLine("      <scope>${dependency.scope.xmlValue}</scope>")
            appendLine("    </dependency>")
        }
        appendLine("  </dependencies>")
        appendLine("</project>")
    }

private fun String.escapeXml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

private val versionSegmentPattern = Regex("""\d+|\D+""")

private val dependencyVersionComparator =
    Comparator<String> { first, second ->
        val firstSegments = versionSegmentPattern.findAll(first).map(MatchResult::value).toList()
        val secondSegments = versionSegmentPattern.findAll(second).map(MatchResult::value).toList()
        firstSegments
            .zip(secondSegments)
            .firstNotNullOfOrNull { (firstSegment, secondSegment) ->
                compareVersionSegments(firstSegment, secondSegment).takeIf { it != 0 }
            }
            ?: firstSegments.size.compareTo(secondSegments.size)
    }

private val resolvedVersionComparator =
    compareBy<ResolvedVersion>(ResolvedVersion::group)
        .thenBy(ResolvedVersion::artifact)
        .thenComparator { first, second ->
            dependencyVersionComparator.compare(first.version, second.version)
        }

private fun compareVersionSegments(first: String, second: String): Int {
    val firstIsNumeric = first.all(Char::isDigit)
    val secondIsNumeric = second.all(Char::isDigit)
    if (!firstIsNumeric || !secondIsNumeric) {
        return first.compareTo(second)
    }
    val normalizedFirst = first.trimStart('0').ifEmpty { "0" }
    val normalizedSecond = second.trimStart('0').ifEmpty { "0" }
    return normalizedFirst.length
        .compareTo(normalizedSecond.length)
        .takeIf { it != 0 }
        ?: normalizedFirst.compareTo(normalizedSecond)
}

private fun DeclaredDependency.toTaskInput(): String =
    encodeTaskInput(
        group,
        artifact,
        configuredVersion.orEmpty(),
        configurationName,
    )

private fun ResolvedVersion.toTaskInput(): String =
    encodeTaskInput(group, artifact, version)

private fun String.toDeclaredDependency(): DeclaredDependency {
    val (group, artifact, configuredVersion, configurationName) =
        decodeTaskInput(expectedFieldCount = 4)
    return DeclaredDependency(
        group = group,
        artifact = artifact,
        configuredVersion = configuredVersion.takeIf(String::isNotEmpty),
        configurationName = configurationName,
    )
}

private fun String.toResolvedVersion(): ResolvedVersion {
    val (group, artifact, version) = decodeTaskInput(expectedFieldCount = 3)
    return ResolvedVersion(group, artifact, version)
}

private fun encodeTaskInput(vararg fields: String): String =
    buildString {
        fields.forEach { field ->
            append(field.length)
            append(':')
            append(field)
        }
    }

private fun String.decodeTaskInput(expectedFieldCount: Int): List<String> {
    val fields = ArrayList<String>(expectedFieldCount)
    var offset = 0
    repeat(expectedFieldCount) {
        val separator = indexOf(':', startIndex = offset)
        check(separator >= offset) {
            "Malformed aggregate dependency POM task input."
        }
        val fieldLength =
            substring(offset, separator).toIntOrNull()
                ?: error("Malformed aggregate dependency POM task input.")
        val fieldStart = separator + 1
        val fieldEnd = fieldStart + fieldLength
        check(fieldEnd <= length) {
            "Malformed aggregate dependency POM task input."
        }
        fields.add(substring(fieldStart, fieldEnd))
        offset = fieldEnd
    }
    check(offset == length) {
        "Malformed aggregate dependency POM task input."
    }
    return fields
}
