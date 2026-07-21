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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLConnection
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

/**
 * Downloads and prepares the Embed Code executable selected for the host.
 *
 * Cached executables are reused only after their SHA-256 integrity metadata is
 * checked. For the latest release, the remote version is checked before an
 * existing executable is reused. When that check fails, a previously verified
 * executable remains available.
 */
@DisableCachingByDefault(because = "Release assets come from external URLs that may change")
public abstract class InstallEmbedCodeTask : DefaultTask() {

    /** An optional Embed Code release version. */
    @get:Input
    @get:Optional
    public abstract val version: Property<String>

    /** An optional user-provided SHA-256 digest of the release asset. */
    @get:Input
    @get:Optional
    public abstract val sha256: Property<String>

    /** An optional token used for GitHub release metadata requests. */
    @get:Internal
    public abstract val githubToken: Property<String>

    /** The base URL of the Embed Code releases. */
    @get:Input
    public abstract val downloadBaseUrl: Property<String>

    /** The operating system used to select a release asset. */
    @get:Input
    public abstract val operatingSystem: Property<String>

    /** The architecture used to select a release asset. */
    @get:Input
    public abstract val architecture: Property<String>

    /** Whether Gradle is running without network access. */
    @get:Input
    public abstract val offline: Property<Boolean>

    /** The installed executable used by Embed Code execution tasks. */
    @get:OutputFile
    public abstract val executableFile: RegularFileProperty

    /** Stores the release version represented by the latest executable. */
    @get:LocalState
    public abstract val resolvedVersionFile: RegularFileProperty

    /** Stores the verified digest of the downloaded release asset. */
    @get:LocalState
    public abstract val assetChecksumFile: RegularFileProperty

    /** Stores the digest of the prepared executable used from the local cache. */
    @get:LocalState
    public abstract val executableChecksumFile: RegularFileProperty

    /** Stores the selected release source and asset identity. */
    @get:LocalState
    public abstract val sourceIdentityFile: RegularFileProperty

