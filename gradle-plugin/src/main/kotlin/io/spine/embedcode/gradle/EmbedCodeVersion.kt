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

import org.gradle.api.InvalidUserDataException

private val validReleaseTag = Regex("[A-Za-z0-9][A-Za-z0-9._+~-]*")

/**
 * A release tag numbered `major.minor.patch`, optionally followed by a suffix.
 *
 * Each number is limited to nine digits, so it always fits an `Int`.
 */
private val numberedReleaseTag = Regex("""[vV]?(\d{1,9})\.(\d{1,9})\.(\d{1,9})(?:[._+~-].*)?""")

/**
 * The oldest supported Embed Code release.
 *
 * Older releases do not accept the `joined-fragment-separator` option that the plugin
 * always configures.
 */
private const val MINIMUM_EMBED_CODE_VERSION = "v1.2.5"

/**
 * Trims and validates a user-configured Embed Code release tag.
 *
 * The tag becomes a release URL segment, so only URL-safe characters are accepted.
 * Releases numbered below [MINIMUM_EMBED_CODE_VERSION] are rejected.
 */
internal fun validateVersion(value: String): String {
    val version = value.trim()
    if (version.isEmpty()) {
        throw InvalidUserDataException(
            "The Embed Code release tag must not be empty. Configure an exact release tag.",
        )
    }
    if (version.equals("latest", ignoreCase = true)) {
        throw InvalidUserDataException(
            "The rolling Embed Code version `latest` is not supported. " +
                "Configure an exact release tag.",
        )
    }
    if (!validReleaseTag.matches(version)) {
        throw InvalidUserDataException(
            "Embed Code release tag `$value` is invalid. " +
                "Use letters, digits, dots, hyphens, underscores, plus signs, or tildes, " +
                "and start with a letter or digit.",
        )
    }
    if (isOlderThanMinimumVersion(version)) {
        throw InvalidUserDataException(
            "Embed Code release `$version` is not supported. " +
                "Configure `embedCode.version` with `$MINIMUM_EMBED_CODE_VERSION` or newer.",
        )
    }
    return version
}

/**
 * Tells whether [tag] is numbered below [MINIMUM_EMBED_CODE_VERSION].
 *
 * A suffix does not affect the comparison. A tag without a `major.minor.patch` number
 * cannot be ordered against the minimum, so it is not considered older.
 */
private fun isOlderThanMinimumVersion(tag: String): Boolean {
    val numbers = releaseNumbers(tag) ?: return false
    val minimum = checkNotNull(releaseNumbers(MINIMUM_EMBED_CODE_VERSION))
    val firstDifference = numbers.zip(minimum).firstOrNull { (part, floor) -> part != floor }
    return firstDifference != null && firstDifference.first < firstDifference.second
}

/**
 * Returns the `major.minor.patch` numbers of [tag], or `null` if it is not numbered that way.
 */
private fun releaseNumbers(tag: String): List<Int>? =
    numberedReleaseTag.matchEntire(tag)?.groupValues?.drop(1)?.map(String::toInt)

/**
 * Returns a case-sensitive, filesystem-portable cache key for [releaseTag].
 */
internal fun releaseTagCacheKey(releaseTag: String): String =
    sha256("embed-code-release-tag\u0000$releaseTag")
