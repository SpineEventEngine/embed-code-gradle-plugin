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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Embed Code release-tag support should")
internal class EmbedCodeVersionSpec {

    @Test
    fun `accept and trim safe release tags`() {
        val tags = listOf(
            "v1.2.3",
            "1.0.0-beta+build",
            "2.0_rc1",
            "latest",
            "CON",
            "release.",
        )

        tags.forEach { tag ->
            assertEquals(tag, validateVersion(" $tag "))
        }
    }

    @Test
    fun `reject unsafe release tags`() {
        val tags = listOf("../x", "a/b", "a%2f")

        tags.forEach { tag ->
            assertThrows(InvalidUserDataException::class.java) {
                validateVersion(tag)
            }
        }
    }

    @Test
    fun `treat an empty release tag as latest`() {
        assertEquals("", validateVersion("  "))
    }

    @Test
    fun `derive distinct portable cache keys from exact tags`() {
        val lowercaseKey = releaseTagCacheKey("v1")
        val uppercaseKey = releaseTagCacheKey("V1")

        assertNotEquals(lowercaseKey, uppercaseKey)
        listOf(
            lowercaseKey,
            uppercaseKey,
            releaseTagCacheKey("CON"),
            releaseTagCacheKey("v1."),
        ).forEach { key ->
            assertEquals(64, key.length)
            assertTrue(key.all { character -> character in '0'..'9' || character in 'a'..'f' })
        }
    }
}
