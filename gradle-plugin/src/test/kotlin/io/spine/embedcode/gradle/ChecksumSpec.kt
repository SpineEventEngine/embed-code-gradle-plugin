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

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@DisplayName("Checksum support should")
internal class ChecksumSpec {

    @TempDir
    private lateinit var temporaryDirectory: Path

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
    fun `reject a digest with an invalid length`() {
        val digest = "0".repeat(63)

        val error = assertThrows(GradleException::class.java) {
            normalizeSha256(digest)
        }

        assertEquals("Invalid SHA-256 digest `$digest`.", error.message)
    }

    @Test
    fun `distinguish release assets in cache metadata`() {
        val baseUrl = "https://github.com/SpineEventEngine/embed-code-go/releases"

        assertNotEquals(
            releaseAssetIdentity(baseUrl, "v1", "embed-code-macos-x64.zip"),
            releaseAssetIdentity(baseUrl, "v1", "embed-code-macos-arm64.zip"),
        )
        assertNotEquals(
            releaseAssetIdentity(baseUrl, "v1", "embed-code-linux"),
            releaseAssetIdentity(
                "https://releases.example.com/embed-code",
                "v1",
                "embed-code-linux",
            ),
        )
        assertNotEquals(
            releaseAssetIdentity(baseUrl, "v1", "embed-code-linux"),
            releaseAssetIdentity(baseUrl, "V1", "embed-code-linux"),
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
    fun `ignore a GitHub URL outside the release path`() {
        assertNull(
            githubReleaseApi(
                "https://github.com/SpineEventEngine/embed-code-go/downloads",
                "v1.2.4",
            ),
        )
    }

    @Test
    fun `read an asset digest from GitHub JSON`() {
        val digest = "2".repeat(64)
        val json =
            """{"assets":[{"name":"embed-code-linux","digest":"sha256:$digest"}]}"""

        assertEquals(digest, parseGitHubAssetSha256(json, "embed-code-linux"))
    }

    @Test
    fun `send GitHub headers when reading checksum metadata`() {
        val authorization = AtomicReference<String>()
        val accept = AtomicReference<String>()
        val userAgent = AtomicReference<String>()
        val server = startServer()
        server.createContext("/metadata") { exchange ->
            authorization.set(exchange.requestHeaders.getFirst("Authorization"))
            accept.set(exchange.requestHeaders.getFirst("Accept"))
            userAgent.set(exchange.requestHeaders.getFirst("User-Agent"))
            exchange.respond(200, """{"assets":[]}""")
        }
        server.start()

        try {
            val result = readChecksumMetadata(server.uri("/metadata"), "secret-token")

            assertEquals("""{"assets":[]}""", result)
            assertEquals("Bearer secret-token", authorization.get())
            assertEquals("application/vnd.github+json", accept.get())
            assertEquals("embed-code-gradle-plugin", userAgent.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `not follow checksum metadata redirects`() {
        val redirectedRequests = AtomicInteger()
        val server = startServer()
        server.createContext("/metadata") { exchange ->
            exchange.responseHeaders.add("Location", server.uri("/redirected").toString())
            exchange.respond(302)
        }
        server.createContext("/redirected") { exchange ->
            redirectedRequests.incrementAndGet()
            exchange.respond(200, """{"assets":[]}""")
        }
        server.start()

        try {
            val source = server.uri("/metadata")

            val error = assertThrows(GradleException::class.java) {
                readChecksumMetadata(source, "secret-token")
            }

            assertEquals(
                "Could not read checksum metadata: HTTP 302 from $source.",
                error.message,
            )
            assertEquals(0, redirectedRequests.get())
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `reject a failed checksum metadata response`() {
        val server = startServer()
        server.createContext("/metadata") { exchange ->
            exchange.respond(503)
        }
        server.start()

        try {
            val source = server.uri("/metadata")

            val error = assertThrows(GradleException::class.java) {
                readChecksumMetadata(source)
            }

            assertEquals(
                "Could not read checksum metadata: HTTP 503 from $source.",
                error.message,
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `use a trimmed token for the GitHub API host`() {
        val source = URI.create("https://api.github.com/repos/owner/repository/releases/tags/v1")
        var receivedToken: String? = null

        val result = readGitHubReleaseMetadata(source, "  secret-token  ") { uri, token ->
            assertEquals(source, uri)
            receivedToken = token
            "metadata"
        }

        assertEquals("metadata", result)
        assertEquals("secret-token", receivedToken)
    }

    @Test
    fun `omit a token outside the GitHub API host`() {
        val source = URI.create("https://metadata.example.com/releases/v1")
        var receivedToken: String? = "not-called"

        readGitHubReleaseMetadata(source, "secret-token") { _, token ->
            receivedToken = token
            "metadata"
        }

        assertNull(receivedToken)
    }

    @Test
    fun `omit an absent or blank token for the GitHub API host`() {
        val source = URI.create("https://api.github.com/repos/owner/repository/releases/tags/v1")
        var requests = 0

        listOf(null, "   ").forEach { configuredToken ->
            readGitHubReleaseMetadata(source, configuredToken) { _, receivedToken ->
                assertNull(receivedToken)
                requests++
                "metadata"
            }
        }

        assertEquals(2, requests)
    }

    @Test
    fun `read release metadata through the default transport`() {
        val metadata = temporaryDirectory.resolve("metadata.json")
        Files.writeString(metadata, "metadata")

        val result = readGitHubReleaseMetadata(metadata.toUri(), "secret-token")

        assertEquals("metadata", result)
    }

    @Test
    fun `report a checksum metadata read failure`() {
        val source = temporaryDirectory.resolve("missing.json").toUri()

        val error = assertThrows(GradleException::class.java) {
            readChecksumMetadata(source)
        }

        assertEquals("Could not read checksum metadata from $source.", error.message)
    }

    @Test
    fun `reject a non-object GitHub release response`() {
        val error = assertThrows(GradleException::class.java) {
            parseGitHubAssetSha256("[]", "embed-code-linux")
        }

        assertEquals("The GitHub release response is not a JSON object.", error.message)
    }

    @Test
    fun `reject a GitHub release response without assets`() {
        val error = assertThrows(GradleException::class.java) {
            parseGitHubAssetSha256("{}", "embed-code-linux")
        }

        assertEquals("The GitHub release response does not contain assets.", error.message)
    }

    @Test
    fun `reject a GitHub release response without the requested asset`() {
        val error = assertThrows(GradleException::class.java) {
            parseGitHubAssetSha256(
                """{"assets":[{"name":"embed-code-macos"}]}""",
                "embed-code-linux",
            )
        }

        assertEquals(
            "The GitHub release does not contain asset `embed-code-linux`.",
            error.message,
        )
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
            "v1.2.4",
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
                "v1.2.4",
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

    private fun startServer(): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    private fun HttpServer.uri(path: String): URI =
        URI.create("http://127.0.0.1:${address.port}$path")

    private fun HttpExchange.respond(status: Int, body: String = "") {
        val content = body.toByteArray()
        sendResponseHeaders(status, content.size.toLong())
        responseBody.use { output -> output.write(content) }
        close()
    }
}
