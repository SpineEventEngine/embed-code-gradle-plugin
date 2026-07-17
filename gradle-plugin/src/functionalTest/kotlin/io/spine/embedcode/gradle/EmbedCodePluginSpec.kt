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

import com.sun.net.httpserver.HttpServer
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@DisplayName("`EmbedCodePlugin` should")
internal class EmbedCodePluginSpec {

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
    fun `log main execution points at info level`() {
        val result = runner(":checkEmbedding", "--info").build()

        result.output shouldContain "Applying the Embed Code plugin to project `:`."
        result.output shouldContain
            "Registered Embed Code tasks `checkEmbedding` and `embedCode` in project `:`."
        result.output shouldContain "Preparing the Embed Code executable for operating system"
        result.output shouldContain "Preparing Embed Code `check` mode"
        result.output shouldContain "Using source root"
        result.output shouldContain "Starting Embed Code `check` mode with executable"
        result.output shouldContain "Embed Code `check` mode completed successfully."
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
        val executableName = EmbedCodePlatform.installedExecutableName(
            System.getProperty("os.name"),
        )
        val installedExecutable = projectDirectory.resolve(
            "build/embed-code/latest/$executableName",
        )

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.exists(installedExecutable) shouldBe true
    }

    @Test
    fun `reuse latest executable when the release version is unchanged`() {
        val latestTag = AtomicReference(TEST_RELEASE_TAG)
        val versionChecks = AtomicInteger()
        val downloads = AtomicInteger()
        val server = startReleaseServer(latestTag, versionChecks, downloads)
        try {
            writeBuildFile(downloadBaseUrl = server.releaseBaseUrl)

            runner(":installEmbedCode").build()
            val result = runner(":installEmbedCode").build()

            result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
            versionChecks.get() shouldBe 2
            downloads.get() shouldBe 1
            result.output shouldContain "Reusing Embed Code v$TEST_RELEASE_VERSION"
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `use a resolved latest release tag without modification`() {
        val releaseTag = "release-$TEST_RELEASE_VERSION"
        createFakeRelease(releaseDirectory, tag = releaseTag)
        val downloads = AtomicInteger()
        val server = startReleaseServer(
            AtomicReference(releaseTag),
            AtomicInteger(),
            downloads,
        )
        try {
            writeBuildFile(downloadBaseUrl = server.releaseBaseUrl)

            runner(":installEmbedCode").build()

            downloads.get() shouldBe 1
            Files.readString(
                projectDirectory.resolve("build/embed-code/latest/version.txt"),
            ).trim() shouldBe releaseTag
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `download latest executable when the release version changes`() {
        val nextVersion = "1.2.5-test"
        createFakeRelease(releaseDirectory, nextVersion)
        val latestTag = AtomicReference(TEST_RELEASE_TAG)
        val versionChecks = AtomicInteger()
        val downloads = AtomicInteger()
        val server = startReleaseServer(latestTag, versionChecks, downloads)
        try {
            writeBuildFile(downloadBaseUrl = server.releaseBaseUrl)
            runner(":installEmbedCode").build()

            latestTag.set("v$nextVersion")
            runner(":installEmbedCode").build()

            versionChecks.get() shouldBe 2
            downloads.get() shouldBe 2
            Files.readString(
                projectDirectory.resolve("build/embed-code/latest/version.txt"),
            ).trim() shouldBe "v$nextVersion"
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `reuse latest executable in offline mode`() {
        val latestTag = AtomicReference(TEST_RELEASE_TAG)
        val server = startReleaseServer(
            latestTag,
            AtomicInteger(),
            AtomicInteger(),
        )
        writeBuildFile(downloadBaseUrl = server.releaseBaseUrl)
        try {
            runner(":installEmbedCode").build()
        } finally {
            server.stop(0)
        }

        val result = runner(":installEmbedCode", "--offline").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "Reusing cached Embed Code executable"
    }

    @Test
    fun `reuse cached executable when the latest release check fails`() {
        val latestTag = AtomicReference(TEST_RELEASE_TAG)
        val server = startReleaseServer(
            latestTag,
            AtomicInteger(),
            AtomicInteger(),
        )
        writeBuildFile(downloadBaseUrl = server.releaseBaseUrl)
        try {
            runner(":installEmbedCode").build()
        } finally {
            server.stop(0)
        }

        val result = runner(":installEmbedCode").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "Could not check the latest Embed Code release"
        result.output shouldContain "Reusing the cached executable"
    }

    @Test
    fun `report a failed latest release check without a cached executable`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val baseUrl = "http://127.0.0.1:${server.address.port}/releases"
        server.stop(0)
        writeBuildFile(downloadBaseUrl = baseUrl)

        val result = runner(":installEmbedCode").buildAndFail()

        result.output shouldContain "Could not resolve the latest Embed Code release"
    }

    @Test
    fun `report a missing latest executable in offline mode`() {
        val result = runner(":installEmbedCode", "--offline").buildAndFail()

        result.output shouldContain
            "Cannot install the latest Embed Code release in offline mode because " +
            "no cached executable exists"
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `trim an overridden Embed Code version`() {
        val overrideVersion = "0.0.0-test"
        createFakeRelease(releaseDirectory, overrideVersion)
        writeBuildFile(" $overrideVersion ")

        val result = runner(":checkEmbedding").build()

        result.task(":checkEmbedding")?.outcome shouldBe TaskOutcome.SUCCESS
        val executableName = EmbedCodePlatform.installedExecutableName(
            System.getProperty("os.name"),
        )
        Files.exists(
            projectDirectory.resolve("build/embed-code/$overrideVersion/$executableName"),
        ) shouldBe true
    }

    @Test
    fun `defer unsupported platform failure until installation`() {
        Files.writeString(
            projectDirectory.resolve("build.gradle.kts"),
            """
            plugins {
                id("io.spine.embed-code")
            }

            tasks.named<io.spine.embedcode.gradle.InstallEmbedCodeTask>("installEmbedCode") {
                operatingSystem.set("Linux")
                architecture.set("aarch64")
            }
            """.trimIndent(),
        )

        runner("tasks").build()
        val result = runner(":installEmbedCode").buildAndFail()

        result.output shouldContain
            "Embed Code does not publish a binary for operating system `Linux`" +
            " and architecture `aarch64`."
    }

    @Test
    fun `accept trailing slashes in the release base URL`() {
        val baseUrl = releaseDirectory.toUri().toString().trimEnd('/') + "///"
        writeBuildFile(downloadBaseUrl = baseUrl)

        val result = runner(":installEmbedCode").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    @Test
    fun `report an HTTP status returned for a release asset`() {
        val server = HttpServer.create(
            InetSocketAddress("127.0.0.1", 0),
            0,
        )
        server.createContext("/") { exchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}/releases"
            writeBuildFile(downloadBaseUrl = baseUrl)

            val result = runner(":installEmbedCode").buildAndFail()

            result.output shouldContain "HTTP 503"
        } finally {
            server.stop(0)
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `run check mode with Gradle 8_14_4`() {
        runCheckModeWithGradle("8.14.4")
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `run check mode with Gradle 9_0_0`() {
        runCheckModeWithGradle("9.0.0")
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
    fun `reject an empty named source`() {
        writeNamedSourcesBuildFile(firstSourceName = " ", includeSecondSource = false)

        val result = runner("tasks").buildAndFail()

        result.output shouldContain "An Embed Code source name must not be empty."
    }

    @Test
    fun `reject a duplicate named source`() {
        writeNamedSourcesBuildFile(secondSourceName = "company-site")

        val result = runner("tasks").buildAndFail()

        result.output shouldContain "Embed Code source `company-site` is already configured."
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

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `prepend underscores to an occupied installEmbedCode task name`() {
        Files.writeString(
            projectDirectory.resolve("settings.gradle.kts"),
            """
            rootProject.name = "test-project"

            gradle.beforeProject {
                tasks.register("installEmbedCode")
                tasks.register("_installEmbedCode")
            }
            """.trimIndent(),
        )

        val result = runner(":checkEmbedding").build()

        result.task(":__installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":checkEmbedding")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    /**
     * Creates a runner using the plugin-under-test classpath.
     */
    private fun runner(
        vararg arguments: String,
    ): GradleRunner {
        val gradleArguments = arguments.toMutableList()
        gradleArguments.add("--configuration-cache")
        gradleArguments.add("--stacktrace")
        return GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withArguments(gradleArguments)
            .withPluginClasspath()
    }

    /**
     * Runs check mode with [gradleVersion].
     */
    private fun runCheckModeWithGradle(gradleVersion: String) {
        val result = runner(":checkEmbedding")
            .withGradleVersion(gradleVersion)
            .build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":checkEmbedding")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.readString(projectDirectory.resolve("mode.txt")).trim() shouldBe "check"
    }

    /**
     * Writes a consuming build configured entirely through the plugin extension.
     */
    private fun writeBuildFile(
        version: String? = null,
        downloadBaseUrl: String = releaseDirectory.toUri().toString().trimEnd('/'),
    ) {
        val versionConfiguration = version?.let { "version.set(\"$it\")" }.orEmpty()
        Files.writeString(
            projectDirectory.resolve("build.gradle.kts"),
            """
            plugins {
                id("io.spine.embed-code")
            }

            embedCode {
                $versionConfiguration
                downloadBaseUrl.set("$downloadBaseUrl")
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

    /**
     * Writes a consuming build with two named source roots and no YAML file.
     */
    private fun writeNamedSourcesBuildFile(
        includeDirectSource: Boolean = false,
        firstSourceName: String = "company-site",
        secondSourceName: String = "jxbrowser",
        includeSecondSource: Boolean = true,
    ) {
        val baseUrl = releaseDirectory.toUri().toString().trimEnd('/')
        val directSource = if (includeDirectSource) {
            "codePath.set(layout.projectDirectory.dir(\"code\"))"
        } else {
            ""
        }
        val secondSource = if (includeSecondSource) {
            "namedSource(\"$secondSourceName\", layout.projectDirectory.dir(\"browser\"))"
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
                namedSource("$firstSourceName", layout.projectDirectory.dir("company-site"))
                $secondSource
                docsPath.set(layout.projectDirectory)
            }
            """.trimIndent(),
        )
    }

    /**
     * Creates a host-specific fake release asset that records received arguments.
     */
    private fun createFakeRelease(
        root: Path,
        version: String = TEST_RELEASE_VERSION,
        tag: String = "v$version",
    ) {
        val platform = EmbedCodePlatform.detect(
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
        )
        val versionDirectory = root.resolve("download/$tag")
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

    /**
     * Starts a release server whose latest endpoint redirects to a mutable tag.
     */
    private fun startReleaseServer(
        latestTag: AtomicReference<String>,
        versionChecks: AtomicInteger,
        downloads: AtomicInteger,
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/releases/latest") { exchange ->
            versionChecks.incrementAndGet()
            if (exchange.requestMethod != "HEAD") {
                exchange.sendResponseHeaders(405, -1)
            } else {
                exchange.responseHeaders.add(
                    "Location",
                    "/releases/tag/${latestTag.get()}",
                )
                exchange.sendResponseHeaders(302, -1)
            }
            exchange.close()
        }
        server.createContext("/releases/download/") { exchange ->
            downloads.incrementAndGet()
            val relativePath = exchange.requestURI.path.removePrefix("/releases/download/")
            val asset = releaseDirectory.resolve("download").resolve(relativePath).normalize()
            if (!asset.startsWith(releaseDirectory.resolve("download")) || !Files.isRegularFile(asset)) {
                exchange.sendResponseHeaders(404, -1)
            } else {
                val content = Files.readAllBytes(asset)
                exchange.sendResponseHeaders(200, content.size.toLong())
                exchange.responseBody.use { output -> output.write(content) }
            }
            exchange.close()
        }
        server.start()
        return server
    }

    private val HttpServer.releaseBaseUrl: String
        get() = "http://127.0.0.1:${address.port}/releases"

    private companion object {
        const val TEST_RELEASE_VERSION = "1.2.4-test"
        const val TEST_RELEASE_TAG = "v1.2.4-test"
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
