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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

/**
 * Downloads and prepares the Embed Code executable selected for the host.
 *
 * An explicitly selected version is reused using Gradle's normal up-to-date
 * behavior. For the latest release, the remote version is checked before an
 * existing executable is replaced. When the check fails, for example without
 * network access, a previously installed executable is reused.
 */
@DisableCachingByDefault(because = "Release assets come from external URLs that may change")
public abstract class InstallEmbedCodeTask : DefaultTask() {

    /** An optional Embed Code release version. */
    @get:Input
    @get:Optional
    public abstract val version: Property<String>

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

    /**
     * Downloads, extracts when necessary, and marks the executable runnable.
     */
    @TaskAction
    public fun install() {
        val requestedVersion = version.orNull
        if (requestedVersion != null && requestedVersion.isEmpty()) {
            throw GradleException("Embed Code version must not be empty.")
        }
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
        if (requestedVersion == null && offline.get()) {
            reuseOfflineInstallation(destination)
            return
        }
        val resolvedVersion = if (requestedVersion == null) {
            logger.info("Resolving the latest Embed Code release from {}.", baseUrl)
            try {
                resolveLatestVersion(baseUrl)
            } catch (exception: GradleException) {
                if (!Files.isRegularFile(destination)) {
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
            resolvedVersion != null &&
            Files.isRegularFile(destination) &&
            readResolvedVersion(versionFile) == resolvedVersion
        ) {
            logger.lifecycle("Reusing Embed Code {} from {}", resolvedVersion, destination)
            return
        }
        val releaseVersion = requestedVersion ?: resolvedVersion
        val source = releaseAsset(baseUrl, releaseVersion, asset)
        val download = temporaryDir.toPath().resolve(asset)
        val preparedExecutable = temporaryDir.toPath().resolve(platform.executableName)

        try {
            Files.createDirectories(destination.parent)
            val release = requestedVersion ?: "latest release"
            logger.lifecycle("Downloading Embed Code {} from {}", release, source)
            download(source, download)

            if (asset.endsWith(".zip")) {
                extractExecutable(download, platform.executableName, preparedExecutable)
            } else {
                Files.move(download, preparedExecutable, StandardCopyOption.REPLACE_EXISTING)
            }

            if (!preparedExecutable.toFile().setExecutable(true, false)) {
                throw GradleException("Could not make `$preparedExecutable` executable.")
            }
            moveAtomically(preparedExecutable, destination)
            if (resolvedVersion != null) {
                writeResolvedVersion(versionFile, resolvedVersion)
            }
            logger.info(
                "Installed Embed Code {} at {}.",
                releaseVersion ?: "latest release",
                destination,
            )
        } catch (exception: IOException) {
            throw GradleException("Could not install Embed Code from $source.", exception)
        }
    }

    /**
     * Reuses an installed executable while Gradle is offline.
     */
    private fun reuseOfflineInstallation(destination: Path) {
        if (!Files.isRegularFile(destination)) {
            throw GradleException(
                "Cannot install the latest Embed Code release in offline mode because " +
                    "no cached executable exists at `$destination`.",
            )
        }
        logger.lifecycle("Reusing cached Embed Code executable from {} in offline mode", destination)
    }

    private companion object {

        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val READ_TIMEOUT_MILLIS = 120_000
        const val BUFFER_SIZE = 8_192

        /**
         * Returns the tag of the release targeted by the latest-release redirect.
         *
         * Non-HTTP release mirrors cannot expose an HTTP redirect, so they keep
         * using the latest asset URL directly.
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
         * Returns the release asset URI for the latest or explicitly requested version.
         */
        fun releaseAsset(baseUrl: String, requestedVersion: String?, asset: String): URI {
            if (requestedVersion == null) {
                return URI.create("$baseUrl/latest/download/$asset")
            }
            val releaseTag = if (requestedVersion.startsWith("v")) {
                requestedVersion
            } else {
                "v$requestedVersion"
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
