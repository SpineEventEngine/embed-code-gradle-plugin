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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@DisplayName("`InstallationDirectory` should")
internal class InstallationDirectorySpec {

    @TempDir
    private lateinit var temporaryDirectory: Path

    @Test
    fun `reject the installation root as its own descendant`() {
        val root = temporaryDirectory.resolve("installation")
        val installation = InstallationDirectory(root)

        val error = assertThrows(GradleException::class.java) {
            installation.requireInside(root)
        }

        assertEquals(
            "Embed Code installation path `$root` must remain inside `$root`.",
            error.message,
        )
    }

    @Test
    fun `report a failure to prepare the installation root`() {
        val blockingFile = temporaryDirectory.resolve("blocked")
        Files.writeString(blockingFile, "unchanged")
        val root = blockingFile.resolve("installation")
        val installation = InstallationDirectory(root)

        val error = assertThrows(GradleException::class.java) {
            installation.prepare()
        }

        assertEquals(
            "Could not create Embed Code installation directory `$root`.",
            error.message,
        )
        assertEquals("unchanged", Files.readString(blockingFile))
    }

    @Test
    fun `reject a regular file as the installation root`() {
        val root = temporaryDirectory.resolve("installation")
        Files.writeString(root, "unchanged")
        val installation = InstallationDirectory(root)

        val error = assertThrows(GradleException::class.java) {
            installation.prepare()
        }

        assertEquals(
            "Embed Code installation directory `$root` must be a real directory, " +
                "not a symbolic link or redirecting entry.",
            error.message,
        )
        assertEquals("unchanged", Files.readString(root))
    }

    @Test
    fun `create a missing directory and accept the installation root`() {
        val root = temporaryDirectory.resolve("installation")
        val child = root.resolve("child")
        val installation = InstallationDirectory(root)
        installation.prepare()

        installation.createDirectoriesSafely(root)
        installation.createDirectoriesSafely(child)

        assertTrue(Files.isDirectory(child))
    }

    @Test
    fun `reject a regular file while creating a directory`() {
        val root = temporaryDirectory.resolve("installation")
        val blockingFile = root.resolve("blocked")
        val installation = InstallationDirectory(root)
        installation.prepare()
        Files.writeString(blockingFile, "unchanged")

        val error = assertThrows(GradleException::class.java) {
            installation.createDirectoriesSafely(blockingFile.resolve("child"))
        }

        assertEquals(
            "Embed Code installation directory `$blockingFile` must be a real directory, " +
                "not a symbolic link or redirecting entry.",
            error.message,
        )
        assertEquals("unchanged", Files.readString(blockingFile))
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `report a directory creation failure`() {
        val root = temporaryDirectory.resolve("installation")
        val invalidDirectory = root.resolve("a".repeat(256))
        val installation = InstallationDirectory(root)
        installation.prepare()

        val error = assertThrows(GradleException::class.java) {
            installation.createDirectoriesSafely(invalidDirectory)
        }

        assertEquals(
            "Could not create Embed Code installation directory `$invalidDirectory`.",
            error.message,
        )
        assertFalse(Files.exists(invalidDirectory))
    }

    @Test
    fun `report a failure to inspect a filesystem entry`() {
        val missing = temporaryDirectory.resolve("missing")

        val error = assertThrows(GradleException::class.java) {
            isRedirectingFileSystemEntry(missing)
        }

        assertEquals(
            "Could not inspect Embed Code installation path `$missing`.",
            error.message,
        )
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `accept the filesystem root as non-redirecting`() {
        assertFalse(isRedirectingFileSystemEntry(Path.of("/")))
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `reject a symbolic link while creating a directory`() {
        val root = temporaryDirectory.resolve("installation")
        val outside = temporaryDirectory.resolve("outside")
        Files.createDirectories(outside)
        val redirect = root.resolve("redirect")
        val installation = InstallationDirectory(root)
        installation.prepare()
        Files.createSymbolicLink(redirect, outside)

        val error = assertThrows(GradleException::class.java) {
            installation.createDirectoriesSafely(redirect.resolve("child"))
        }

        assertEquals(
            "Embed Code installation directory `$redirect` must be a real directory, " +
                "not a symbolic link or redirecting entry.",
            error.message,
        )
        assertFalse(Files.exists(outside.resolve("child")))
    }
}
