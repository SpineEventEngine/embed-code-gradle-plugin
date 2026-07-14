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
            EmbedCodePlatform.detect("Mac OS X", "aarch64"),
        )
    }

    @Test
    fun `select Intel macOS asset`() {
        assertEquals(
            EmbedCodePlatform("embed-code-macos-x64.zip", "embed-code-macos-x64"),
            EmbedCodePlatform.detect("Mac OS X", "x86_64"),
        )
    }

    @Test
    fun `select Linux asset`() {
        assertEquals(
            EmbedCodePlatform("embed-code-linux", "embed-code-linux"),
            EmbedCodePlatform.detect("Linux", "amd64"),
        )
    }

    @Test
    fun `select Windows asset`() {
        assertEquals(
            EmbedCodePlatform("embed-code-windows.exe", "embed-code-windows.exe"),
            EmbedCodePlatform.detect("Windows 11", "amd64"),
        )
    }

    @Test
    fun `reject platform without release binary`() {
        val error = assertThrows(GradleException::class.java) {
            EmbedCodePlatform.detect("Linux", "aarch64")
        }

        assertEquals(
            "Embed Code does not publish a binary for operating system `Linux`" +
                " and architecture `aarch64`.",
            error.message,
        )
    }
}
