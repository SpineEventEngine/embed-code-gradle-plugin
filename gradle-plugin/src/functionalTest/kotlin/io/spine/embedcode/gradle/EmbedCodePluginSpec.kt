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
import org.gradle.testkit.runner.BuildResult
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
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
        runner(":checkEmbedding", collectCoverage = false).build()

        val result = runner(":checkEmbedding", collectCoverage = false).build()

        result.output shouldContain "Reusing configuration cache."
        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        result.task(":checkEmbedding")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    @Test
    fun `install the plugin default application release`() {
        val result = runner(":installEmbedCode").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.exists(installedExecutable()) shouldBe true
        result.output shouldContain "Downloading Embed Code $TEST_RELEASE_TAG"
    }

    @Test
    fun `install the Linux ZIP release asset`() {
        val releaseTag = "v1.2.5"
        val asset = releaseDirectory.resolve(
            "download/$releaseTag/embed-code-linux.zip",
        )
        Files.createDirectories(asset.parent)
        ZipOutputStream(Files.newOutputStream(asset)).use { zip ->
            zip.putNextEntry(ZipEntry("bin/embed-code-linux"))
            zip.write("Linux executable".toByteArray())
            zip.closeEntry()
        }
        writeBuildFile(version = releaseTag, sha256 = sha256(asset))
        selectLinuxReleaseAsset()

        val result = runner(":installEmbedCode").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.readString(installedExecutable(releaseTag)) shouldBe "Linux executable"
    }

    @Test
    fun `reject a Linux ZIP release without the expected executable`() {
        val releaseTag = "v1.2.5-missing-executable"
        val asset = releaseDirectory.resolve(
            "download/$releaseTag/embed-code-linux.zip",
        )
        Files.createDirectories(asset.parent)
        ZipOutputStream(Files.newOutputStream(asset)).use { zip ->
            zip.putNextEntry(ZipEntry("README.md"))
            zip.write("No executable in this archive".toByteArray())
            zip.closeEntry()
        }
        writeBuildFile(version = releaseTag, sha256 = sha256(asset))
        selectLinuxReleaseAsset()

        val result = runner(":installEmbedCode").buildAndFail()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.FAILED
        result.output shouldContain "does not contain `embed-code-linux`"
        Files.exists(installedExecutable(releaseTag)) shouldBe false
        val installation = installationDirectory(releaseTag)
        listOf("asset.sha256", "executable.sha256", "source.sha256").forEach { marker ->
            Files.exists(installation.resolve(marker)) shouldBe false
        }
    }

    @Test
    fun `install a bare Linux asset from a release published before ZIP packaging`() {
        val asset = releaseDirectory.resolve(
            "download/$TEST_RELEASE_TAG/embed-code-linux",
        )
        Files.writeString(asset, "Legacy Linux executable")
        writeBuildFile(sha256 = sha256(asset))
        selectLinuxReleaseAsset()

        val result = runner(":installEmbedCode").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.readString(installedExecutable()) shouldBe "Legacy Linux executable"
    }

    @Test
    fun `reuse the verified default executable without another download`() {
        val downloads = AtomicInteger()
        val server = startReleaseServer(downloads)
        try {
            writeBuildFile(downloadBaseUrl = server.releaseBaseUrl)

            runner(":installEmbedCode").build()
            writeBuildFile(
                downloadBaseUrl = server.releaseBaseUrl,
                configureSha256 = false,
            )
            val result = runner(":installEmbedCode").build()

            result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
            downloads.get() shouldBe 1
            result.output shouldContain
                "Reusing previously verified Embed Code $TEST_RELEASE_TAG"
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `ignore an ambient GitHub token unless explicitly configured`() {
        Files.writeString(
            projectDirectory.resolve("build.gradle.kts"),
            """

            tasks.named("installEmbedCode") {
                doFirst {
                    val token = javaClass.getMethod("getGithubToken").invoke(this)
                        as org.gradle.api.provider.Property<*>
                    check(!token.isPresent) {
                        "The plugin must not use GITHUB_TOKEN implicitly."
                    }
                }
            }
            """.trimIndent(),
            StandardOpenOption.APPEND,
        )
        val environment = System.getenv().toMutableMap()
        environment["GITHUB_TOKEN"] = "ambient-token"

        val result = runner(":installEmbedCode")
            .withEnvironment(environment)
            .build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    @Test
    fun `install an overridden application release into a separate cache`() {
        val nextVersion = "1.2.5-test"
        val nextTag = "v$nextVersion"
        createFakeRelease(releaseDirectory, nextVersion, nextTag)
        val downloads = AtomicInteger()
        val server = startReleaseServer(downloads)
        try {
            writeBuildFile(downloadBaseUrl = server.releaseBaseUrl)
            runner(":installEmbedCode").build()

            writeBuildFile(
                version = nextTag,
                downloadBaseUrl = server.releaseBaseUrl,
                sha256 = releaseAssetSha256(tag = nextTag),
            )
            runner(":installEmbedCode").build()

            downloads.get() shouldBe 2
            Files.exists(installedExecutable(TEST_RELEASE_TAG)) shouldBe true
            Files.readString(installedExecutable(nextTag)) shouldContain
                "# release-marker: $nextVersion"
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `reuse the default executable in offline mode`() {
        val server = startReleaseServer()
        writeBuildFile(downloadBaseUrl = server.releaseBaseUrl)
        try {
            runner(":installEmbedCode").build()
        } finally {
            server.stop(0)
        }
        writeBuildFile(
            downloadBaseUrl = server.releaseBaseUrl,
            configureSha256 = false,
        )

        val result = runner(":installEmbedCode", "--offline").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "Reusing locally verified Embed Code executable"
    }

    @Test
    fun `reject a manually installed executable offline without integrity metadata`() {
        writeBuildFile(configureSha256 = false)
        val executable = installedExecutable()
        Files.createDirectories(executable.parent)
        Files.writeString(executable, "user-provided executable")

        val result = runner(":installEmbedCode", "--offline").buildAndFail()

        Files.readString(executable) shouldBe "user-provided executable"
        result.output shouldContain "no locally verified executable is available"
        result.output shouldNotContain "Reusing locally verified Embed Code executable"
    }

    @Test
    fun `report a missing default executable in offline mode`() {
        val result = runner(":installEmbedCode", "--offline").buildAndFail()

        result.output shouldContain "Cannot reuse cached Embed Code asset"
    }

    @Test
    fun `reject a release asset whose SHA-256 digest does not match`() {
        writeBuildFile(sha256 = "0".repeat(64))

        val result = runner(":installEmbedCode").buildAndFail()

        result.output shouldContain "SHA-256 verification failed for Embed Code asset"
    }

    @Test
    fun `restore a modified installed executable from the verified cached asset`() {
        runner(":installEmbedCode").build()
        val executable = installedExecutable()
        val verifiedExecutable = Files.readAllBytes(executable)
        Files.writeString(executable, "modified after verification")

        val result = runner(":installEmbedCode").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.readAllBytes(executable).contentEquals(verifiedExecutable) shouldBe true
        result.output shouldContain "Reusing verified Embed Code"
    }

    @Test
    fun `restore a modified installed executable from the verified cache offline`() {
        runner(":installEmbedCode").build()
        val executable = installedExecutable()
        val verifiedExecutable = Files.readAllBytes(executable)
        Files.writeString(executable, "modified after verification")

        val result = runner(":installEmbedCode", "--offline").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.readAllBytes(executable).contentEquals(verifiedExecutable) shouldBe true
        result.output shouldContain "Reusing verified cached Embed Code executable"
    }

    @Test
    fun `reject a modified installed executable offline without a trusted asset digest`() {
        runner(":installEmbedCode").build()
        val executable = installedExecutable()
        Files.writeString(executable, "modified after verification")
        writeBuildFile(configureSha256 = false)

        val result = runner(":installEmbedCode", "--offline").buildAndFail()

        Files.readString(executable) shouldBe "modified after verification"
        result.output shouldContain "no locally verified executable is available"
        result.output shouldNotContain "Reusing locally verified Embed Code executable"
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `restore execute permission when verified cache metadata is missing`() {
        runner(":installEmbedCode").build()
        val executable = installedExecutable()
        executable.toFile().setExecutable(false, false) shouldBe true
        Files.delete(installationDirectory().resolve("source.sha256"))

        val result = runner(":installEmbedCode").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.isExecutable(executable) shouldBe true
    }

    @Test
    fun `restore missing asset checksum metadata from the verified cached asset`() {
        val downloads = AtomicInteger()
        val server = startReleaseServer(downloads)
        try {
            writeBuildFile(downloadBaseUrl = server.releaseBaseUrl)
            runner(":installEmbedCode").build()
            val assetChecksum = installationDirectory().resolve("asset.sha256")
            Files.delete(assetChecksum)

            val result = runner(":installEmbedCode").build()

            result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
            downloads.get() shouldBe 1
            Files.readString(assetChecksum).trim() shouldBe releaseAssetSha256()
            result.output shouldContain "Reusing verified Embed Code"
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `replace malformed executable checksum metadata from the verified cached asset`() {
        val downloads = AtomicInteger()
        val server = startReleaseServer(downloads)
        try {
            writeBuildFile(downloadBaseUrl = server.releaseBaseUrl)
            runner(":installEmbedCode").build()
            val executableChecksum = installationDirectory().resolve("executable.sha256")
            Files.writeString(executableChecksum, "not-a-sha256")

            val result = runner(":installEmbedCode").build()

            result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
            downloads.get() shouldBe 1
            Files.readString(executableChecksum).trim() shouldBe sha256(installedExecutable())
            result.output shouldContain "Reusing verified Embed Code"
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `remove staged release assets after cache reuse`() {
        runner(":installEmbedCode").build()
        runner(":installEmbedCode").build()
        val taskTemporaryDirectory = projectDirectory.resolve("build/tmp/installEmbedCode")

        val stagedFiles = if (Files.isDirectory(taskTemporaryDirectory)) {
            Files.list(taskTemporaryDirectory).use { paths ->
                paths.map { path -> path.fileName.toString() }
                    .filter { name ->
                        name.startsWith("downloaded-asset-") ||
                            name.startsWith("cached-asset-") ||
                            name.startsWith("prepared-executable-")
                    }
                    .toList()
            }
        } else {
            emptyList()
        }

        stagedFiles shouldBe emptyList()
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `do not follow the former predictable download path`() {
        val outsideFile = projectDirectory.resolve("outside-download")
        Files.writeString(outsideFile, "unchanged")
        val platform = EmbedCodePlatform.detect(
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            TEST_RELEASE_TAG,
        )
        val taskTemporaryDirectory = projectDirectory.resolve("build/tmp/installEmbedCode")
        Files.createDirectories(taskTemporaryDirectory)
        Files.createSymbolicLink(
            taskTemporaryDirectory.resolve(platform.assetName),
            outsideFile,
        )

        val result = runner(":installEmbedCode").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.readString(outsideFile) shouldBe "unchanged"
    }

    @Test
    fun `reject a tampered cached asset offline even when its sidecar is rewritten`() {
        runner(":installEmbedCode").build()
        val installation = installationDirectory()
        val cachedAsset = installation.resolve("release-asset")
        Files.writeString(cachedAsset, "untrusted replacement")
        Files.writeString(installation.resolve("asset.sha256"), sha256(cachedAsset))
        Files.delete(installedExecutable())

        val result = runner(":installEmbedCode", "--offline").buildAndFail()

        result.output shouldContain "does not match `embedCode.sha256`"
    }

    @Test
    fun `redownload a tampered cached asset while online`() {
        val downloads = AtomicInteger()
        val server = startReleaseServer(downloads = downloads)
        try {
            writeBuildFile(downloadBaseUrl = server.releaseBaseUrl)
            runner(":installEmbedCode").build()
            Files.writeString(
                installationDirectory().resolve("release-asset"),
                "untrusted replacement",
            )
            Files.delete(installedExecutable())

            val result = runner(":installEmbedCode").build()

            result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
            downloads.get() shouldBe 2
        } finally {
            server.stop(0)
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `redownload an unreadable cached asset while online`() {
        runner(":installEmbedCode").build()
        Files.delete(installedExecutable())
        Files.setPosixFilePermissions(
            installationDirectory().resolve("release-asset"),
            emptySet(),
        )

        val result = runner(":installEmbedCode").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.isRegularFile(installedExecutable()) shouldBe true
    }

    @Test
    fun `preserve the cause of a verified asset restoration failure`() {
        val checksumFile = installationDirectory().resolve("asset.sha256")
        Files.createDirectories(checksumFile)

        val result = runner(":installEmbedCode").buildAndFail()

        result.output shouldContain
            "Could not restore the verified Embed Code asset from"
        result.output shouldContain "Caused by: java.nio.file."
    }

    @Test
    fun `require a configured digest to restore a missing executable offline`() {
        runner(":installEmbedCode").build()
        Files.delete(installedExecutable())
        writeBuildFile(configureSha256 = false)

        val result = runner(":installEmbedCode", "--offline").buildAndFail()

        result.output shouldContain
            "no locally verified executable is available at"
        result.output shouldContain "Configure `embedCode.sha256`"
    }

    @Test
    fun `restore a missing executable from a verified cached asset offline`() {
        runner(":installEmbedCode").build()
        Files.delete(installedExecutable())

        val result = runner(":installEmbedCode", "--offline").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.exists(installedExecutable()) shouldBe true
        result.output shouldContain "Reusing verified cached Embed Code executable"
    }

    @Test
    fun `accept an explicitly pinned release asset`() {
        writeBuildFile(
            version = TEST_RELEASE_TAG,
            sha256 = releaseAssetSha256(tag = TEST_RELEASE_TAG),
        )

        val result = runner(":installEmbedCode").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `trim an overridden Embed Code release tag`() {
        val overrideTag = "v0.0.0-test"
        createFakeRelease(releaseDirectory, version = "0.0.0-test", tag = overrideTag)
        writeBuildFile(" $overrideTag ")

        val result = runner(":checkEmbedding").build()

        result.task(":checkEmbedding")?.outcome shouldBe TaskOutcome.SUCCESS
        val executableName = EmbedCodePlatform.installedExecutableName(
            System.getProperty("os.name"),
        )
        Files.exists(
            projectDirectory.resolve(
                "build/embed-code/versions/${releaseTagCacheKey(overrideTag)}/$executableName",
            ),
        ) shouldBe true
    }

    @Test
    fun `reject an empty release tag`() {
        writeBuildFile(version = "  ", configureSha256 = false)

        val result = runner(":installEmbedCode").buildAndFail()

        result.output shouldContain "The Embed Code release tag must not be empty."
    }

    @Test
    fun `store exact release tags under portable cache keys`() {
        writeBuildFile(version = TEST_RELEASE_TAG)

        val result = runner(":installEmbedCode").build()
        val cacheKey = releaseTagCacheKey(TEST_RELEASE_TAG)

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        cacheKey.length shouldBe 64
        cacheKey.all { it in '0'..'9' || it in 'a'..'f' } shouldBe true
        Files.exists(installedExecutable(TEST_RELEASE_TAG)) shouldBe true
    }

    @Test
    fun `keep case-distinct release tags in separate cache directories`() {
        val lowerTag = "vcase-test"
        val upperTag = "VCASE-test"
        createFakeRelease(releaseDirectory, version = "lower", tag = lowerTag)
        writeBuildFile(version = lowerTag)
        runner(":installEmbedCode").build()

        createFakeRelease(releaseDirectory, version = "upper", tag = upperTag)
        // Change the file length as well as its case so Gradle cannot reuse a timestamp/size
        // file-system snapshot for the rewritten build script.
        writeBuildFile(version = " $upperTag ")
        runner(":installEmbedCode").build()

        (releaseTagCacheKey(lowerTag) == releaseTagCacheKey(upperTag)) shouldBe false
        Files.readString(installedExecutable(lowerTag)) shouldContain
            "# release-marker: lower"
        Files.readString(installedExecutable(upperTag)) shouldContain
            "# release-marker: upper"
    }

    @Test
    fun `reject the rolling latest version`() {
        writeBuildFile(version = "latest", configureSha256 = false)

        val result = runner(":installEmbedCode").buildAndFail()

        result.output shouldContain "The rolling Embed Code version `latest` is not supported."
    }

    @Test
    fun `reject a release tag containing path traversal segments`() {
        writeBuildFile(
            version = "../../escaped",
            sha256 = "0".repeat(64),
        )

        val result = runner(":installEmbedCode").buildAndFail()

        result.output shouldContain "Embed Code release tag `../../escaped` is invalid."
        Files.exists(projectDirectory.resolve("escaped")) shouldBe false
    }

    @Test
    fun `validate a release tag configured directly on the installation task`() {
        writeBuildFile(version = TEST_RELEASE_TAG)
        Files.writeString(
            projectDirectory.resolve("build.gradle.kts"),
            """

            tasks.named<io.spine.embedcode.gradle.InstallEmbedCodeTask>("installEmbedCode") {
                version.set("../../escaped")
            }
            """.trimIndent(),
            StandardOpenOption.APPEND,
        )

        val result = runner(":installEmbedCode").buildAndFail()

        result.output shouldContain "Embed Code release tag `../../escaped` is invalid."
        Files.exists(projectDirectory.resolve("escaped")) shouldBe false
    }

    @Test
    fun `use a release tag configured directly on the installation task`() {
        Files.writeString(
            projectDirectory.resolve("build.gradle.kts"),
            """

            tasks.named<io.spine.embedcode.gradle.InstallEmbedCodeTask>("installEmbedCode") {
                version.set("$TEST_RELEASE_TAG")
            }
            """.trimIndent(),
            StandardOpenOption.APPEND,
        )

        val result = runner(":installEmbedCode").build()
        val executableName = EmbedCodePlatform.installedExecutableName(
            System.getProperty("os.name"),
        )

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        Files.exists(
            projectDirectory.resolve(
                "build/embed-code/versions/${releaseTagCacheKey(TEST_RELEASE_TAG)}/$executableName",
            ),
        ) shouldBe true
    }

    @Test
    fun `reject an executable path outside the installation directory`() {
        writeBuildFile(version = TEST_RELEASE_TAG)
        Files.writeString(
            projectDirectory.resolve("build.gradle.kts"),
            """

            tasks.named<io.spine.embedcode.gradle.InstallEmbedCodeTask>("installEmbedCode") {
                executableFile.set(layout.projectDirectory.file("escaped/embed-code"))
            }
            """.trimIndent(),
            StandardOpenOption.APPEND,
        )

        val result = runner(":installEmbedCode").buildAndFail()

        result.output shouldContain "must remain inside"
        Files.exists(projectDirectory.resolve("escaped/embed-code")) shouldBe false
    }

    @Test
    fun `reject a checksum path outside the installation directory`() {
        writeBuildFile(version = TEST_RELEASE_TAG)
        Files.writeString(
            projectDirectory.resolve("build.gradle.kts"),
            """

            tasks.named<io.spine.embedcode.gradle.InstallEmbedCodeTask>("installEmbedCode") {
                assetChecksumFile.set(layout.projectDirectory.file("escaped/asset.sha256"))
            }
            """.trimIndent(),
            StandardOpenOption.APPEND,
        )

        val result = runner(":installEmbedCode").buildAndFail()

        result.output shouldContain "must remain inside"
        Files.exists(projectDirectory.resolve("escaped/asset.sha256")) shouldBe false
    }

    @Test
    fun `reject a cached asset path outside the installation directory`() {
        Files.writeString(
            projectDirectory.resolve("build.gradle.kts"),
            """

            tasks.named<io.spine.embedcode.gradle.InstallEmbedCodeTask>("installEmbedCode") {
                cachedAssetFile.set(layout.projectDirectory.file("escaped/release-asset"))
            }
            """.trimIndent(),
            StandardOpenOption.APPEND,
        )

        val result = runner(":installEmbedCode").buildAndFail()

        result.output shouldContain "must remain inside"
        Files.exists(projectDirectory.resolve("escaped/release-asset")) shouldBe false
    }

    @Test
    fun `reject a regular file in the installation path`() {
        val installationRoot = projectDirectory.resolve("build/embed-code")
        val blockingFile = installationRoot.resolve("blocked")
        Files.writeString(
            projectDirectory.resolve("build.gradle.kts"),
            """

            tasks.named<io.spine.embedcode.gradle.InstallEmbedCodeTask>("installEmbedCode") {
                cachedAssetFile.set(
                    layout.buildDirectory.file("embed-code/blocked/release-asset")
                )
                doFirst {
                    val blockingFile = cachedAssetFile.get().asFile.parentFile
                    check(blockingFile.deleteRecursively())
                    blockingFile.writeText("unchanged")
                }
            }
            """.trimIndent(),
            StandardOpenOption.APPEND,
        )

        val result = runner(
            ":installEmbedCode",
            useConfigurationCache = false,
        ).buildAndFail()

        result.output shouldContain "Embed Code installation path component"
        result.output shouldContain "must be a directory."
        Files.readString(blockingFile) shouldBe "unchanged"
        Files.exists(installedExecutable()) shouldBe false
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `reject a symbolic-link installation root`() {
        val outside = projectDirectory.resolve("outside-cache")
        Files.createDirectories(outside)
        val sentinel = outside.resolve("sentinel.txt")
        Files.writeString(sentinel, "unchanged")
        Files.createDirectories(projectDirectory.resolve("build"))
        Files.createSymbolicLink(projectDirectory.resolve("build/embed-code"), outside)

        val result = runner(":installEmbedCode").buildAndFail()

        result.output shouldContain "must be a real directory, not a symbolic link"
        Files.readString(sentinel) shouldBe "unchanged"
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `reject an installation root redirected during download`() {
        val downloadStarted = CountDownLatch(1)
        val continueDownload = CountDownLatch(1)
        val server = startReleaseServer {
            downloadStarted.countDown()
            check(continueDownload.await(30, TimeUnit.SECONDS))
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            writeBuildFile(downloadBaseUrl = server.releaseBaseUrl)
            val build = executor.submit<BuildResult> {
                runner(":installEmbedCode").buildAndFail()
            }
            assertTrue(
                downloadStarted.await(30, TimeUnit.SECONDS),
                "The release download did not start.",
            )
            val installationRoot = projectDirectory.resolve("build/embed-code")
            val originalInstallation = projectDirectory.resolve("original-installation")
            val outside = projectDirectory.resolve("outside-installation")
            Files.createDirectories(outside)
            val sentinel = outside.resolve("sentinel.txt")
            Files.writeString(sentinel, "unchanged")
            Files.move(installationRoot, originalInstallation)
            Files.createSymbolicLink(installationRoot, outside)
            continueDownload.countDown()

            val result = build.get(30, TimeUnit.SECONDS)

            result.output shouldContain
                "must be a real directory, not a symbolic link or redirecting entry"
            Files.readString(sentinel) shouldBe "unchanged"
            Files.exists(outside.resolve("versions")) shouldBe false
        } finally {
            continueDownload.countDown()
            executor.shutdownNow()
            server.stop(0)
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `reject a symbolic-link explicit cache directory`() {
        val outside = projectDirectory.resolve("outside-cache")
        Files.createDirectories(outside)
        val sentinel = outside.resolve("sentinel.txt")
        Files.writeString(sentinel, "unchanged")
        val versions = projectDirectory.resolve("build/embed-code/versions")
        Files.createDirectories(versions)
        Files.createSymbolicLink(versions.resolve(releaseTagCacheKey(TEST_RELEASE_TAG)), outside)
        writeBuildFile(version = TEST_RELEASE_TAG)

        val result = runner(":installEmbedCode").buildAndFail()

        result.output shouldContain "must not be a symbolic link"
        Files.readString(sentinel) shouldBe "unchanged"
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `reject a symbolic-link cached asset`() {
        runner(":installEmbedCode").build()
        Files.delete(installedExecutable())
        val outsideAsset = projectDirectory.resolve("outside-asset")
        Files.writeString(outsideAsset, "unchanged")
        val cachedAsset = installationDirectory().resolve("release-asset")
        Files.delete(cachedAsset)
        Files.createSymbolicLink(cachedAsset, outsideAsset)

        val result = runner(":installEmbedCode", "--offline").buildAndFail()

        result.output shouldContain "must not be a symbolic link"
        Files.readString(outsideAsset) shouldBe "unchanged"
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
    fun `suggest a prefixed tag when a pinned release download fails`() {
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
            writeBuildFile(
                version = TEST_RELEASE_VERSION,
                downloadBaseUrl = baseUrl,
                sha256 = "0".repeat(64),
            )

            val result = runner(":installEmbedCode").buildAndFail()

            result.output shouldContain "HTTP 503"
            result.output shouldContain
                "A release tag `v$TEST_RELEASE_VERSION` may exist; " +
                "previous plugin versions added this prefix automatically."
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `report an HTTP status returned for a release asset`() {
        val downloads = AtomicInteger()
        val server = HttpServer.create(
            InetSocketAddress("127.0.0.1", 0),
            0,
        )
        server.createContext("/releases/download/") { exchange ->
            downloads.incrementAndGet()
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}/releases"
            writeBuildFile(
                version = TEST_RELEASE_TAG,
                downloadBaseUrl = baseUrl,
            )

            val result = runner(":installEmbedCode").buildAndFail()

            val platform = EmbedCodePlatform.detect(
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                TEST_RELEASE_TAG,
            )
            val source = "$baseUrl/download/$TEST_RELEASE_TAG/${platform.assetName}"
            result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.FAILED
            downloads.get() shouldBe 1
            result.output shouldContain
                "Could not download Embed Code: HTTP 503 from $source."
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
        writeBuildFile(TEST_RELEASE_TAG)
        runner(":checkEmbedding").build()
        releaseDirectory.toFile().deleteRecursively()

        val result = runner(":embedCode").build()

        result.task(":installEmbedCode")?.outcome shouldBe TaskOutcome.SUCCESS
        result.output shouldContain "Reusing previously verified Embed Code $TEST_RELEASE_TAG"
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
        collectCoverage: Boolean = testKitCoverageJvmArgument != null,
        useConfigurationCache: Boolean = !collectCoverage,
    ): GradleRunner {
        val gradleArguments = arguments.toMutableList()
        if (collectCoverage) {
            gradleArguments.add(
                "-Dorg.gradle.jvmargs=${requireNotNull(testKitCoverageJvmArgument)}",
            )
        } else if (useConfigurationCache) {
            gradleArguments.add("--configuration-cache")
        }
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
        val result = runner(":checkEmbedding", collectCoverage = false)
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
        sha256: String? = null,
        configureSha256: Boolean = true,
    ) {
        val versionConfiguration = version?.let { "version.set(\"$it\")" }.orEmpty()
        val checksumConfiguration = if (configureSha256) {
            val selectedTag = version?.trim() ?: TEST_RELEASE_TAG
            val configuredSha256 = sha256 ?: releaseAssetSha256(tag = selectedTag)
            "sha256.set(\"$configuredSha256\")"
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
                $versionConfiguration
                $checksumConfiguration
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
     * Overrides the installation task to select the Linux AMD64 release asset.
     */
    private fun selectLinuxReleaseAsset() {
        Files.writeString(
            projectDirectory.resolve("build.gradle.kts"),
            """

            tasks.named<io.spine.embedcode.gradle.InstallEmbedCodeTask>("installEmbedCode") {
                operatingSystem.set("Linux")
                architecture.set("amd64")
            }
            """.trimIndent(),
            StandardOpenOption.APPEND,
        )
    }

    /**
     * Returns the cache directory for [releaseTag].
     */
    private fun installationDirectory(releaseTag: String = TEST_RELEASE_TAG): Path =
        projectDirectory.resolve(
            "build/embed-code/versions/${releaseTagCacheKey(releaseTag)}",
        )

    /**
     * Returns the installed executable for [releaseTag].
     */
    private fun installedExecutable(releaseTag: String = TEST_RELEASE_TAG): Path {
        val executableName = EmbedCodePlatform.installedExecutableName(
            System.getProperty("os.name"),
        )
        return installationDirectory(releaseTag).resolve(executableName)
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
            "namedSource(" +
                "\"$secondSourceName\", " +
                "providers.provider { layout.projectDirectory.dir(\"browser\") }" +
                ")"
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
                sha256.set("${releaseAssetSha256()}")
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
            tag,
        )
        val versionDirectory = root.resolve("download/$tag")
        Files.createDirectories(versionDirectory)
        val executable = projectDirectory.resolve(platform.executableName)
        Files.writeString(
            executable,
            """
            #!/bin/sh
            # release-marker: $version
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
            Files.copy(
                executable,
                asset,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    /**
     * Returns the digest of a fake release asset.
     */
    private fun releaseAssetSha256(
        root: Path = releaseDirectory,
        tag: String = TEST_RELEASE_TAG,
    ): String {
        val platform = EmbedCodePlatform.detect(
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            tag.trim(),
        )
        val asset = root.resolve("download/${tag.trim()}/${platform.assetName}")
        return sha256(asset)
    }

    /**
     * Starts a release server that records exact-tag asset downloads.
     */
    private fun startReleaseServer(
        downloads: AtomicInteger = AtomicInteger(),
        beforeDownload: () -> Unit = {},
    ): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/releases/download/") { exchange ->
            val relativePath = exchange.requestURI.path.removePrefix("/releases/download/")
            downloads.incrementAndGet()
            beforeDownload()
            val downloadDirectory = releaseDirectory.resolve("download")
            val asset = downloadDirectory.resolve(relativePath).normalize()
            if (!asset.startsWith(downloadDirectory) || !Files.isRegularFile(asset)) {
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

    private val testKitCoverageJvmArgument: String?
        get() = System.getProperty(TEST_KIT_COVERAGE_JVM_ARGUMENT_PROPERTY)

    private companion object {
        const val TEST_KIT_COVERAGE_JVM_ARGUMENT_PROPERTY =
            "io.spine.embedcode.gradle.testkit.coverage.jvm-argument"
        val TEST_RELEASE_TAG = DEFAULT_EMBED_CODE_VERSION
        val TEST_RELEASE_VERSION = TEST_RELEASE_TAG.removePrefix("v")
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