    /**
     * Downloads, extracts when necessary, and marks the executable runnable.
     */
    @TaskAction
    public fun install() {
        val requestedVersion = version.orNull
        if (requestedVersion != null && requestedVersion.isEmpty()) {
            throw GradleException("Embed Code version must not be empty.")
        }
        val configuredSha256 = sha256.orNull?.let(::normalizeSha256)
        val hostOperatingSystem = operatingSystem.get()
        val hostArchitecture = architecture.get()
        logger.info(
            "Preparing the Embed Code executable for operating system `{}` and architecture `{}`.",
            hostOperatingSystem,
            hostArchitecture,
        )
        val platform = EmbedCodePlatform.detect(hostOperatingSystem, hostArchitecture)
        val asset = platform.assetName
        val baseUrl = trimTrailingSlashes(downloadBaseUrl.get())
        val destination = executableFile.get().asFile.toPath()
        val versionFile = resolvedVersionFile.get().asFile.toPath()
        val assetChecksum = assetChecksumFile.get().asFile.toPath()
        val executableChecksum = executableChecksumFile.get().asFile.toPath()
        val sourceIdentity = sourceIdentityFile.get().asFile.toPath()
        val expectedSourceIdentity = releaseAssetIdentity(baseUrl, asset)
        if (offline.get()) {
            reuseOfflineInstallation(
                destination,
                assetChecksum,
                executableChecksum,
                sourceIdentity,
                expectedSourceIdentity,
                configuredSha256,
            )
            return
        }
        val resolvedVersion = if (requestedVersion == null) {
            logger.info("Resolving the latest Embed Code release from {}.", baseUrl)
            try {
                resolveLatestVersion(baseUrl)
            } catch (exception: GradleException) {
                if (
                    !isTrustedCachedInstallation(
                        destination,
                        assetChecksum,
                        executableChecksum,
                        sourceIdentity,
                        expectedSourceIdentity,
                        configuredSha256,
                    )
                ) {
                    throw exception
                }
                logger.warn(
                    "Could not check the latest Embed Code release ({}). " +
                        "Reusing the cached executable from `{}`.",
                    exception.message,
                    destination,
                )
                return
            }
        } else {
            null
        }
        if (resolvedVersion != null) {
            logger.info("Resolved the latest Embed Code release as {}.", resolvedVersion)
        }
        if (
            (
                requestedVersion != null ||
                    resolvedVersion != null &&
                    readResolvedVersion(versionFile) == resolvedVersion
            ) &&
            isTrustedCachedInstallation(
                destination,
                assetChecksum,
                executableChecksum,
                sourceIdentity,
                expectedSourceIdentity,
                configuredSha256,
            )
        ) {
            logger.lifecycle(
                "Reusing verified Embed Code {} from {}",
                selectedVersionName(requestedVersion, resolvedVersion),
                destination,
            )
            return
        }
        val selectedReleaseTag = if (requestedVersion != null) {
            releaseTagForVersion(requestedVersion)
        } else {
            resolvedVersion
        }
        val source = releaseAsset(baseUrl, selectedReleaseTag, asset)
        val download = temporaryDir.toPath().resolve(asset)
        val preparedExecutable = temporaryDir.toPath().resolve(platform.executableName)

        try {
            Files.createDirectories(destination.parent)
            val release = requestedVersion ?: "latest release"
            logger.lifecycle("Downloading Embed Code {} from {}", release, source)
            download(source, download)
            val expectedAssetSha256 = resolveExpectedAssetSha256(
                configuredSha256,
                baseUrl,
                selectedReleaseTag,
                asset,
            ) { metadataSource ->
                val token = if (
                    metadataSource.host.equals("api.github.com", ignoreCase = true)
                ) {
                    githubToken.orNull?.trim()?.ifEmpty { null }
                } else {
                    null
                }
                readText(metadataSource, token)
            }
            val downloadedSha256 = sha256(download)
            if (downloadedSha256 != expectedAssetSha256) {
                throw GradleException(
                    "SHA-256 verification failed for Embed Code asset `$asset`: " +
                        "expected `$expectedAssetSha256`, but downloaded `$downloadedSha256`.",
                )
            }
            logger.info("Verified SHA-256 digest `{}` for {}.", downloadedSha256, asset)

            if (asset.endsWith(".zip")) {
                extractExecutable(download, platform.executableName, preparedExecutable)
            } else {
                Files.move(download, preparedExecutable, StandardCopyOption.REPLACE_EXISTING)
            }

            if (!preparedExecutable.toFile().setExecutable(true, false)) {
                throw GradleException("Could not make `$preparedExecutable` executable.")
            }
            val preparedExecutableSha256 = sha256(preparedExecutable)
            moveAtomically(preparedExecutable, destination)
            writeResolvedVersion(assetChecksum, expectedAssetSha256)
            writeResolvedVersion(executableChecksum, preparedExecutableSha256)
            writeResolvedVersion(sourceIdentity, expectedSourceIdentity)
            if (resolvedVersion != null) {
                writeResolvedVersion(versionFile, resolvedVersion)
            }
            logger.info(
                "Installed Embed Code {} at {}.",
                selectedReleaseTag ?: "latest release",
                destination,
            )
        } catch (exception: IOException) {
            throw GradleException("Could not install Embed Code from $source.", exception)
        }
    }

    /**
     * Reuses an installed executable while Gradle is offline.
     */
    private fun reuseOfflineInstallation(
        destination: Path,
        assetChecksum: Path,
        executableChecksum: Path,
        sourceIdentity: Path,
        expectedSourceIdentity: String,
        configuredSha256: String?,
    ) {
        if (!Files.isRegularFile(destination)) {
            throw GradleException(
                "Cannot install Embed Code in offline mode because " +
                    "no cached executable exists at `$destination`.",
            )
        }
        if (
            !isTrustedCachedInstallation(
                destination,
                assetChecksum,
                executableChecksum,
                sourceIdentity,
                expectedSourceIdentity,
                configuredSha256,
            )
        ) {
            throw GradleException(
                "Cannot reuse cached Embed Code executable `$destination` in offline mode " +
                    "because its SHA-256 integrity metadata is missing or does not match.",
            )
        }
        logger.lifecycle(
            "Reusing verified cached Embed Code executable from {} in offline mode",
            destination,
        )
    }

