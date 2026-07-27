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

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Aggregate dependency POM generation should")
internal class GenerateDependencyPomSpec {

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

        val dependencies = collectDependencies(project.configurations)

        assertEquals(1, dependencies.size)
        assertEquals(MavenScope.PROVIDED, dependencies.single().scope)
    }

    @Test
    fun `compare numeric version segments when selecting a declared version`() {
        val project = ProjectBuilder.builder().build()
        val implementation = project.configurations.create("implementation") {
            isCanBeResolved = false
        }
        val testImplementation = project.configurations.create("testImplementation") {
            isCanBeResolved = false
        }
        implementation.dependencies.add(project.dependencies.create("org.example:shared:1.9"))
        testImplementation.dependencies.add(
            project.dependencies.create("org.example:shared:1.10"),
        )

        val dependency = collectDependencies(project.configurations).single()

        assertEquals("1.10", dependency.version)
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
}
