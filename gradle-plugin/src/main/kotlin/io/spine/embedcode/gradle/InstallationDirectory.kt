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

import org.gradle.api.GradleException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Owns a normalized installation root and validates paths used by one task action.
 *
 * The task checks all configured paths before [prepare] creates the root. Later operations reuse
 * this instance and recheck the root without repeating its creation.
 */
internal class InstallationDirectory(path: Path) {

    private val root = path.toAbsolutePath().normalize()

    /**
     * Normalizes [path] and verifies that it is a strict descendant of the root.
     */
    internal fun requireInside(path: Path): Path {
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
    internal fun prepare() {
        try {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(root)
            }
        } catch (exception: IOException) {
            throw GradleException(
                "Could not create Embed Code installation directory `$root`.",
                exception,
            )
        }
        requireRealRoot()
    }

    /**
     * Rejects a symbolic-link, redirecting, missing, or non-directory root.
     */
    private fun requireRealRoot() {
        if (
            isRedirectingFileSystemEntry(root) ||
            !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw GradleException(
                "Embed Code installation directory `$root` must be a real directory, " +
                    "not a symbolic link or redirecting entry.",
            )
        }
    }

    /**
     * Rejects symbolic links in every existing component from the installation root.
     */
    internal fun requireNoSymbolicLinks(path: Path) {
        requireRealRoot()
        val normalizedPath = requireInside(path)
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
                        "Embed Code installation path component `$current` must be a directory.",
                    )
                }
            }
        }
    }

    /**
     * Creates [directory] component by component without following symbolic links.
     */
    internal fun createDirectoriesSafely(directory: Path) {
        requireRealRoot()
        val normalizedDirectory = directory.toAbsolutePath().normalize()
        if (normalizedDirectory != root) {
            requireInside(normalizedDirectory)
        }
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
        requireRealPathInside(normalizedDirectory)
    }

    /**
     * Verifies that [directory] resolves below the real installation root.
     */
    internal fun requireRealPathInside(directory: Path) {
        val realRoot = root.toRealPath()
        val realDirectory = directory.toRealPath()
        if (!realDirectory.startsWith(realRoot)) {
            throw GradleException(
                "Embed Code installation directory `$realDirectory` " +
                    "must remain inside `$realRoot`.",
            )
        }
    }
}

/**
 * Detects symbolic links and directory redirects such as Windows junctions.
 */
internal fun isRedirectingFileSystemEntry(path: Path): Boolean {
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