    private companion object {

        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val READ_TIMEOUT_MILLIS = 120_000
        const val BUFFER_SIZE = 8_192

        fun selectedVersionName(requestedVersion: String?, resolvedVersion: String?): String =
            requestedVersion ?: resolvedVersion ?: "latest release"

        /**
         * Checks both the trusted release-asset digest and cached executable contents.
         */
        fun isTrustedCachedInstallation(
            destination: Path,
            assetChecksumFile: Path,
            executableChecksumFile: Path,
            sourceIdentityFile: Path,
            expectedSourceIdentity: String,
            configuredSha256: String?,
        ): Boolean {
            if (!Files.isRegularFile(destination)) {
                return false
            }
            val storedAssetSha256 = readStoredSha256(assetChecksumFile) ?: return false
            val storedExecutableSha256 = readStoredSha256(executableChecksumFile) ?: return false
            val storedSourceIdentity = readStoredSha256(sourceIdentityFile) ?: return false
            if (storedSourceIdentity != expectedSourceIdentity) {
                return false
            }
            if (configuredSha256 != null && configuredSha256 != storedAssetSha256) {
                return false
            }
            return try {
                sha256(destination) == storedExecutableSha256
            } catch (_: IOException) {
                false
            }
        }

        /**
         * Reads a locally stored SHA-256 digest, ignoring invalid state.
         */
        fun readStoredSha256(file: Path): String? {
            val value = readResolvedVersion(file) ?: return null
            return try {
                normalizeSha256(value)
            } catch (_: GradleException) {
                null
            }
        }

        /**
         * Returns the tag of the release targeted by the latest-release redirect.
         *
         * Non-HTTP sources have no redirect response, so their tag is unknown.
         */
        fun resolveLatestVersion(baseUrl: String): String? {
            val source = URI.create("$baseUrl/latest")
            val connection = source.toURL().openConnection()
            if (connection !is HttpURLConnection) {
                return null
            }
            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.setRequestProperty("User-Agent", "embed-code-gradle-plugin")
                connection.instanceFollowRedirects = false
                connection.requestMethod = "HEAD"
                val status = connection.responseCode
                if (status < 300 || status > 399) {
                    throw GradleException(
                        "Could not resolve the latest Embed Code release: " +
                            "HTTP $status from $source.",
                    )
                }
                val location = connection.getHeaderField("Location")
                    ?: throw GradleException(
                        "Could not resolve the latest Embed Code release: " +
                            "the redirect from $source has no Location header.",
                    )
                val releaseUri = source.resolve(location)
                val tag = releaseUri.path.substringAfterLast('/')
                if (tag.isEmpty()) {
                    throw GradleException(
                        "Could not resolve the latest Embed Code release from `$releaseUri`.",
                    )
                }
                return tag
            } catch (exception: IOException) {
                throw GradleException(
                    "Could not resolve the latest Embed Code release from $source.",
                    exception,
                )
            } finally {
                connection.disconnect()
            }
        }

        /**
         * Returns the release asset URI for the latest release or [releaseTag].
         */
        fun releaseAsset(baseUrl: String, releaseTag: String?, asset: String): URI {
            if (releaseTag == null) {
                return URI.create("$baseUrl/latest/download/$asset")
            }
            return URI.create("$baseUrl/download/$releaseTag/$asset")
        }

        /**
         * Returns the release tag corresponding to a user-configured [version].
         */
        fun releaseTagForVersion(version: String): String {
            return if (version.startsWith("v")) version else "v$version"
        }

