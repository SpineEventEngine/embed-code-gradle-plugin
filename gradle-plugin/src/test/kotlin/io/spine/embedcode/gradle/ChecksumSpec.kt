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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI

@DisplayName("Checksum support should")
internal class ChecksumSpec {

    @Test
    fun `normalize a prefixed uppercase digest`() {
        val digest = "A".repeat(64)

        assertEquals("a".repeat(64), normalizeSha256("SHA256:$digest"))
    }

    @Test
    fun `parse a conventional checksum file`() {
        val digest = "1".repeat(64)

        assertEquals(digest, parseSha256File("$digest  embed-code-linux\n"))
    }

    @Test
    fun `distinguish release assets in cache metadata`() {
        val baseUrl = "https://github.com/SpineEventEngine/embed-code-go/releases"

        assertNotEquals(
            releaseAssetIdentity(baseUrl, "embed-code-macos-x64.zip"),
            releaseAssetIdentity(baseUrl, "embed-code-macos-arm64.zip"),
        )
        assertNotEquals(
            releaseAssetIdentity(baseUrl, "embed-code-linux"),
            releaseAssetIdentity("https://releases.example.com/embed-code", "embed-code-linux"),
        )
    }

    @Test
    fun `derive a GitHub release API URI`() {
        assertEquals(
            "https://api.github.com/repos/SpineEventEngine/embed-code-go/" +
                "releases/tags/v1.2.4",
            githubReleaseApi(
                "https://github.com/SpineEventEngine/embed-code-go/releases",
                "v1.2.4",
            ).toString(),
        )
    }

    @Test
    fun `ignore a non-GitHub release URL`() {
        assertNull(githubReleaseApi("https://releases.example.com/embed-code", "v1.2.4"))
    }

    @Test
    fun `read an asset digest from GitHub JSON`() {
        val digest = "2".repeat(64)
        val json =
            """{"assets":[{"name":"embed-code-linux","digest":"sha256:$digest"}]}"""

        assertEquals(digest, parseGitHubAssetSha256(json, "embed-code-linux"))
    }

    @Test
    fun `prefer a companion checksum over GitHub release metadata`() {
        val digest = "3".repeat(64)
        val assetSource = URI.create(
            "https://github.com/SpineEventEngine/embed-code-go/" +
                "releases/download/v1.2.4/embed-code-linux",
        )
        val requests = mutableListOf<URI>()

        val resolved = resolveExpectedAssetSha256(
            null,
            "https://github.com/SpineEventEngine/embed-code-go/releases",
            "v1.2.4",
            "embed-code-linux",
            assetSource,
        ) { source ->
            requests.add(source)
            "$digest  embed-code-linux\n"
        }

        assertEquals(digest, resolved)
        assertEquals(listOf(URI.create("$assetSource.sha256")), requests)
    }

    @Test
    fun `fall back to GitHub release metadata when a companion checksum is absent`() {
        val digest = "4".repeat(64)
        val baseUrl = "https://github.com/SpineEventEngine/embed-code-go/releases"
        val assetSource = URI.create("$baseUrl/download/v1.2.4/embed-code-linux")
        val githubApi = githubReleaseApi(baseUrl, "v1.2.4")!!
        val requests = mutableListOf<URI>()

        val resolved = resolveExpectedAssetSha256(
            null,
            baseUrl,
            "v1.2.4",
            "embed-code-linux",
            assetSource,
        ) { source ->
            requests.add(source)
            if (source == githubApi) {
                """{"assets":[{"name":"embed-code-linux","digest":"sha256:$digest"}]}"""
            } else {
                throw GradleException("Checksum asset is absent.")
            }
        }

        assertEquals(digest, resolved)
        assertEquals(listOf(URI.create("$assetSource.sha256"), githubApi), requests)
    }

    @Test
    fun `reject a missing GitHub asset digest`() {
        val error = assertThrows(GradleException::class.java) {
            parseGitHubAssetSha256(
                """{"assets":[{"name":"embed-code-linux","digest":null}]}""",
                "embed-code-linux",
            )
        }

        assertEquals(
            "GitHub does not provide a SHA-256 digest for release asset `embed-code-linux`.",
            error.message,
        )
    }
}
