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

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `reject a directory whose real path escapes the installation root`() {
        val root = temporaryDirectory.resolve("installation")
        val outside = temporaryDirectory.resolve("outside")
        Files.createDirectories(outside)
        val redirect = root.resolve("redirect")
        val installation = InstallationDirectory(root)
        installation.prepare()
        Files.createSymbolicLink(redirect, outside)

        val error = assertThrows(GradleException::class.java) {
            installation.requireRealPathInside(redirect)
        }

        assertEquals(
            "Embed Code installation directory `${outside.toRealPath()}` " +
                "must remain inside `${root.toRealPath()}`.",
            error.message,
        )
    }
}
