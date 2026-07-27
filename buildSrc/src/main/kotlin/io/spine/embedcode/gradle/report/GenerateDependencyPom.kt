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
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Generates the root dependency POM used as repository documentation.
 *
 * The POM lists first-level external dependencies declared by the plugin module across all
 * configurations. It describes the build rather than a Maven publication and is not suitable
 * for Maven build tasks.
 */
@DisableCachingByDefault(
    because = "The task reads Gradle configuration metadata that is not modeled as task inputs.",
)
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

    /** Generated aggregate dependency POM. */
    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    private lateinit var dependencyConfigurations: ConfigurationContainer

    /**
     * Selects the configurations whose direct external dependencies enter the report.
     */
    public fun dependenciesFrom(configurations: ConfigurationContainer) {
        dependencyConfigurations = configurations
    }

    /** Resolves dependency versions and writes the aggregate report. */
    @TaskAction
    public fun generate() {
        check(::dependencyConfigurations.isInitialized) {
            "No dependency configurations were supplied for the aggregate POM."
        }
        val dependencies = collectDependencies(dependencyConfigurations)
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
    val xmlValue: String?,
    val outputOrder: Int,
    val selectionOrder: Int,
) {
    COMPILE("compile", 0, 0),
    RUNTIME("runtime", 1, 1),
    TEST("test", 2, 3),
    PROVIDED("provided", 3, 2),
    UNDEFINED(null, 4, 4),
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

internal fun collectDependencies(
    configurations: ConfigurationContainer,
): List<PomDependency> {
    val resolvedVersions = resolveDirectVersions(configurations)
    val declarations =
        configurations
            .sortedBy { it.name }
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
    return selectDependencies(declarations, resolvedVersions)
}

private fun resolveDirectVersions(
    configurations: ConfigurationContainer,
): Map<ModuleId, List<String>> {
    val versions = mutableMapOf<ModuleId, MutableSet<String>>()
    configurations
        .filter { it.isCanBeResolved }
        .forEach { configuration ->
            configuration.incoming.resolutionResult.rootComponent
                .get()
                .dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .mapNotNull { dependency -> dependency.selected.moduleVersion }
                .forEach { module ->
                    val id = ModuleId(module.group, module.name)
                    versions.getOrPut(id, ::mutableSetOf).add(module.version)
                }
        }
    return versions.mapValues { (_, values) -> values.sorted() }
}

private fun selectDependencies(
    declarations: List<DeclaredDependency>,
    resolvedVersions: Map<ModuleId, List<String>>,
): List<PomDependency> =
    declarations
        .map { declaration ->
            val id = ModuleId(declaration.group, declaration.artifact)
            val version =
                resolvedVersions[id]?.maxOrNull()
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
            versions
                .filter { dependency ->
                    dependency.version == versions.maxOf(PomDependency::version)
                }
                .minBy { dependency -> dependency.scope.selectionOrder }
        }
        .sortedWith(
            compareBy<PomDependency> { dependency -> dependency.scope.outputOrder }
                .thenBy { dependency -> dependency.group }
                .thenBy { dependency -> dependency.artifact }
                .thenBy { dependency -> dependency.version },
        )

internal fun mavenScope(configurationName: String): MavenScope =
    when {
        configurationName in setOf("compile", "implementation", "api") ->
            MavenScope.COMPILE
        configurationName in setOf("runtime", "runtimeOnly", "runtimeClasspath", "default") ->
            MavenScope.RUNTIME
        configurationName.startsWith("test", ignoreCase = true) ||
            configurationName.startsWith("functionalTest", ignoreCase = true) ->
            MavenScope.TEST
        configurationName in setOf("compileOnly", "compileOnlyApi", "annotationProcessor") ->
            MavenScope.PROVIDED
        else ->
            MavenScope.UNDEFINED
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
            dependency.scope.xmlValue?.let { scope ->
                appendLine("      <scope>$scope</scope>")
            }
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
