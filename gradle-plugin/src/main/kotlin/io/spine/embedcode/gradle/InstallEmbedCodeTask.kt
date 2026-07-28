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
 * Release assets are authenticated before their first installation. Before a local
 * installation is reused, its digest is calculated and compared with the digest stored
 * during that verified installation; this does not require a network request. If the
 * installation is missing, modified, or its metadata no longer matches the configured
 * release source, the retained asset is authenticated again before use.
 */
@DisableCachingByDefault(because = "Release assets come from external URLs that may change")
public abstract class InstallEmbedCodeTask : DefaultTask() {

    /** The exact Embed Code release tag used verbatim. */
    @get:Input
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

    /** Stores the downloaded release asset used to recreate the executable. */
    @get:LocalState
    public abstract val cachedAssetFile: RegularFileProperty

    /** Records the verified digest of the downloaded release asset. */
    @get:LocalState
    public abstract val assetChecksumFile: RegularFileProperty

    /** Records the digest used to authenticate the prepared executable on reuse. */
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
        val releaseTag = validateVersion(version.get())
        val configuredSha256 = sha256.orNull?.let(::normalizeSha256)
        val hostOperatingSystem = operatingSystem.get()
        val hostArchitecture = architecture.get()
        logger.info(
            "Preparing the Embed Code executable for operating system `{}` and architecture `{}`.",
            hostOperatingSystem,
            hostArchitecture,
        )
        val platform = EmbedCodePlatform.detect(
            hostOperatingSystem,
            hostArchitecture,
            releaseTag,
        )
        val asset = platform.assetName
        val baseUrl = trimTrailingSlashes(downloadBaseUrl.get())
        val installation = InstallationDirectory(
            installationDirectory.get().asFile.toPath(),
        )
        val destination = installation.requireInside(
            executableFile.get().asFile.toPath(),
        )
        val cachedAsset = installation.requireInside(
            cachedAssetFile.get().asFile.toPath(),
        )
        val assetChecksum = installation.requireInside(
            assetChecksumFile.get().asFile.toPath(),
        )
        val executableChecksum = installation.requireInside(
            executableChecksumFile.get().asFile.toPath(),
        )
        val sourceIdentity = installation.requireInside(
            sourceIdentityFile.get().asFile.toPath(),
        )
        installation.prepare()
        installation.requireNoSymbolicLinks(destination)

        if (offline.get()) {
            reuseOfflineInstallation(
                platform,
                destination,
                cachedAsset,
                assetChecksum,
                executableChecksum,
                sourceIdentity,
                releaseTag,
                baseUrl,
                asset,
                configuredSha256,
                installation,
            )
            return
        }
        if (isPreviouslyVerifiedInstallation(
                destination,
                assetChecksum,
                executableChecksum,
                sourceIdentity,
                releaseTag,
                baseUrl,
                asset,
                configuredSha256,
                installation,
            )
        ) {
            logger.lifecycle(
                "Reusing previously verified Embed Code {} from {}",
                releaseTag,
                destination,
            )
            return
        }
        val expectedAssetSha256 = resolveTrustedAssetSha256(
            configuredSha256,
            baseUrl,
            releaseTag,
            asset,
        )
        val expectedSourceIdentity = releaseAssetIdentity(baseUrl, releaseTag, asset)
        val restoredFromCache = try {
            installFromVerifiedAsset(
                platform,
                destination,
                cachedAsset,
                assetChecksum,
                executableChecksum,
                sourceIdentity,
                expectedSourceIdentity,
                expectedAssetSha256,
                installation,
            )
        } catch (exception: GradleException) {
            if (exception.cause !is IOException) {
                throw exception
            }
            logger.info(
                "Could not restore the verified Embed Code asset from `$cachedAsset`; " +
                    "downloading it again.",
                exception,
            )
            false
        }
        if (restoredFromCache) {
            logger.lifecycle(
                "Reusing verified Embed Code {} from {}",
                releaseTag,
                destination,
            )
            return
        }
        val source = releaseAsset(baseUrl, releaseTag, asset)
        val download = Files.createTempFile(temporaryDir.toPath(), "downloaded-asset-", ".tmp")

