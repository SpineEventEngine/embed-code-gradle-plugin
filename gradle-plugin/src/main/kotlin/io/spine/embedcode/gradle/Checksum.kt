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

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

private const val SHA256_LENGTH = 64

/**
 * Calculates the lowercase SHA-256 digest of [file].
 */
internal fun sha256(file: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file).use { input ->
        val buffer = ByteArray(8_192)
        var count = input.read(buffer)
        while (count >= 0) {
            digest.update(buffer, 0, count)
            count = input.read(buffer)
        }
    }
    return HexFormat.of().formatHex(digest.digest())
}

/**
 * Validates and normalizes a SHA-256 [value].
 */
internal fun normalizeSha256(value: String): String {
    val trimmed = value.trim()
    val digest = if (trimmed.startsWith("sha256:", ignoreCase = true)) {
        trimmed.substringAfter(':')
    } else {
        trimmed
    }
    val isInvalid = digest.length != SHA256_LENGTH ||
        digest.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }
    if (isInvalid) {
        throw GradleException("Invalid SHA-256 digest `$value`.")
    }
    return digest.lowercase()
}

/**
 * Reads the digest from common checksum-file formats.
 */
internal fun parseSha256File(contents: String): String {
    val firstLine = contents.lineSequence().firstOrNull { it.isNotBlank() }
        ?: throw GradleException("The SHA-256 checksum file is empty.")
    return normalizeSha256(firstLine.trim().split(Regex("\\s+"), limit = 2).first())
}

/**
 * Returns the GitHub release API URI represented by [releaseBaseUrl].
 */
internal fun githubReleaseApi(releaseBaseUrl: String, releaseTag: String): URI? {
    val base = URI.create(releaseBaseUrl)
    if (base.scheme != "https" || !base.host.equals("github.com", ignoreCase = true)) {
        return null
    }
    val segments = base.path.trim('/').split('/')
    if (segments.size != 3 || segments[2] != "releases") {
        return null
    }
    val encodedTag = URLEncoder.encode(releaseTag, StandardCharsets.UTF_8).replace("+", "%20")
    return URI.create(
        "https://api.github.com/repos/${segments[0]}/${segments[1]}/releases/tags/$encodedTag",
    )
}

/**
 * Extracts [assetName]'s SHA-256 digest from GitHub release JSON.
 */
internal fun parseGitHubAssetSha256(json: String, assetName: String): String {
    val release = JsonSlurper().parseText(json) as? Map<*, *>
        ?: throw GradleException("The GitHub release response is not a JSON object.")
    val assets = release["assets"] as? Iterable<*>
        ?: throw GradleException("The GitHub release response does not contain assets.")
    val asset = assets
        .filterIsInstance<Map<*, *>>()
        .firstOrNull { it["name"] == assetName }
        ?: throw GradleException("The GitHub release does not contain asset `$assetName`.")
    val digest = asset["digest"] as? String
        ?: throw GradleException(
            "GitHub does not provide a SHA-256 digest for release asset `$assetName`.",
        )
    return normalizeSha256(digest)
}
