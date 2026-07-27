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

package io.spine.embedcode.gradle.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Dependency Markdown report normalization should")
internal class DependencyMarkdownReportRendererSpec {

    @Test
    fun `remove boundary blank lines and trailing whitespace`() {
        val report = "\r\n# Dependency report\r\nEntry  \r\n\r\n\r\n"

        val normalized = normalizeMarkdownReport(report)

        assertEquals("# Dependency report\nEntry\n", normalized)
    }

    @Test
    fun `replace a link to an untracked license file with a code span`() {
        val line = "> - **Embedded license files**: [caffeine-2.9.3.jar/META-INF/LICENSE]" +
            "(caffeine-2.9.3.jar/META-INF/LICENSE)"

        val neutralized = neutralizeRelativeLinks(line)

        assertEquals(
            "> - **Embedded license files**: `caffeine-2.9.3.jar/META-INF/LICENSE`",
            neutralized,
        )
    }

    @Test
    fun `replace every link on a line listing several license files`() {
        val line = "> - **Embedded license files**: [a.jar/LICENSE](a.jar/LICENSE), " +
            "[b.jar/NOTICE](b.jar/NOTICE)"

        val neutralized = neutralizeRelativeLinks(line)

        assertEquals(
            "> - **Embedded license files**: `a.jar/LICENSE`, `b.jar/NOTICE`",
            neutralized,
        )
    }

    @Test
    fun `keep links that address an absolute URL`() {
        val line = "> - **POM License**: Apache 2.0 - " +
            "[https://www.apache.org/licenses/LICENSE-2.0.txt]" +
            "(https://www.apache.org/licenses/LICENSE-2.0.txt)"

        val neutralized = neutralizeRelativeLinks(line)

        assertEquals(line, neutralized)
    }

    @Test
    fun `explain an incompatible license report extension`() {
        val exception =
            assertThrows(IllegalStateException::class.java) {
                requireLicenseReportExtension(Any())
            }

        assertEquals(
            "Expected ProjectData.extension to be LicenseReportExtension, but received " +
                "java.lang.Object. Check compatibility with the configured " +
                "gradle-license-report version.",
            exception.message,
        )
    }
}
