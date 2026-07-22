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
import org.gradle.api.file.DirectoryProperty
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
import java.nio.channels.Channels
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.ZipInputStream

/**
 * Downloads and prepares the Embed Code executable selected for the host.
 *
 * Release assets are authenticated before their first installation. A previously
 * verified local installation is reused without another network request or digest
 * calculation. If that installation is missing or its metadata no longer matches
 * the configured release source, the retained asset is authenticated again before use.
 */
@DisableCachingByDefault(because = "Release assets come from external URLs that may change")
public abstract class InstallEmbedCodeTask : DefaultTask() {

    /** An optional Embed Code release tag used verbatim; absent or empty selects latest. */
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

    /** The directory containing executables and their integrity metadata. */
    @get:Internal
    public abstract val installationDirectory: DirectoryProperty

    /** The installed executable used by Embed Code execution tasks. */
    @get:OutputFile
    public abstract val executableFile: RegularFileProperty

    /** Stores the exact release tag represented by this installation. */
    @get:LocalState
    public abstract val resolvedVersionFile: RegularFileProperty

    /** Stores the downloaded release asset used to recreate the executable. */
    @get:LocalState
    public abstract val cachedAssetFile: RegularFileProperty

    /** Records the verified digest of the downloaded release asset. */
    @get:LocalState
    public abstract val assetChecksumFile: RegularFileProperty

    /** Records that the prepared executable came from a verified release asset. */
    @get:LocalState
    public abstract val executableChecksumFile: RegularFileProperty

    /** Records the selected release source and asset identity. */
    @get:LocalState
    public abstract val sourceIdentityFile: RegularFileProperty

