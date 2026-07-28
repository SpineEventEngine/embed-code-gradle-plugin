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
import java.nio.file.Path
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@DisplayName("Aggregate dependency POM generation should")
internal class GenerateDependencyPomSpec {

    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `map build configurations to representative Maven scopes`() {
        assertEquals(MavenScope.COMPILE, mavenScope("implementation"))
        assertEquals(MavenScope.RUNTIME, mavenScope("runtimeOnly"))
        assertEquals(MavenScope.TEST, mavenScope("functionalTestImplementation"))
        assertEquals(MavenScope.PROVIDED, mavenScope("compileOnly"))
        assertEquals(MavenScope.PROVIDED, mavenScope("kotlinBuildToolsApiClasspath"))
    }

    @Test
    fun `prefer a production declaration when a test configuration repeats it`() {
        val project = ProjectBuilder.builder().build()
        val compileOnly = project.configurations.create("compileOnly") {
            isCanBeResolved = false
        }
        val testImplementation = project.configurations.create("testImplementation") {
            isCanBeResolved = false
        }
        val dependency = "org.example:shared:1.0"
        compileOnly.dependencies.add(project.dependencies.create(dependency))
        testImplementation.dependencies.add(project.dependencies.create(dependency))

        val pom = generatePom(project)

        assertEquals(1, "<dependency>".toRegex().findAll(pom).count())
        assertTrue(pom.contains("<artifactId>shared</artifactId>"))
        assertTrue(pom.contains("<scope>provided</scope>"))
    }

    @Test
    fun `use the resolved version from a configured dependency source`() {
        val project = ProjectBuilder.builder().build()
        val implementation = project.configurations.create("implementation") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        createMavenModule("org.example", "shared", "1.10")
        project.repositories.maven(
            Action<MavenArtifactRepository> {
                setUrl(projectDirectory.resolve("repository").toUri())
            },
        )
        implementation.resolutionStrategy.force("org.example:shared:1.10")
        implementation.dependencies.add(project.dependencies.create("org.example:shared:1.9"))

        val pom = generatePom(project)

        assertTrue(pom.contains("<version>1.10</version>"))
        assertFalse(pom.contains("<version>1.9</version>"))
    }

    @Test
    fun `compare numeric version segments numerically`() {
        assertTrue(dependencyVersionComparator.compare("1.10", "1.9") > 0)
    }

    @Test
    fun `round trip task input fields with separators and empty values`() {
        val fields =
            listOf(
                "org.example",
                "shared:fixtures",
                "",
                "functionalTest:implementation",
            )

        val decoded =
            encodeTaskInput(*fields.toTypedArray())
                .decodeTaskInput(expectedFieldCount = fields.size)

        assertEquals(fields, decoded)
    }

    @Test
    fun `reject malformed encoded task input`() {
        val malformedInputs =
            listOf(
                "3abc",
                "x:abc",
                "4:abc",
                "3:abc1:x",
            )

        malformedInputs.forEach { input ->
            assertThrows(IllegalStateException::class.java) {
                input.decodeTaskInput(expectedFieldCount = 1)
            }
        }
    }

    @Test
    fun `describe direct build dependencies without claiming to be a Maven build`() {
        val pom =
            renderDependencyPom(
                coordinates =
                    PomCoordinates(
                        group = "io.spine.tools",
                        artifact = "embed-code-gradle-plugin",
                        version = "0.1.1",
                    ),
                dependencies =
                    listOf(
                        PomDependency(
                            group = "org.example",
                            artifact = "runtime",
                            version = "2.0",
                            scope = MavenScope.RUNTIME,
                        ),
                        PomDependency(
                            group = "org.example",
                            artifact = "tooling",
                            version = "1.0",
                            scope = MavenScope.PROVIDED,
                        ),
                    ),
            )

        assertTrue(pom.contains("<artifactId>embed-code-gradle-plugin</artifactId>"))
        assertTrue(pom.contains("<artifactId>runtime</artifactId>"))
        assertTrue(pom.contains("<scope>runtime</scope>"))
        assertTrue(pom.contains("<artifactId>tooling</artifactId>"))
        assertTrue(pom.contains("<scope>provided</scope>"))
        assertTrue(pom.contains("is not suitable for Maven build tasks"))
    }

    private fun generatePom(project: Project): String {
        val task =
            project.tasks
                .register("generateDependencyPom", GenerateDependencyPom::class.java)
                .get()
        task.groupId.set("io.spine.tools")
        task.artifactId.set("embed-code-gradle-plugin")
        task.projectVersion.set("0.1.1")
        task.outputFile.set(project.layout.buildDirectory.file("reports/dependencies/pom.xml"))
        task.dependenciesFrom(project.configurations)

        task.generate()

        return Files.readString(task.outputFile.get().asFile.toPath(), UTF_8)
    }

    private fun createMavenModule(group: String, artifact: String, version: String) {
        val moduleDirectory =
            projectDirectory
                .resolve("repository")
                .resolve(group.replace('.', '/'))
                .resolve(artifact)
                .resolve(version)
        Files.createDirectories(moduleDirectory)
        Files.writeString(
            moduleDirectory.resolve("$artifact-$version.pom"),
            """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>$group</groupId>
              <artifactId>$artifact</artifactId>
              <version>$version</version>
            </project>
            """.trimIndent(),
            UTF_8,
        )
    }
}
