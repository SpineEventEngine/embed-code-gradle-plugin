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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`EmbedCodePlatform` should")
internal class EmbedCodePlatformSpec {

    @Test
    fun `select Apple silicon asset`() {
        assertEquals(
            EmbedCodePlatform("embed-code-macos-arm64.zip", "embed-code-macos-arm64"),
            EmbedCodePlatform.detect("Mac OS X", "aarch64", "v1.2.4"),
        )
    }

    @Test
    fun `select Intel macOS asset`() {
        assertEquals(
            EmbedCodePlatform("embed-code-macos-x64.zip", "embed-code-macos-x64"),
            EmbedCodePlatform.detect("Mac OS X", "x86_64", "v1.2.4"),
        )
    }

    @Test
    fun `select Linux ZIP asset for a new release`() {
        assertEquals(
            EmbedCodePlatform("embed-code-linux.zip", "embed-code-linux"),
            EmbedCodePlatform.detect("Linux", "amd64", "v1.2.5"),
        )
    }

    @Test
    fun `select bare Linux asset for releases published before ZIP packaging`() {
        listOf("v1.2.3", "v1.2.4").forEach { releaseTag ->
            assertEquals(
                EmbedCodePlatform("embed-code-linux", "embed-code-linux"),
                EmbedCodePlatform.detect("Linux", "amd64", releaseTag),
                releaseTag,
            )
        }
    }

    @Test
    fun `select Windows asset`() {
        assertEquals(
            EmbedCodePlatform("embed-code-windows.exe", "embed-code-windows.exe"),
            EmbedCodePlatform.detect("Windows 11", "amd64", "v1.2.4"),
        )
    }

    @Test
    fun `use a stable executable name on Unix`() {
        assertEquals("embed-code", EmbedCodePlatform.installedExecutableName("Linux"))
    }

    @Test
    fun `keep the executable suffix on Windows`() {
        assertEquals("embed-code.exe", EmbedCodePlatform.installedExecutableName("Windows 11"))
    }

    @Test
    fun `reject platform without release binary`() {
        val error = assertThrows(GradleException::class.java) {
            EmbedCodePlatform.detect("Linux", "aarch64", "v1.2.4")
        }

        assertEquals(
            "Embed Code does not publish a binary for operating system `Linux`" +
                " and architecture `aarch64`.",
            error.message,
        )
    }
}