    /**
     * Downloads, extracts when necessary, and marks the executable runnable.
     */
    @TaskAction
    public fun install() {
        // Validate again because consumers can configure this public task input directly.
        val requestedTag = version.orNull?.let(::validateVersion)?.ifEmpty { null }
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
        val installationRoot = installationDirectory.get().asFile.toPath()
            .toAbsolutePath()
            .normalize()
        val destination = requireInsideInstallationDirectory(
            executableFile.get().asFile.toPath(),
            installationRoot,
        )
        val cachedAsset = requireInsideInstallationDirectory(
            cachedAssetFile.get().asFile.toPath(),
            installationRoot,
        )
        val versionFile = requireInsideInstallationDirectory(
            resolvedVersionFile.get().asFile.toPath(),
            installationRoot,
        )
        val assetChecksum = requireInsideInstallationDirectory(
            assetChecksumFile.get().asFile.toPath(),
            installationRoot,
        )
        val executableChecksum = requireInsideInstallationDirectory(
            executableChecksumFile.get().asFile.toPath(),
            installationRoot,
        )
        val sourceIdentity = requireInsideInstallationDirectory(
            sourceIdentityFile.get().asFile.toPath(),
            installationRoot,
        )
        prepareInstallationDirectory(installationRoot)
        requireNoSymbolicLinks(destination, installationRoot)

        if (offline.get()) {
            reuseOfflineInstallation(
                platform,
                destination,
                cachedAsset,
                versionFile,
                assetChecksum,
                executableChecksum,
                sourceIdentity,
                requestedTag,
                baseUrl,
                asset,
                configuredSha256,
                installationRoot,
            )
            return
        }
        val cachedTag = readStoredValue(versionFile, installationRoot)
        if (isPreviouslyVerifiedInstallation(
                destination,
                versionFile,
                assetChecksum,
                executableChecksum,
                sourceIdentity,
                requestedTag,
                baseUrl,
                asset,
                configuredSha256,
                installationRoot,
            )
        ) {
            logger.lifecycle(
                "Reusing previously verified Embed Code {} from {}",
                selectedVersionName(requestedTag, cachedTag),
                destination,
            )
            return
        }
        val resolvedTag = if (requestedTag == null) {
            logger.info("Resolving the latest Embed Code release from {}.", baseUrl)
            try {
                resolveLatestVersion(baseUrl)
            } catch (exception: GradleException) {
                if (isReusableInstalledExecutable(destination, installationRoot)) {
                    logger.warn(
                        "Could not check the latest Embed Code release ({}). " +
                            "Reusing the installed executable from `{}`.",
                        exception.message,
                        destination,
                    )
                    return
                }
                val expectedAssetSha256 = configuredSha256 ?: throw GradleException(
                    "${exception.message} No installed executable is available for reuse, " +
                        "and the cached asset cannot be authenticated without a configured " +
                        "digest. Configure `embedCode.sha256` to restore it safely.",
                    exception,
                )
                val cachedTag = readStoredValue(versionFile, installationRoot)
                val expectedSourceIdentity = releaseAssetIdentity(baseUrl, cachedTag, asset)
                val reused = installFromVerifiedAsset(
                    platform,
                    destination,
                    cachedAsset,
                    versionFile,
                    assetChecksum,
                    executableChecksum,
                    sourceIdentity,
                    cachedTag,
                    expectedSourceIdentity,
                    expectedAssetSha256,
                    installationRoot,
                )
                if (!reused) {
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
        if (resolvedTag != null) {
            logger.info("Resolved the latest Embed Code release as {}.", resolvedTag)
        }
        val selectedReleaseTag = requestedTag ?: resolvedTag
        val expectedAssetSha256 = resolveTrustedAssetSha256(
            configuredSha256,
            baseUrl,
            selectedReleaseTag,
            asset,
        )
        val expectedSourceIdentity = releaseAssetIdentity(baseUrl, selectedReleaseTag, asset)
        if (installFromVerifiedAsset(
                platform,
                destination,
                cachedAsset,
                versionFile,
                assetChecksum,
                executableChecksum,
                sourceIdentity,
                selectedReleaseTag,
                expectedSourceIdentity,
                expectedAssetSha256,
                installationRoot,
            )
        ) {
            logger.lifecycle(
                "Reusing verified Embed Code {} from {}",
                selectedVersionName(requestedTag, resolvedTag),
                destination,
            )
            return
        }
        val source = releaseAsset(baseUrl, selectedReleaseTag, asset)
        val download = Files.createTempFile(temporaryDir.toPath(), "downloaded-asset-", ".tmp")

        try {
            createDirectoriesSafely(destination.parent, installationRoot)
            val release = requestedTag ?: "latest release"
            logger.lifecycle("Downloading Embed Code {} from {}", release, source)
            try {
                download(source, download)
            } catch (exception: GradleException) {
                throw addReleaseTagMigrationHint(exception, requestedTag)
            }
            val downloadedSha256 = sha256(download)
            if (downloadedSha256 != expectedAssetSha256) {
                throw GradleException(
                    "SHA-256 verification failed for Embed Code asset `$asset`: " +
                        "expected `$expectedAssetSha256`, but downloaded `$downloadedSha256`.",
                )
            }
            logger.info("Verified SHA-256 digest `{}` for {}.", downloadedSha256, asset)
            moveSafely(download, cachedAsset, installationRoot)
            if (selectedReleaseTag == null) {
                deleteSafely(versionFile, installationRoot)
            } else {
                writeStoredValue(versionFile, selectedReleaseTag, installationRoot)
            }
            val installed = installFromVerifiedAsset(
                platform,
                destination,
                cachedAsset,
                versionFile,
                assetChecksum,
                executableChecksum,
                sourceIdentity,
                selectedReleaseTag,
                expectedSourceIdentity,
                expectedAssetSha256,
                installationRoot,
            )
            if (!installed) {
                throw GradleException(
                    "The verified Embed Code asset could not be restored from `$cachedAsset`.",
                )
            }
            logger.info(
                "Installed Embed Code {} at {}.",
                selectedReleaseTag ?: "latest release",
                destination,
            )
        } catch (exception: IOException) {
            throw GradleException("Could not install Embed Code from $source.", exception)
        } finally {
            Files.deleteIfExists(download)
        }
    }

    /**
     * Reuses an installed executable while Gradle is offline.
     */
    private fun reuseOfflineInstallation(
        platform: EmbedCodePlatform,
        destination: Path,
        cachedAsset: Path,
        versionFile: Path,
        assetChecksum: Path,
        executableChecksum: Path,
        sourceIdentity: Path,
        requestedTag: String?,
        baseUrl: String,
        asset: String,
        configuredSha256: String?,
        installationRoot: Path,
    ) {
        if (isReusableInstalledExecutable(destination, installationRoot)) {
            logger.lifecycle(
                "Reusing installed Embed Code executable from {} in offline mode",
                destination,
            )
            return
        }
        val expectedAssetSha256 = configuredSha256 ?: throw GradleException(
            "Cannot install Embed Code in offline mode because no executable exists at " +
                "`$destination`, and the cached asset cannot be authenticated without a " +
                "trusted digest. Configure `embedCode.sha256` to restore it safely.",
        )
        val cachedTag = readStoredValue(versionFile, installationRoot)
        val selectedTag = requestedTag ?: cachedTag
        if (requestedTag != null && cachedTag != requestedTag) {
            throw GradleException(
                "Cannot install Embed Code in offline mode because " +
                    "no cached asset exists for release tag `$requestedTag`.",
            )
        }
        val expectedSourceIdentity = releaseAssetIdentity(baseUrl, selectedTag, asset)
        if (!installFromVerifiedAsset(
                platform,
                destination,
                cachedAsset,
                versionFile,
                assetChecksum,
                executableChecksum,
                sourceIdentity,
                selectedTag,
                expectedSourceIdentity,
                expectedAssetSha256,
                installationRoot,
            )
        ) {
            throw GradleException(
                "Cannot reuse cached Embed Code asset `$cachedAsset` in offline mode " +
                    "because it is missing or does not match `embedCode.sha256`.",
            )
        }
        logger.lifecycle(
            "Reusing verified cached Embed Code executable from {} in offline mode",
            destination,
        )
    }

    /**
     * Returns whether an installed executable can be reused without remote verification.
     *
     * Offline operation deliberately trusts a regular file placed at the configured output
     * path. The path itself remains subject to the installation-root and link checks.
     */
    private fun isReusableInstalledExecutable(
        destination: Path,
        installationRoot: Path,
    ): Boolean {
        requireNoSymbolicLinks(destination, installationRoot)
        return Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
    }

    /**
     * Returns whether the installed executable was produced by a successful verified install.
     *
     * This deliberately treats the local installation and its metadata as trusted after the
     * initial asset verification. It validates cache identity, but does not rehash local files.
     */
    private fun isPreviouslyVerifiedInstallation(
        destination: Path,
        versionFile: Path,
        assetChecksum: Path,
        executableChecksum: Path,
        sourceIdentity: Path,
        requestedTag: String?,
        baseUrl: String,
        asset: String,
        configuredSha256: String?,
        installationRoot: Path,
    ): Boolean {
        if (!isReusableInstalledExecutable(destination, installationRoot)) {
            return false
        }
        val cachedTag = readStoredValue(versionFile, installationRoot)
        if (requestedTag != null && cachedTag != requestedTag) {
            return false
        }
        val selectedTag = requestedTag ?: cachedTag
        val expectedSourceIdentity = releaseAssetIdentity(baseUrl, selectedTag, asset)
        if (readStoredValue(sourceIdentity, installationRoot) != expectedSourceIdentity) {
            return false
        }
        val storedAssetSha256 = readStoredSha256(assetChecksum, installationRoot)
            ?: return false
        if (configuredSha256 != null && storedAssetSha256 != configuredSha256) {
            return false
        }
        return readStoredSha256(executableChecksum, installationRoot) != null
    }

    /**
     * Reads a valid SHA-256 cache marker, or returns `null` when it is absent or malformed.
     */
    private fun readStoredSha256(file: Path, installationRoot: Path): String? {
        val value = readStoredValue(file, installationRoot) ?: return null
        return runCatching { normalizeSha256(value) }.getOrNull()
    }

    /**
     * Resolves a digest from trusted configuration or current release metadata.
     */
    private fun resolveTrustedAssetSha256(
        configuredSha256: String?,
        baseUrl: String,
        releaseTag: String?,
        asset: String,
    ): String = resolveExpectedAssetSha256(
        configuredSha256,
        baseUrl,
        releaseTag,
        asset,
    ) { metadataSource ->
        val token = if (metadataSource.host.equals("api.github.com", ignoreCase = true)) {
            githubToken.orNull?.trim()?.ifEmpty { null }
        } else {
            null
        }
        readText(metadataSource, token)
    }

    /**
     * Authenticates the cached release asset and recreates the installed executable from it.
     */
    private fun installFromVerifiedAsset(
        platform: EmbedCodePlatform,
        destination: Path,
        cachedAsset: Path,
        versionFile: Path,
        assetChecksum: Path,
        executableChecksum: Path,
        sourceIdentity: Path,
        releaseTag: String?,
        expectedSourceIdentity: String,
        expectedAssetSha256: String,
        installationRoot: Path,
    ): Boolean {
        requireNoSymbolicLinks(cachedAsset, installationRoot)
        if (!Files.isRegularFile(cachedAsset, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }
        if (readStoredValue(versionFile, installationRoot) != releaseTag) {
            return false
        }
        val stagedAsset = Files.createTempFile(temporaryDir.toPath(), "cached-asset-", ".tmp")
        var preparedExecutable: Path? = null
        try {
            copyNoFollow(cachedAsset, stagedAsset)
            if (sha256(stagedAsset) != expectedAssetSha256) {
                return false
            }
            val prepared = Files.createTempFile(
                temporaryDir.toPath(),
                "prepared-executable-",
                ".tmp",
            )
            preparedExecutable = prepared
            if (platform.assetName.endsWith(".zip")) {
                extractExecutable(stagedAsset, platform.executableName, prepared)
            } else {
                Files.copy(stagedAsset, prepared, StandardCopyOption.REPLACE_EXISTING)
            }
            if (!prepared.toFile().setExecutable(true, false)) {
                throw GradleException("Could not make `$prepared` executable.")
            }
            val preparedExecutableSha256 = sha256(prepared)
            moveSafely(prepared, destination, installationRoot)
            writeStoredValue(assetChecksum, expectedAssetSha256, installationRoot)
            writeStoredValue(executableChecksum, preparedExecutableSha256, installationRoot)
            writeStoredValue(sourceIdentity, expectedSourceIdentity, installationRoot)
            if (releaseTag == null) {
                deleteSafely(versionFile, installationRoot)
            } else {
                writeStoredValue(versionFile, releaseTag, installationRoot)
            }
            return true
        } catch (_: IOException) {
            return false
        } finally {
            Files.deleteIfExists(stagedAsset)
            preparedExecutable?.let(Files::deleteIfExists)
        }
    }

    private companion object {

        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val READ_TIMEOUT_MILLIS = 120_000
        const val BUFFER_SIZE = 8_192

        fun selectedVersionName(requestedTag: String?, resolvedTag: String?): String =
            requestedTag ?: resolvedTag ?: "latest release"

        /**
         * Normalizes [path] and verifies that it is below [installationDirectory].
         */
        fun requireInsideInstallationDirectory(
            path: Path,
            installationDirectory: Path,
        ): Path {
            val root = installationDirectory.toAbsolutePath().normalize()
            val normalizedPath = path.toAbsolutePath().normalize()
            if (normalizedPath == root || !normalizedPath.startsWith(root)) {
                throw GradleException(
                    "Embed Code installation path `$normalizedPath` must remain inside `$root`.",
                )
            }
            return normalizedPath
        }

        /**
         * Creates the installation root and rejects a symlink or non-directory root.
         */
        fun prepareInstallationDirectory(installationDirectory: Path) {
            try {
                if (!Files.exists(installationDirectory, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(installationDirectory)
                }
            } catch (exception: IOException) {
                throw GradleException(
                    "Could not create Embed Code installation directory `$installationDirectory`.",
                    exception,
                )
            }
            if (
                isRedirectingFileSystemEntry(installationDirectory) ||
                !Files.isDirectory(installationDirectory, LinkOption.NOFOLLOW_LINKS)
            ) {
                throw GradleException(
                    "Embed Code installation directory `$installationDirectory` " +
                        "must be a real directory, not a symbolic link or redirecting entry.",
                )
            }
        }

        /**
         * Detects symbolic links and directory redirects such as Windows junctions.
         */
        fun isRedirectingFileSystemEntry(path: Path): Boolean {
            return try {
                if (Files.isSymbolicLink(path)) {
                    true
                } else {
                    val parent = path.parent ?: return false
                    val expectedRealPath = parent.toRealPath().resolve(path.fileName).normalize()
                    path.toRealPath() != expectedRealPath
                }
            } catch (exception: IOException) {
                throw GradleException(
                    "Could not inspect Embed Code installation path `$path`.",
                    exception,
                )
            }
        }

        /**
         * Rejects symbolic links in every existing component from the installation root.
         */
        fun requireNoSymbolicLinks(path: Path, installationDirectory: Path) {
            val root = installationDirectory.toAbsolutePath().normalize()
            val normalizedPath = requireInsideInstallationDirectory(path, root)
            prepareInstallationDirectory(root)
            var current = root
            root.relativize(normalizedPath).forEach { component ->
                current = current.resolve(component)
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    if (isRedirectingFileSystemEntry(current)) {
                        throw GradleException(
                            "Embed Code installation path `$current` must not be a " +
                                "symbolic link or redirecting filesystem entry.",
                        )
                    }
                    if (
                        current != normalizedPath &&
                        !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)
                    ) {
                        throw GradleException(
                            "Embed Code installation path component `$current` " +
                                "must be a directory.",
                        )
                    }
                }
            }
        }

        /**
         * Creates [directory] component by component without following symbolic links.
         */
        fun createDirectoriesSafely(directory: Path, installationDirectory: Path) {
            val root = installationDirectory.toAbsolutePath().normalize()
            val normalizedDirectory = directory.toAbsolutePath().normalize()
            if (normalizedDirectory != root) {
                requireInsideInstallationDirectory(normalizedDirectory, root)
            }
            prepareInstallationDirectory(root)
            var current = root
            root.relativize(normalizedDirectory).forEach { component ->
                current = current.resolve(component)
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    if (
                        isRedirectingFileSystemEntry(current) ||
                        !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)
                    ) {
                        throw GradleException(
                            "Embed Code installation directory `$current` " +
                                "must be a real directory, not a symbolic link " +
                                "or redirecting entry.",
                        )
                    }
                } else {
                    try {
                        Files.createDirectory(current)
                    } catch (exception: IOException) {
                        throw GradleException(
                            "Could not create Embed Code installation directory `$current`.",
                            exception,
                        )
                    }
                }
            }
            val realRoot = root.toRealPath()
            val realDirectory = normalizedDirectory.toRealPath()
            if (!realDirectory.startsWith(realRoot)) {
                throw GradleException(
                    "Embed Code installation directory `$realDirectory` " +
                        "must remain inside `$realRoot`.",
                )
            }
        }

        /**
         * Adds an upgrade hint when an exact tag may be missing its former automatic prefix.
         */
        fun addReleaseTagMigrationHint(
            exception: GradleException,
            requestedTag: String?,
        ): GradleException {
            if (requestedTag == null || requestedTag.startsWith('v')) {
                return exception
            }
            return GradleException(
                "${exception.message} A release tag `v$requestedTag` may exist; " +
                    "previous plugin versions added this prefix automatically.",
                exception,
            )
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
                    Files.newByteChannel(
                        destination,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        LinkOption.NOFOLLOW_LINKS,
                    ).use { outputChannel ->
                        Channels.newOutputStream(outputChannel).use { output ->
                            copy(input, output)
                        }
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
                    connection.instanceFollowRedirects = false
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
         * Reads a UTF-8 cache metadata value without following a symbolic link.
         */
        fun readStoredValue(file: Path, installationDirectory: Path): String? {
            requireNoSymbolicLinks(file, installationDirectory)
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                return null
            }
            return try {
                Files.newByteChannel(
                    file,
                    StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS,
                ).use { channel ->
                    Channels.newInputStream(channel)
                        .bufferedReader(StandardCharsets.UTF_8)
                        .use { reader -> reader.readText().trim().ifEmpty { null } }
                }
            } catch (_: IOException) {
                null
            }
        }

        /**
         * Writes a UTF-8 cache metadata value through a fresh, unpredictable file.
         */
        @Throws(IOException::class)
        fun writeStoredValue(
            file: Path,
            value: String,
            installationDirectory: Path,
        ) {
            createDirectoriesSafely(file.parent, installationDirectory)
            requireNoSymbolicLinks(file, installationDirectory)
            val temporaryFile = Files.createTempFile(
                file.parent,
                ".${file.fileName}-",
                ".tmp",
            )
            try {
                Files.writeString(
                    temporaryFile,
                    "$value\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                )
                moveSafely(temporaryFile, file, installationDirectory)
            } finally {
                Files.deleteIfExists(temporaryFile)
            }
        }

        /**
         * Removes [file] without following symbolic links.
         */
        @Throws(IOException::class)
        fun deleteSafely(file: Path, installationDirectory: Path) {
            requireNoSymbolicLinks(file, installationDirectory)
            Files.deleteIfExists(file)
        }

        /**
         * Copies [source] without following it when it is a symbolic link.
         */
        @Throws(IOException::class)
        fun copyNoFollow(source: Path, destination: Path) {
            Files.newByteChannel(
                source,
                StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS,
            ).use { inputChannel ->
                Channels.newInputStream(inputChannel).use { input ->
                    Files.newOutputStream(
                        destination,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        LinkOption.NOFOLLOW_LINKS,
                    ).use { output -> copy(input, output) }
                }
            }
        }

        /**
         * Moves [source] to a checked installation path.
         */
        @Throws(IOException::class)
        fun moveSafely(source: Path, destination: Path, installationDirectory: Path) {
            createDirectoriesSafely(destination.parent, installationDirectory)
            requireNoSymbolicLinks(destination, installationDirectory)
            moveAtomically(source, destination)
            requireNoSymbolicLinks(destination, installationDirectory)
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
