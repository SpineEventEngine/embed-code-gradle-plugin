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

package io.spine.embedcode.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@DisplayName("`EmbedCodePlugin` should")
internal class EmbedCodePluginIgTest {

    @TempDir
    private lateinit var projectDirectory: Path

    private lateinit var releaseDirectory: Path

    @BeforeEach
    fun setUp() {
        Files.createDirectories(projectDirectory.resolve("code"))
        Files.createDirectories(projectDirectory.resolve("docs"))
        Files.writeString(
            projectDirectory.resolve("settings.gradle.kts"),
            "rootProject.name = \"test-project\"\n",
        )

        releaseDirectory = projectDirectory.resolve("releases")
        createFakeRelease(releaseDirectory)
        writeBuildFile()
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `run check mode with Gradle configuration`() {
        val result = runner(":checkEmbedding").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":checkEmbedding")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.readString(projectDirectory.resolve("mode.txt")).trim() shouldBe "check"

        val arguments = Files.readAllLines(projectDirectory.resolve("arguments.txt"))
        arguments shouldContain "-mode=check"
        arguments shouldContain "-code-path=${projectDirectory.resolve("code").toRealPath()}"
        arguments shouldContain "-docs-path=${projectDirectory.resolve("docs").toRealPath()}"
        arguments shouldContain "-doc-includes=**/*.md,**/*.html"
        arguments shouldContain "-doc-excludes=drafts/**,generated/**"
        arguments shouldContain "-separator=---"
        arguments shouldContain "-info=true"
        arguments shouldContain "-stacktrace=true"
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `reuse the configuration cache`() {
        runner(":checkEmbedding").build()

        val result = runner(":checkEmbedding").build()

        result.output shouldContain "Reusing configuration cache."
        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":checkEmbedding")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    @Test
    fun `install platform release asset`() {
        val result = runner(":installEmbedCode").build()
        val executableName = EmbedCodePlatform.detect(
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
        ).executableName
        val installedExecutable = projectDirectory.resolve(
            "build/embed-code/latest/$executableName",
        )

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.exists(installedExecutable) shouldBe true
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `allow overriding the latest Embed Code version`() {
        val overrideVersion = "0.0.0-test"
        createFakeRelease(releaseDirectory, overrideVersion)
        writeBuildFile(overrideVersion)

        val result = runner(":checkEmbedding").build()

        result.task(":checkEmbedding")?.outcome shouldBe TaskOutcome.SUCCESS
        val executableName = EmbedCodePlatform.detect(
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
        ).executableName
        Files.exists(
            projectDirectory.resolve("build/embed-code/$overrideVersion/$executableName"),
        ) shouldBe true
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `run check mode with Gradle 7_6_3`() {
        val javaHome = System.getenv("EMBED_CODE_GRADLE_7_JAVA_HOME")
            ?: System.getenv("JAVA_HOME_17_X64")
        assumeTrue(
            !javaHome.isNullOrBlank(),
            "Set EMBED_CODE_GRADLE_7_JAVA_HOME to a JDK supported by Gradle 7.6.3.",
        )

        val result = runner(":checkEmbedding", useConfigurationCache = false)
            .withGradleVersion("7.6.3")
            .withEnvironment(System.getenv() + ("JAVA_HOME" to javaHome))
            .build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":checkEmbedding")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.readString(projectDirectory.resolve("mode.txt")).trim() shouldBe "check"
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `reuse installation when running embed mode`() {
        writeBuildFile(TEST_RELEASE_VERSION)
        runner(":checkEmbedding").build()
        releaseDirectory.toFile().deleteRecursively()

        val result = runner(":embedCode").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.UP_TO_DATE
        result.task(":embedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.readString(projectDirectory.resolve("mode.txt")).trim() shouldBe "embed"
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `run with named source roots and generated configuration`() {
        Files.createDirectories(projectDirectory.resolve("company-site"))
        Files.createDirectories(projectDirectory.resolve("browser"))
        writeNamedSourcesBuildFile()

        val result = runner(":checkEmbedding").build()

        result.task(":checkEmbedding")?.outcome shouldBe TaskOutcome.SUCCESS
        val arguments = Files.readAllLines(projectDirectory.resolve("arguments.txt"))
        arguments shouldContain "-mode=check"
        arguments.single { it.startsWith("-config-path=") }

        val configuration = Files.readString(projectDirectory.resolve("generated-config.json"))
        configuration shouldContain "\"name\": \"company-site\""
        val companySitePath = projectDirectory.resolve("company-site").toRealPath()
        val browserPath = projectDirectory.resolve("browser").toRealPath()
        configuration shouldContain "\"path\": \"$companySitePath\""
        configuration shouldContain "\"name\": \"jxbrowser\""
        configuration shouldContain "\"path\": \"$browserPath\""
        configuration shouldContain "\"docs-path\": \"${projectDirectory.toRealPath()}\""
    }

    @Test
    fun `reject direct and named source roots together`() {
        Files.createDirectories(projectDirectory.resolve("browser"))
        writeNamedSourcesBuildFile(includeDirectSource = true)

        val result = runner(":checkEmbedding").buildAndFail()

        result.output shouldContain
            "Configure exactly one of `codePath` or `namedSource(...)` for Embed Code."
    }

    @Test
    fun `report missing release asset`() {
        releaseDirectory.toFile().deleteRecursively()

        val result = runner(":checkEmbedding").buildAndFail()

        result.output shouldContain "Could not download Embed Code"
    }

    @Test
    fun `list only execution tasks under the Embed Code group`() {
        val result = runner("tasks").build()

        result.output shouldContain "Embed code tasks"
        result.output shouldContain "checkEmbedding - Checks embedded code snippets are up to date"
        result.output shouldContain "embedCode - Updates embedded code snippets from source files"
        result.output shouldNotContain "installEmbedCode"

        val allTasks = runner("tasks", "--all").build()
        allTasks.output shouldContain
            "installEmbedCode - Installs the requested Embed Code executable"
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `prepend underscores to an occupied checkEmbedding task name`() {
        Files.writeString(
            projectDirectory.resolve("settings.gradle.kts"),
            """
            rootProject.name = "test-project"

            gradle.beforeProject {
                tasks.register("checkEmbedding")
                tasks.register("_checkEmbedding")
            }
            """.trimIndent(),
        )

        val result = runner(":__checkEmbedding").build()

        result.task(":__checkEmbedding")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.readString(projectDirectory.resolve("mode.txt")).trim() shouldBe "check"
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `prepend underscores to an occupied embedCode task name`() {
        Files.writeString(
            projectDirectory.resolve("settings.gradle.kts"),
            """
            rootProject.name = "test-project"

            gradle.beforeProject {
                tasks.register("embedCode")
                tasks.register("_embedCode")
            }
            """.trimIndent(),
        )

        val result = runner(":__embedCode").build()

        result.task(":__embedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.readString(projectDirectory.resolve("mode.txt")).trim() shouldBe "embed"
    }

    /** Creates a runner using the plugin-under-test classpath. */
    private fun runner(
        vararg arguments: String,
        useConfigurationCache: Boolean = true,
    ): GradleRunner {
        val gradleArguments = arguments.toMutableList()
        if (useConfigurationCache) {
            gradleArguments.add("--configuration-cache")
        }
        gradleArguments.add("--stacktrace")
        return GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments(gradleArguments)
            .withPluginClasspath()
    }

    /** Writes a consuming build configured entirely through the plugin extension. */
    private fun writeBuildFile(version: String? = null) {
        val baseUrl = releaseDirectory.toUri().toString().trimEnd('/')
        val versionConfiguration = version?.let { "version.set(\"$it\")" }.orEmpty()
        Files.writeString(
            projectDirectory.resolve("build.gradle.kts"),
            """
            plugins {
                id("io.spine.embed-code")
            }

            embedCode {
                $versionConfiguration
                downloadBaseUrl.set("$baseUrl")
                codePath.set(layout.projectDirectory.dir("code"))
                docsPath.set(layout.projectDirectory.dir("docs"))
                docIncludes.set(listOf("**/*.md", "**/*.html"))
                docExcludes.set(listOf("drafts/**", "generated/**"))
                separator.set("---")
                info.set(true)
                stacktrace.set(true)
            }
            """.trimIndent(),
        )
    }

    /** Writes a consuming build with two named source roots and no YAML file. */
    private fun writeNamedSourcesBuildFile(includeDirectSource: Boolean = false) {
        val baseUrl = releaseDirectory.toUri().toString().trimEnd('/')
        val directSource = if (includeDirectSource) {
            "codePath.set(layout.projectDirectory.dir(\"code\"))"
        } else {
            ""
        }
        Files.writeString(
            projectDirectory.resolve("build.gradle.kts"),
            """
            plugins {
                id("io.spine.embed-code")
            }

            embedCode {
                downloadBaseUrl.set("$baseUrl")
                $directSource
                namedSource("company-site", layout.projectDirectory.dir("company-site"))
                namedSource("jxbrowser", layout.projectDirectory.dir("browser"))
                docsPath.set(layout.projectDirectory)
            }
            """.trimIndent(),
        )
    }

    /** Creates a host-specific fake release asset that records received arguments. */
    private fun createFakeRelease(root: Path, version: String = TEST_RELEASE_VERSION) {
        val platform = EmbedCodePlatform.detect(
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
        )
        val versionDirectory = root.resolve("download/v$version")
        val latestDirectory = root.resolve("latest/download")
        Files.createDirectories(versionDirectory)
        Files.createDirectories(latestDirectory)
        val executable = projectDirectory.resolve(platform.executableName)
        Files.writeString(
            executable,
            """
            #!/bin/sh
            : > arguments.txt
            for argument in "${'$'}@"; do
              printf '%s\n' "${'$'}argument" >> arguments.txt
              case "${'$'}argument" in
                -mode=check) printf 'check\n' > mode.txt ;;
                -mode=embed) printf 'embed\n' > mode.txt ;;
                -config-path=*) cp "${'$'}{argument#-config-path=}" generated-config.json ;;
              esac
            done
            """.trimIndent() + "\n",
        )

        val asset = versionDirectory.resolve(platform.assetName)
        if (platform.assetName.endsWith(".zip")) {
            ZipOutputStream(Files.newOutputStream(asset)).use { zip ->
                zip.putNextEntry(ZipEntry(platform.executableName))
                Files.newInputStream(executable).use { it.copyTo(zip) }
                zip.closeEntry()
            }
        } else {
            Files.copy(executable, asset)
        }
        Files.copy(
            asset,
            latestDirectory.resolve(platform.assetName),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    private companion object {
        const val TEST_RELEASE_VERSION = "1.2.4-test"
    }
}

private infix fun <T> T.shouldBe(expected: T) {
    assertEquals(expected, this)
}

private infix fun <T> Iterable<T>.shouldContain(expected: T) {
    assertTrue(any { it == expected }, "Expected collection to contain <$expected>.")
}

private infix fun String.shouldContain(expected: String) {
    assertTrue(contains(expected), "Expected text to contain <$expected>.")
}

private infix fun String.shouldNotContain(expected: String) {
    assertFalse(contains(expected), "Expected text not to contain <$expected>.")
}