        /**
         * Downloads [source] into [destination], reporting HTTP failures clearly.
         */
        fun download(source: URI, destination: Path) {
            var connection: URLConnection? = null
            try {
                connection = source.toURL().openConnection()
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.setRequestProperty("User-Agent", "embed-code-gradle-plugin")

                if (connection is HttpURLConnection) {
                    connection.instanceFollowRedirects = true
                    val status = connection.responseCode
                    if (status < 200 || status > 299) {
                        throw GradleException(
                            "Could not download Embed Code: HTTP $status from $source.",
                        )
                    }
                }

                connection.getInputStream().use { input ->
                    Files.newOutputStream(destination).use { output ->
                        copy(input, output)
                    }
                }
            } catch (exception: IOException) {
                throw GradleException("Could not download Embed Code from $source.", exception)
            } finally {
                if (connection is HttpURLConnection) {
                    connection.disconnect()
                }
            }
        }

        /**
         * Reads UTF-8 text from [source], reporting HTTP failures clearly.
         */
        fun readText(source: URI, githubToken: String? = null): String {
            var connection: URLConnection? = null
            try {
                connection = source.toURL().openConnection()
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.setRequestProperty("User-Agent", "embed-code-gradle-plugin")
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                if (githubToken != null) {
                    connection.setRequestProperty("Authorization", "Bearer $githubToken")
                }
                if (connection is HttpURLConnection) {
                    connection.instanceFollowRedirects = true
                    val status = connection.responseCode
                    if (status < 200 || status > 299) {
                        throw GradleException(
                            "Could not read checksum metadata: HTTP $status from $source.",
                        )
                    }
                }
                return connection.getInputStream()
                    .bufferedReader(StandardCharsets.UTF_8)
                    .use { reader -> reader.readText() }
            } catch (exception: IOException) {
                throw GradleException("Could not read checksum metadata from $source.", exception)
            } finally {
                if (connection is HttpURLConnection) {
                    connection.disconnect()
                }
            }
        }

        /**
         * Returns the recorded latest release version, if available.
         */
        fun readResolvedVersion(versionFile: Path): String? {
            return try {
                Files.readString(versionFile).trim().ifEmpty { null }
            } catch (_: IOException) {
                null
            }
        }

        /**
         * Records [version] after its executable has been installed.
         */
        @Throws(IOException::class)
        fun writeResolvedVersion(versionFile: Path, version: String) {
            Files.createDirectories(versionFile.parent)
            val temporaryFile = versionFile.resolveSibling("${versionFile.fileName}.tmp")
            Files.writeString(temporaryFile, "$version\n")
            moveAtomically(temporaryFile, versionFile)
        }

        /**
         * Extracts [entryName] from [archive] into [destination].
         */
        @Throws(IOException::class)
        fun extractExecutable(archive: Path, entryName: String, destination: Path) {
            ZipInputStream(Files.newInputStream(archive)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val entryPath = entry.name
                    val slash = entryPath.lastIndexOf('/')
                    val fileName = if (slash >= 0) {
                        entryPath.substring(slash + 1)
                    } else {
                        entryPath
                    }
                    if (!entry.isDirectory && fileName == entryName) {
                        Files.newOutputStream(destination).use { output ->
                            copy(zip, output)
                        }
                        return
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            throw GradleException("Archive `$archive` does not contain `$entryName`.")
        }

        /**
         * Copies all bytes from [input] into [output].
         */
        @Throws(IOException::class)
        fun copy(input: InputStream, output: OutputStream) {
            val buffer = ByteArray(BUFFER_SIZE)
            var count = input.read(buffer)
            while (count >= 0) {
                output.write(buffer, 0, count)
                count = input.read(buffer)
            }
        }

        /**
         * Moves [source] to [destination], atomically when supported.
         */
        @Throws(IOException::class)
        fun moveAtomically(source: Path, destination: Path) {
            try {
                Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        /**
         * Removes trailing slashes without changing a URL scheme.
         */
        fun trimTrailingSlashes(value: String): String {
            var end = value.length
            while (end > 0 && value[end - 1] == '/') {
                end--
            }
            return value.substring(0, end)
        }
    }
}
