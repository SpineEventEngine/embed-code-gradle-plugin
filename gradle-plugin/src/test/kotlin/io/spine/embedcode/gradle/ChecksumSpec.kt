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
    fun `reject non-ASCII digits in a digest`() {
        val digest = "٣" + "0".repeat(63)

        val error = assertThrows(GradleException::class.java) {
            normalizeSha256(digest)
        }

        assertEquals("Invalid SHA-256 digest `$digest`.", error.message)
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
        assertNull(githubReleaseApi("https:///releases", "v1.2.4"))
    }

    @Test
    fun `read an asset digest from GitHub JSON`() {
        val digest = "2".repeat(64)
        val json =
            """{"assets":[{"name":"embed-code-linux","digest":"sha256:$digest"}]}"""

        assertEquals(digest, parseGitHubAssetSha256(json, "embed-code-linux"))
    }

    @Test
    fun `resolve an asset digest from GitHub release metadata`() {
        val digest = "3".repeat(64)
        val baseUrl = "https://github.com/SpineEventEngine/embed-code-go/releases"
        val githubApi = githubReleaseApi(baseUrl, "v1.2.4")!!
        val requests = mutableListOf<URI>()

        val resolved = resolveExpectedAssetSha256(
            null,
            baseUrl,
            "v1.2.4",
            "embed-code-linux",
        ) { source ->
            requests.add(source)
            """{"assets":[{"name":"embed-code-linux","digest":"sha256:$digest"}]}"""
        }

        assertEquals(digest, resolved)
        assertEquals(listOf(githubApi), requests)
    }

    @Test
    fun `use a configured checksum without reading metadata`() {
        val digest = "4".repeat(64)

        val resolved = resolveExpectedAssetSha256(
            digest,
            "file:///tmp/embed-code/releases",
            null,
            "embed-code-linux",
        ) {
            throw AssertionError("Metadata must not be read for a configured checksum.")
        }

        assertEquals(digest, resolved)
    }

    @Test
    fun `require a configured checksum outside GitHub`() {
        val error = assertThrows(GradleException::class.java) {
            resolveExpectedAssetSha256(
                null,
                "file:///tmp/embed-code/releases",
                null,
                "embed-code-linux",
            ) {
                throw AssertionError("Metadata must not be read outside GitHub.")
            }
        }

        assertEquals(
            "Automatic SHA-256 resolution is available only for github.com releases. " +
                "Configure `embedCode.sha256` for asset `embed-code-linux`.",
            error.message,
        )
    }

    @Test
    fun `report a GitHub metadata failure`() {
        val baseUrl = "https://github.com/SpineEventEngine/embed-code-go/releases"

        val error = assertThrows(GradleException::class.java) {
            resolveExpectedAssetSha256(
                null,
                baseUrl,
                "v1.2.4",
                "embed-code-linux",
            ) {
                throw GradleException("GitHub metadata is unavailable.")
            }
        }

        assertEquals(
            "Could not resolve a SHA-256 digest for Embed Code asset `embed-code-linux` " +
                "from `https://api.github.com/repos/SpineEventEngine/embed-code-go/" +
                "releases/tags/v1.2.4`. Configure `embedCode.sha256` explicitly, or " +
                "`embedCode.githubToken` if the GitHub API rate limit was exceeded.",
            error.message,
        )
        assertEquals("GitHub metadata is unavailable.", error.cause?.message)
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
            "GitHub does not provide a SHA-256 digest for release asset `embed-code-linux`. " +
                "Configure `embedCode.sha256` explicitly.",
            error.message,
        )
    }
}