        try {
            installation.createDirectoriesSafely(destination.parent)
            logger.lifecycle("Downloading Embed Code {} from {}", releaseTag, source)
            try {
                download(source, download)
            } catch (exception: GradleException) {
                throw addReleaseTagMigrationHint(exception, releaseTag)
            }
            val downloadedSha256 = sha256(download)
            if (downloadedSha256 != expectedAssetSha256) {
                throw GradleException(
                    "SHA-256 verification failed for Embed Code asset `$asset`: " +
                        "expected `$expectedAssetSha256`, but downloaded `$downloadedSha256`.",
                )
            }
            logger.info("Verified SHA-256 digest `{}` for {}.", downloadedSha256, asset)
            moveSafely(download, cachedAsset, installation)
            val installed = installFromVerifiedAsset(
                platform,
                destination,
                cachedAsset,
                assetChecksum,
                executableChecksum,
                sourceIdentity,
                expectedSourceIdentity,
                expectedAssetSha256,
                installation,
            )
            if (!installed) {
                throw GradleException(
                    "Could not restore the verified Embed Code asset from `$cachedAsset`.",
                )
            }
            logger.info(
                "Installed Embed Code {} at {}.",
                releaseTag,
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
        assetChecksum: Path,
        executableChecksum: Path,
        sourceIdentity: Path,
        releaseTag: String,
        baseUrl: String,
        asset: String,
        configuredSha256: String?,
        installation: InstallationDirectory,
    ) {
        if (isPreviouslyVerifiedInstallation(
                destination,
                assetChecksum,
                executableChecksum,
                sourceIdentity,
                releaseTag,
                baseUrl,
                asset,
                configuredSha256,
                installation,
            )
        ) {
            logger.lifecycle(
                "Reusing locally verified Embed Code executable from {} in offline mode",
                destination,
            )
            return
        }
        val expectedAssetSha256 = configuredSha256 ?: throw GradleException(
            "Cannot install Embed Code in offline mode because no locally verified " +
                "executable is available at `$destination`, and the cached asset cannot " +
                "be authenticated without a trusted digest. Configure `embedCode.sha256` " +
                "to restore it safely.",
        )
        val expectedSourceIdentity = releaseAssetIdentity(baseUrl, releaseTag, asset)
        if (!installFromVerifiedAsset(
                platform,
                destination,
                cachedAsset,
                assetChecksum,
                executableChecksum,
                sourceIdentity,
                expectedSourceIdentity,
                expectedAssetSha256,
                installation,
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
     * Returns whether an installed executable matches its locally stored verified digest.
     */
    private fun isLocallyVerifiedExecutable(
        destination: Path,
        executableChecksum: Path,
        installation: InstallationDirectory,
    ): Boolean {
        installation.requireNoSymbolicLinks(destination)
        if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)) {
            return false
        }
        val storedExecutableSha256 = readStoredSha256(
            executableChecksum,
            installation,
        ) ?: return false
        return try {
            sha256(destination) == storedExecutableSha256
        } catch (_: IOException) {
            false
        }
    }

    /**
     * Returns whether the installed executable was produced by a successful verified install.
     */
    private fun isPreviouslyVerifiedInstallation(
        destination: Path,
        assetChecksum: Path,
        executableChecksum: Path,
        sourceIdentity: Path,
        releaseTag: String,
        baseUrl: String,
        asset: String,
        configuredSha256: String?,
        installation: InstallationDirectory,
    ): Boolean {
        val expectedSourceIdentity = releaseAssetIdentity(baseUrl, releaseTag, asset)
        if (readStoredValue(sourceIdentity, installation) != expectedSourceIdentity) {
            return false
        }
        val storedAssetSha256 = readStoredSha256(assetChecksum, installation)
            ?: return false
        if (configuredSha256 != null && storedAssetSha256 != configuredSha256) {
            return false
        }
        return isLocallyVerifiedExecutable(
            destination,
            executableChecksum,
            installation,
        )
    }

    /**
     * Reads a valid SHA-256 cache marker, or returns `null` when it is absent or malformed.
     */
    private fun readStoredSha256(file: Path, installation: InstallationDirectory): String? {
        val value = readStoredValue(file, installation) ?: return null
        return runCatching { normalizeSha256(value) }.getOrNull()
    }

    /**
     * Resolves a digest from trusted configuration or current release metadata.
     */
    private fun resolveTrustedAssetSha256(
        configuredSha256: String?,
        baseUrl: String,
        releaseTag: String,
        asset: String,
    ): String = resolveExpectedAssetSha256(
        configuredSha256,
        baseUrl,
        releaseTag,
        asset,
    ) { metadataSource ->
        readGitHubReleaseMetadata(metadataSource, githubToken.orNull)
    }

    /**
     * Authenticates the cached release asset and recreates the installed executable from it.
     */
    private fun installFromVerifiedAsset(
        platform: EmbedCodePlatform,
        destination: Path,
        cachedAsset: Path,
        assetChecksum: Path,
        executableChecksum: Path,
        sourceIdentity: Path,
        expectedSourceIdentity: String,
        expectedAssetSha256: String,
        installation: InstallationDirectory,
    ): Boolean {
        installation.requireNoSymbolicLinks(cachedAsset)
        if (!Files.isRegularFile(cachedAsset, LinkOption.NOFOLLOW_LINKS)) {
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
            moveSafely(prepared, destination, installation)
            writeStoredValue(assetChecksum, expectedAssetSha256, installation)
            writeStoredValue(executableChecksum, preparedExecutableSha256, installation)
            writeStoredValue(sourceIdentity, expectedSourceIdentity, installation)
            return true
        } catch (exception: IOException) {
            throw GradleException(
                "Could not restore the verified Embed Code asset from `$cachedAsset`.",
                exception,
            )
        } finally {
            Files.deleteIfExists(stagedAsset)
            preparedExecutable?.let(Files::deleteIfExists)
        }
    }

    private companion object {

        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val READ_TIMEOUT_MILLIS = 120_000
        const val BUFFER_SIZE = 8_192

        /**
         * Adds an upgrade hint when an exact tag may be missing its former automatic prefix.
         */
        fun addReleaseTagMigrationHint(
            exception: GradleException,
            requestedTag: String,
        ): GradleException {
            if (requestedTag.startsWith('v')) {
                return exception
            }
            return GradleException(
                "${exception.message} A release tag `v$requestedTag` may exist; " +
                    "previous plugin versions added this prefix automatically.",
                exception,
            )
        }

        /**
         * Returns the release asset URI for [releaseTag].
         */
        fun releaseAsset(baseUrl: String, releaseTag: String, asset: String): URI =
            URI.create("$baseUrl/download/$releaseTag/$asset")

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
         * Reads a UTF-8 cache metadata value without following a symbolic link.
         */
        fun readStoredValue(file: Path, installation: InstallationDirectory): String? {
            installation.requireNoSymbolicLinks(file)
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
            installation: InstallationDirectory,
        ) {
            installation.createDirectoriesSafely(file.parent)
            installation.requireNoSymbolicLinks(file)
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
                moveSafely(temporaryFile, file, installation)
            } finally {
                Files.deleteIfExists(temporaryFile)
            }
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
        fun moveSafely(
            source: Path,
            destination: Path,
            installation: InstallationDirectory,
        ) {
            installation.createDirectoriesSafely(destination.parent)
            installation.requireNoSymbolicLinks(destination)
            moveAtomically(source, destination)
            installation.requireNoSymbolicLinks(destination)
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
